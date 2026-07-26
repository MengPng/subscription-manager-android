package com.netkaize.subscription.data

import android.content.Context
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

data class RepositorySnapshot(
    val session: Session? = null,
    val subscriptions: List<Subscription> = emptyList(),
    val templates: List<ServiceTemplate> = emptyList(),
    val currencyRates: CurrencyRates = CurrencyRates(),
    val cloudRevision: Int = 0,
    val cloudUpdatedAt: String? = null,
    val lastSyncedAt: String? = null,
    val syncFrequency: SyncFrequency = SyncFrequency.REALTIME,
    val dirty: Boolean = false,
    val hasUnassignedLegacy: Boolean = false,
    val pendingMutationId: String? = null,
    val pendingPayloadSha256: String? = null,
    val pendingConflict: Boolean = false,
    val recoveryRequired: Boolean = false,
    val recoveryReason: String? = null,
    val quarantineId: Long? = null,
)

class SyncConflictException(
    val local: List<Subscription>,
    val remote: RemoteSubscriptions,
) : Exception("云端与本机都发生了修改")

class SubscriptionRepository private constructor(context: Context) {
    private val api = ApiClient()
    private val local = LocalStore(context.applicationContext)
    private val sessionStore = SecureSessionStore(context.applicationContext)
    private val syncMutex = globalSyncMutex
    private val sessionTransitionMutex = Mutex()
    private val stateLock = Any()
    private val mutationGeneration = AtomicLong(0L)
    @Volatile private var sessionEpoch = 0L

    @Volatile
    var snapshot: RepositorySnapshot = initialSnapshot()
        private set

    private fun initialSnapshot(): RepositorySnapshot {
        val session = sessionStore.load() ?: return RepositorySnapshot()
        val cached = local.loadAccount(session.user.id)
        return if (cached == null) RepositorySnapshot(
            session = session,
            hasUnassignedLegacy = local.hasPendingLegacyAccount(LEGACY_UNASSIGNED_USER_ID),
        ) else RepositorySnapshot(
            session = session.copy(user = cached.user.takeUnless { cached.recoveryRequired } ?: session.user),
            subscriptions = cached.subscriptions,
            templates = cached.templates,
            currencyRates = cached.currencyRates,
            cloudRevision = cached.cloudRevision,
            cloudUpdatedAt = cached.cloudUpdatedAt,
            lastSyncedAt = cached.lastSyncedAt,
            syncFrequency = cached.syncFrequency,
            dirty = cached.dirty,
            hasUnassignedLegacy = local.hasPendingLegacyAccount(LEGACY_UNASSIGNED_USER_ID),
            pendingMutationId = cached.pendingMutationId,
            pendingPayloadSha256 = cached.pendingPayloadSha256,
            pendingConflict = cached.pendingConflict,
            recoveryRequired = cached.recoveryRequired,
            recoveryReason = cached.recoveryReason,
            quarantineId = cached.quarantineId,
        )
    }

    suspend fun login(email: String, password: String): RepositorySnapshot = withContext(Dispatchers.IO) {
        sessionTransitionMutex.withLock { establish(api.login(email, password)) }
    }

    suspend fun register(email: String, password: String, code: String, displayName: String): RepositorySnapshot = withContext(Dispatchers.IO) {
        sessionTransitionMutex.withLock { establish(api.register(email, password, code, displayName)) }
    }

    suspend fun resetPassword(email: String, password: String, code: String): RepositorySnapshot = withContext(Dispatchers.IO) {
        sessionTransitionMutex.withLock { establish(api.resetPassword(email, password, code)) }
    }

    suspend fun sendCode(email: String, purpose: String): String = withContext(Dispatchers.IO) {
        api.sendCode(email, purpose)
    }

    private suspend fun establish(session: Session): RepositorySnapshot = coroutineScope {
        val operationEpoch = synchronized(stateLock) {
            sessionEpoch += 1
            sessionEpoch
        }
        val cached = local.loadAccount(session.user.id)
        val legacy = local.pendingLegacyAccount(session.user.id)
        val remoteRequest = async { api.subscriptions(session.token) }
        val templateRequest = async { runCatching { api.templates(session.token) }.getOrElse { cached?.templates.orEmpty() } }
        val rateRequest = async { runCatching { api.currencyRates(session.token) }.getOrElse { cached?.currencyRates ?: CurrencyRates() } }
        val remote = remoteRequest.await()
        val templates = templateRequest.await()
        val rates = rateRequest.await()
        synchronized(stateLock) {
            if (operationEpoch != sessionEpoch) return@synchronized snapshot
            // An active account may be edited while the login/legacy refresh is in flight. Use
            // the newest in-memory state instead of the cache captured before the network call.
            val liveAccount = snapshot.takeIf { it.session?.user?.id == session.user.id }
            val effectiveCached = liveAccount?.toCachedAccount() ?: cached
            sessionStore.save(session)
            val localDirty = effectiveCached?.dirty == true || effectiveCached?.recoveryRequired == true
            val legacyDirty = legacy?.dirty == true
            val staleRemote = effectiveCached != null && remote.revision < effectiveCached.cloudRevision
            val authoritativeSubscriptions = if (staleRemote) requireNotNull(effectiveCached).subscriptions else remote.subscriptions
            val authoritativeRevision = if (staleRemote) requireNotNull(effectiveCached).cloudRevision else remote.revision
            val legacyAlreadyInCloud = legacyDirty &&
                mergeLegacySubscriptions(authoritativeSubscriptions, requireNotNull(legacy).subscriptions) ==
                authoritativeSubscriptions
            val localLegacyMismatch = (localDirty || staleRemote) && legacyDirty &&
                JsonCodec.subscriptionsSha256(requireNotNull(effectiveCached).subscriptions) !=
                JsonCodec.subscriptionsSha256(requireNotNull(legacy).subscriptions)
            val localCandidate = if (localLegacyMismatch) {
                mergeLegacySubscriptions(requireNotNull(effectiveCached).subscriptions, requireNotNull(legacy).subscriptions)
            } else {
                effectiveCached?.subscriptions.orEmpty()
            }
            if (legacy != null) {
                local.saveBackup(session.user.id, legacy.subscriptions, "legacy-webview-captured")
            }
            val subscriptions = when {
                staleRemote || localDirty -> localCandidate
                legacyAlreadyInCloud -> remote.subscriptions
                legacyDirty -> legacy.subscriptions
                else -> remote.subscriptions
            }
            val baselineRevision = when {
                staleRemote -> requireNotNull(effectiveCached).cloudRevision
                localDirty -> requireNotNull(effectiveCached).cloudRevision
                legacyAlreadyInCloud -> remote.revision
                legacyDirty -> 0
                else -> remote.revision
            }
            snapshot = RepositorySnapshot(
                session = session,
                subscriptions = subscriptions,
                templates = templates,
                currencyRates = rates,
                cloudRevision = baselineRevision,
                cloudUpdatedAt = if (localDirty || staleRemote) requireNotNull(effectiveCached).cloudUpdatedAt else if (legacyDirty && !legacyAlreadyInCloud) null else remote.updatedAt,
                lastSyncedAt = if (legacyAlreadyInCloud && !staleRemote) remote.updatedAt else effectiveCached?.lastSyncedAt ?: remote.updatedAt,
                syncFrequency = effectiveCached?.syncFrequency ?: legacy?.syncFrequency ?: SyncFrequency.REALTIME,
                dirty = localDirty || (legacyDirty && !legacyAlreadyInCloud),
                hasUnassignedLegacy = local.hasPendingLegacyAccount(LEGACY_UNASSIGNED_USER_ID),
                pendingMutationId = effectiveCached?.pendingMutationId.takeUnless { localLegacyMismatch },
                pendingPayloadSha256 = effectiveCached?.pendingPayloadSha256.takeUnless { localLegacyMismatch },
                pendingConflict = effectiveCached?.pendingConflict == true,
                recoveryRequired = effectiveCached?.recoveryRequired == true,
                recoveryReason = effectiveCached?.recoveryReason,
                quarantineId = effectiveCached?.quarantineId,
            )
            val pendingConfirmedByRemote = remoteMatchesPending(snapshot, remote)
            val pendingAcknowledged = pendingConfirmedByRemote && local.acknowledgeSync(
                    userId = session.user.id,
                    mutationId = snapshot.pendingMutationId!!,
                    payloadSha256 = snapshot.pendingPayloadSha256!!,
                    revision = remote.revision,
                    updatedAt = remote.updatedAt,
                )
            if (pendingAcknowledged) {
                snapshot = snapshot.copy(
                    cloudRevision = remote.revision,
                    cloudUpdatedAt = remote.updatedAt,
                    lastSyncedAt = remote.updatedAt,
                    dirty = false,
                    pendingMutationId = null,
                    pendingPayloadSha256 = null,
                    pendingConflict = false,
                )
            }
            val hasConflict = snapshot.pendingConflict || snapshot.recoveryRequired ||
                localLegacyMismatch ||
                (!pendingAcknowledged && localDirty && !staleRemote && requireNotNull(effectiveCached).cloudRevision != remote.revision) ||
                (!pendingAcknowledged && legacyDirty && !legacyAlreadyInCloud &&
                    (authoritativeRevision != 0 || authoritativeSubscriptions.isNotEmpty()))
            if (hasConflict) {
                // Keep the staged legacy row pending until the user has explicitly resolved the
                // conflict. Marking it imported before this point makes a failed merge unrecoverable.
                snapshot = snapshot.copy(pendingConflict = true, dirty = true)
                persistLocked(snapshot)
                local.saveBackup(session.user.id, subscriptions, "sync-conflict-local")
                local.saveBackup(session.user.id, remote.subscriptions, "sync-conflict-cloud")
                throw SyncConflictException(subscriptions, remote)
            }
            if (legacy != null) {
                persistAndCompleteAccountLegacyLocked(snapshot)
            } else {
                persistLocked(snapshot)
            }
            snapshot
        }
    }

    suspend fun refresh(): RepositorySnapshot = withContext(Dispatchers.IO) {
        val initial = snapshot
        val session = initial.session ?: return@withContext initial
        val operationEpoch = sessionEpoch
        val userId = session.user.id
        val userRequest = async { api.me(session.token) }
        val remoteRequest = async { api.subscriptions(session.token) }
        val templateRequest = async { runCatching { api.templates(session.token) }.getOrElse { initial.templates } }
        val rateRequest = async { runCatching { api.currencyRates(session.token) }.getOrElse { initial.currencyRates } }
        val verifiedUser = userRequest.await()
        val verifiedSession = session.copy(user = verifiedUser)
        val remote = remoteRequest.await()
        val templates = templateRequest.await()
        val rates = rateRequest.await()
        synchronized(stateLock) {
            if (operationEpoch != sessionEpoch || snapshot.session?.user?.id != userId) {
                return@synchronized snapshot
            }
            sessionStore.save(verifiedSession)
            val latest = snapshot
            val refreshDecision = remoteRefreshDecision(
                currentRevision = latest.cloudRevision,
                currentDirty = latest.dirty,
                currentPayloadSha256 = JsonCodec.subscriptionsSha256(latest.subscriptions),
                remoteRevision = remote.revision,
                remotePayloadSha256 = JsonCodec.subscriptionsSha256(remote.subscriptions),
            )
            when (refreshDecision) {
                RemoteRefreshDecision.IGNORE_STALE -> {
                    // A slower refresh may finish after a successful upload. Never let an older
                    // response roll the local revision or subscription list backwards.
                    snapshot = latest.copy(
                        session = verifiedSession,
                        templates = templates,
                        currencyRates = rates,
                    )
                    persistLocked(snapshot)
                    return@synchronized snapshot
                }
                RemoteRefreshDecision.CONFLICT_EQUAL_REVISION -> {
                    snapshot = latest.copy(pendingConflict = true, dirty = true)
                    persistLocked(snapshot)
                    local.saveBackup(verifiedUser.id, latest.subscriptions, "sync-conflict-local")
                    local.saveBackup(verifiedUser.id, remote.subscriptions, "sync-conflict-cloud")
                    throw SyncConflictException(latest.subscriptions, remote)
                }
                RemoteRefreshDecision.APPLY -> Unit
            }
            val pendingAcknowledged = remoteMatchesPending(latest, remote) && local.acknowledgeSync(
                    userId = verifiedUser.id,
                    mutationId = latest.pendingMutationId!!,
                    payloadSha256 = latest.pendingPayloadSha256!!,
                    revision = remote.revision,
                    updatedAt = remote.updatedAt,
                )
            if (pendingAcknowledged) {
                snapshot = latest.copy(
                    session = verifiedSession,
                    templates = templates,
                    currencyRates = rates,
                    cloudRevision = remote.revision,
                    cloudUpdatedAt = remote.updatedAt,
                    lastSyncedAt = remote.updatedAt,
                    dirty = false,
                    pendingMutationId = null,
                    pendingPayloadSha256 = null,
                    pendingConflict = false,
                )
                persistAndCompleteAccountLegacyLocked(snapshot)
                return@synchronized snapshot
            }
            if (latest.pendingConflict || latest.recoveryRequired || (latest.dirty && remote.revision != latest.cloudRevision)) {
                snapshot = latest.copy(pendingConflict = true, dirty = true)
                persistLocked(snapshot)
                local.saveBackup(verifiedUser.id, latest.subscriptions, "sync-conflict-local")
                local.saveBackup(verifiedUser.id, remote.subscriptions, "sync-conflict-cloud")
                throw SyncConflictException(latest.subscriptions, remote)
            }
            snapshot = latest.copy(
                session = verifiedSession,
                subscriptions = if (latest.dirty) latest.subscriptions else remote.subscriptions,
                templates = templates,
                currencyRates = rates,
                cloudRevision = remote.revision,
                cloudUpdatedAt = remote.updatedAt,
                lastSyncedAt = if (latest.dirty) latest.lastSyncedAt else remote.updatedAt,
                pendingMutationId = if (latest.dirty) latest.pendingMutationId else null,
                pendingPayloadSha256 = if (latest.dirty) latest.pendingPayloadSha256 else null,
                pendingConflict = false,
            )
            persistLocked(snapshot)
            snapshot
        }
    }

    fun saveSubscription(value: Subscription) {
        synchronized(stateLock) {
            val current = snapshot
            val userId = current.session?.user?.id ?: return
            local.saveBackup(userId, current.subscriptions, "before-edit")
            val values = if (current.subscriptions.any { it.id == value.id }) {
                current.subscriptions.map { if (it.id == value.id) value else it }
            } else current.subscriptions + value
            mutationGeneration.incrementAndGet()
            snapshot = current.copy(
                subscriptions = values,
                dirty = true,
                pendingMutationId = null,
                pendingPayloadSha256 = null,
            )
            persistLocked(snapshot)
        }
    }

    fun deleteSubscription(id: String) {
        synchronized(stateLock) {
            val current = snapshot
            val userId = current.session?.user?.id ?: return
            local.saveBackup(userId, current.subscriptions, "before-delete")
            mutationGeneration.incrementAndGet()
            snapshot = current.copy(
                subscriptions = current.subscriptions.filterNot { it.id == id },
                dirty = true,
                pendingMutationId = null,
                pendingPayloadSha256 = null,
            )
            persistLocked(snapshot)
        }
    }

    fun importBackup(raw: String): Int {
        val envelope = JsonCodec.backupFromJson(raw)
        require(envelope.schemaVersion in 1..3) { "暂不支持此备份版本" }
        require(envelope.baseCurrency == "CNY") { "此备份的基础货币暂不支持" }
        synchronized(stateLock) {
            val current = snapshot
            val currentUser = current.session?.user ?: error("请先登录")
            require(envelope.accountEmail.isBlank() || envelope.accountEmail.equals(currentUser.email, ignoreCase = true)) {
                "备份属于其他账户，请切换到对应账号后导入"
            }
            local.saveBackup(currentUser.id, current.subscriptions, "before-import")
            mutationGeneration.incrementAndGet()
            snapshot = current.copy(
                subscriptions = envelope.subscriptions,
                dirty = true,
                pendingMutationId = null,
                pendingPayloadSha256 = null,
            )
            persistLocked(snapshot)
            return envelope.subscriptions.size
        }
    }

    fun exportBackup(): String = synchronized(stateLock) {
        val current = snapshot
        val email = current.session?.user?.email ?: error("请先登录")
        JsonCodec.backupToJson(
            BackupEnvelope(
                exportedAt = Instant.now().toString(),
                accountEmail = email,
                subscriptions = current.subscriptions,
            ),
        )
    }

    fun restoreLatestLocalBackup(): Boolean {
        synchronized(stateLock) {
            val current = snapshot
            val userId = current.session?.user?.id ?: return false
            val values = local.latestBackup(userId) ?: return false
            mutationGeneration.incrementAndGet()
            snapshot = current.copy(
                subscriptions = values,
                dirty = true,
                pendingMutationId = null,
                pendingPayloadSha256 = null,
            )
            persistLocked(snapshot)
            return true
        }
    }

    fun importUnassignedLegacy(): Int {
        synchronized(stateLock) {
            val current = snapshot
            val user = current.session?.user ?: error("请先登录")
            val legacy = local.pendingLegacyAccount(LEGACY_UNASSIGNED_USER_ID) ?: return 0
            local.saveBackup(user.id, current.subscriptions, "before-unassigned-legacy-import")
            val existingIds = current.subscriptions.mapTo(mutableSetOf()) { it.id }
            val incoming = legacy.subscriptions.map { value ->
                if (existingIds.add(value.id)) value else value.copy(id = "${value.id}-legacy-${UUID.randomUUID()}")
            }
            mutationGeneration.incrementAndGet()
            snapshot = current.copy(
                subscriptions = current.subscriptions + incoming,
                dirty = true,
                hasUnassignedLegacy = false,
                pendingMutationId = null,
                pendingPayloadSha256 = null,
            )
            local.saveAccountAndMarkLegacyImported(
                account = snapshot.toCachedAccount(),
                legacyUserId = LEGACY_UNASSIGNED_USER_ID,
                sourceSubscriptions = legacy.subscriptions,
            )
            return incoming.size
        }
    }

    suspend fun sync(forceLocal: Boolean = false): RepositorySnapshot = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            val before = synchronized(stateLock) { snapshot }
            val session = before.session ?: return@withLock before
            val operationEpoch = sessionEpoch
            val userId = session.user.id
            if (!shouldAttemptSync(before.dirty, forceLocal, before.pendingConflict, before.recoveryRequired)) {
                return@withLock before
            }
            if ((before.pendingConflict || before.recoveryRequired) && !forceLocal) {
                val remote = api.subscriptions(session.token)
                local.saveBackup(userId, before.subscriptions, "corrupt-cache-local-recovery")
                local.saveBackup(userId, remote.subscriptions, "corrupt-cache-cloud-recovery")
                throw SyncConflictException(before.subscriptions, remote)
            }
            val generation = mutationGeneration.get()
            val payloadSha256 = JsonCodec.subscriptionsSha256(before.subscriptions)
            val pending = local.ensurePendingMutation(
                userId = userId,
                payloadSha256 = payloadSha256,
                proposedMutationId = UUID.randomUUID().toString(),
            )
            synchronized(stateLock) {
                if (operationEpoch == sessionEpoch &&
                    snapshot.session?.user?.id == userId &&
                    mutationGeneration.get() == generation
                ) {
                    snapshot = snapshot.copy(
                        pendingMutationId = pending.id,
                        pendingPayloadSha256 = pending.payloadSha256,
                    )
                    persistLocked(snapshot)
                }
            }
            val baseRevision = if (forceLocal) {
                val remote = api.subscriptions(session.token)
                local.saveBackup(session.user.id, remote.subscriptions, "force-local-replaced-cloud")
                remote.revision
            } else {
                before.cloudRevision
            }
            val result = try {
                api.putSubscriptions(
                    token = session.token,
                    subscriptions = before.subscriptions,
                    baseRevision = baseRevision,
                    clientMutationId = pending.id,
                )
            } catch (error: ApiException) {
                if (error.statusCode == 409 && error.code == "SYNC_CONFLICT") {
                    val latest = error.payload.optJSONObject("latest")?.let(api::remoteSubscriptions)
                        ?: api.subscriptions(session.token)
                    local.saveBackup(session.user.id, before.subscriptions, "sync-conflict-local")
                    local.saveBackup(session.user.id, latest.subscriptions, "sync-conflict-cloud")
                    synchronized(stateLock) {
                        if (operationEpoch == sessionEpoch && snapshot.session?.user?.id == userId) {
                            snapshot = snapshot.copy(pendingConflict = true)
                            persistLocked(snapshot)
                        }
                    }
                    throw SyncConflictException(before.subscriptions, latest)
                }
                throw error
            }
            if (operationEpoch != sessionEpoch || synchronized(stateLock) { snapshot.session?.user?.id } != userId) {
                local.acknowledgeSync(
                    userId = userId,
                    mutationId = pending.id,
                    payloadSha256 = payloadSha256,
                    revision = result.revision,
                    updatedAt = result.updatedAt,
                )
                return@withLock synchronized(stateLock) { snapshot }
            }
            synchronized(stateLock) {
                val acknowledged = local.acknowledgeSync(
                    userId = userId,
                    mutationId = pending.id,
                    payloadSha256 = payloadSha256,
                    revision = result.revision,
                    updatedAt = result.updatedAt,
                )
                val after = snapshot
                val sameGeneration = mutationGeneration.get() == generation &&
                    JsonCodec.subscriptionsSha256(after.subscriptions) == payloadSha256
                snapshot = after.copy(
                    cloudRevision = result.revision,
                    cloudUpdatedAt = result.updatedAt,
                    lastSyncedAt = result.updatedAt ?: Instant.now().toString(),
                    dirty = !(acknowledged && sameGeneration),
                    pendingMutationId = if (acknowledged && sameGeneration) null else after.pendingMutationId,
                    pendingPayloadSha256 = if (acknowledged && sameGeneration) null else after.pendingPayloadSha256,
                    pendingConflict = if (acknowledged && sameGeneration) false else after.pendingConflict,
                    recoveryRequired = if (acknowledged && sameGeneration) false else after.recoveryRequired,
                    recoveryReason = if (acknowledged && sameGeneration) null else after.recoveryReason,
                    quarantineId = if (acknowledged && sameGeneration) null else after.quarantineId,
                )
                if (snapshot.dirty) persistLocked(snapshot) else persistAndCompleteAccountLegacyLocked(snapshot)
                snapshot
            }
        }
    }

    fun acceptCloud(remote: RemoteSubscriptions) {
        synchronized(stateLock) {
            val current = snapshot
            current.session?.user?.id?.let { local.saveBackup(it, current.subscriptions, "conflict-replaced-by-cloud") }
            mutationGeneration.incrementAndGet()
            snapshot = current.copy(
                subscriptions = remote.subscriptions,
                cloudRevision = remote.revision,
                cloudUpdatedAt = remote.updatedAt,
                lastSyncedAt = remote.updatedAt,
                dirty = false,
                pendingMutationId = null,
                pendingPayloadSha256 = null,
                pendingConflict = false,
                recoveryRequired = false,
                recoveryReason = null,
                quarantineId = null,
            )
            persistAndCompleteAccountLegacyLocked(snapshot)
        }
    }

    suspend fun updateDisplayName(name: String): RepositorySnapshot = withContext(Dispatchers.IO) {
        sessionTransitionMutex.withLock {
            val current = snapshot
            val session = current.session ?: return@withLock current
            val operationEpoch = sessionEpoch
            val user = api.updateProfile(session.token, name)
            require(user.id == session.user.id) { "账户身份校验失败" }
            val updatedSession = session.copy(user = user)
            synchronized(stateLock) {
                if (operationEpoch != sessionEpoch || snapshot.session?.user?.id != session.user.id) {
                    return@synchronized snapshot
                }
                snapshot = snapshot.copy(session = updatedSession)
                sessionStore.save(updatedSession)
                persistLocked(snapshot)
                snapshot
            }
        }
    }

    suspend fun updateCurrency(code: String): RepositorySnapshot = withContext(Dispatchers.IO) {
        sessionTransitionMutex.withLock {
            val current = snapshot
            val session = current.session ?: return@withLock current
            val operationEpoch = sessionEpoch
            val user = api.updateCurrency(session.token, code)
            require(user.id == session.user.id) { "账户身份校验失败" }
            val updatedSession = session.copy(user = user)
            synchronized(stateLock) {
                if (operationEpoch != sessionEpoch || snapshot.session?.user?.id != session.user.id) {
                    return@synchronized snapshot
                }
                snapshot = snapshot.copy(session = updatedSession)
                sessionStore.save(updatedSession)
                persistLocked(snapshot)
                snapshot
            }
        }
    }

    fun setSyncFrequency(value: SyncFrequency) {
        synchronized(stateLock) {
            snapshot = snapshot.copy(syncFrequency = value)
            persistLocked(snapshot)
        }
    }

    suspend fun logout() = withContext(Dispatchers.IO) {
        val session = sessionTransitionMutex.withLock {
            synchronized(stateLock) {
                sessionEpoch += 1
                snapshot.session.also {
                    sessionStore.clear()
                    snapshot = RepositorySnapshot()
                }
            }
        }
        session?.let { runCatching { api.logout(it.token) } }
    }

    suspend fun captureLegacy(payload: LegacyPayload): Boolean = withContext(Dispatchers.IO) {
        local.stageLegacyAccounts(payload)
        sessionTransitionMutex.withLock {
            val activeSession = synchronized(stateLock) { snapshot.session }
            if (activeSession != null) {
                if (local.hasPendingLegacyAccount(activeSession.user.id)) {
                    mutationGeneration.incrementAndGet()
                    establish(activeSession)
                    return@withLock true
                }
                val hasUnassigned = local.hasPendingLegacyAccount(LEGACY_UNASSIGNED_USER_ID)
                synchronized(stateLock) {
                    if (snapshot.hasUnassignedLegacy != hasUnassigned) {
                        snapshot = snapshot.copy(hasUnassignedLegacy = hasUnassigned)
                        persistLocked(snapshot)
                    }
                }
                return@withLock false
            }
            val token = payload.token ?: return@withLock false
            val expectedUser = payload.user ?: return@withLock false
            val verifiedUser = runCatching { api.me(token) }.getOrNull() ?: return@withLock false
            if (verifiedUser.id != expectedUser.id) return@withLock false
            mutationGeneration.incrementAndGet()
            establish(Session(token, verifiedUser))
            true
        }
    }

    private fun persistLocked(current: RepositorySnapshot) {
        val user = current.session?.user ?: return
        local.saveAccount(current.toCachedAccount())
    }

    private fun persistAndCompleteAccountLegacyLocked(current: RepositorySnapshot) {
        val user = current.session?.user ?: return
        val legacy = local.pendingLegacyAccount(user.id)
        if (legacy == null) {
            local.saveAccount(current.toCachedAccount())
        } else {
            local.saveAccountAndMarkLegacyImported(
                account = current.toCachedAccount(),
                legacyUserId = user.id,
                sourceSubscriptions = legacy.subscriptions,
            )
        }
    }

    private fun remoteMatchesPending(localSnapshot: RepositorySnapshot, remote: RemoteSubscriptions): Boolean {
        val mutationId = localSnapshot.pendingMutationId?.takeIf(String::isNotBlank) ?: return false
        val pendingHash = localSnapshot.pendingPayloadSha256?.takeIf(String::isNotBlank) ?: return false
        if (mutationId.isBlank() || pendingHash != JsonCodec.subscriptionsSha256(localSnapshot.subscriptions)) return false
        return pendingHash == JsonCodec.subscriptionsSha256(remote.subscriptions)
    }

    private fun RepositorySnapshot.toCachedAccount(): CachedAccount {
        val user = session?.user ?: error("请先登录")
        return CachedAccount(
            user = user,
            subscriptions = subscriptions,
            templates = templates,
            currencyRates = currencyRates,
            cloudRevision = cloudRevision,
            cloudUpdatedAt = cloudUpdatedAt,
            dirty = dirty,
            lastSyncedAt = lastSyncedAt,
            syncFrequency = syncFrequency,
            pendingMutationId = pendingMutationId,
            pendingPayloadSha256 = pendingPayloadSha256,
            pendingConflict = pendingConflict,
            recoveryRequired = recoveryRequired,
            recoveryReason = recoveryReason,
            quarantineId = quarantineId,
        )
    }

    fun hasCompletedLegacyMigration(): Boolean = local.metadata(LocalStore.LEGACY_MIGRATION_KEY) == "done"
    fun completeLegacyMigration() = local.setMetadata(LocalStore.LEGACY_MIGRATION_KEY, "done")

    companion object {
        private val globalSyncMutex = Mutex()
        @Volatile private var instance: SubscriptionRepository? = null

        fun get(context: Context): SubscriptionRepository =
            instance ?: synchronized(this) {
                instance ?: SubscriptionRepository(context.applicationContext).also { instance = it }
            }
    }
}
