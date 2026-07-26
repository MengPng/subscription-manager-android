package com.netkaize.subscription.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.netkaize.subscription.data.ApiException
import com.netkaize.subscription.data.BillingCycle
import com.netkaize.subscription.data.CurrencyRates
import com.netkaize.subscription.data.RemoteSubscriptions
import com.netkaize.subscription.data.LegacyCaptureResult
import com.netkaize.subscription.data.RepositorySnapshot
import com.netkaize.subscription.data.ServiceTemplate
import com.netkaize.subscription.data.Session
import com.netkaize.subscription.data.Subscription
import com.netkaize.subscription.data.SubscriptionRepository
import com.netkaize.subscription.data.SyncConflictException
import com.netkaize.subscription.data.SyncFrequency
import com.netkaize.subscription.data.SyncScheduler
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AuthMode { LOGIN, REGISTER, RESET }
enum class MainDestination { HOME, SUBSCRIPTIONS, ADD, ANALYSIS, PROFILE }

data class SyncConflictUi(
    val local: List<Subscription>,
    val remote: RemoteSubscriptions,
)

data class AppUiState(
    val session: Session? = null,
    val subscriptions: List<Subscription> = emptyList(),
    val templates: List<ServiceTemplate> = emptyList(),
    val currencyRates: CurrencyRates = CurrencyRates(),
    val cloudUpdatedAt: String? = null,
    val lastSyncedAt: String? = null,
    val syncFrequency: SyncFrequency = SyncFrequency.REALTIME,
    val dirty: Boolean = false,
    val hasUnassignedLegacy: Boolean = false,
    val authMode: AuthMode = AuthMode.LOGIN,
    val destination: MainDestination = MainDestination.HOME,
    val busy: Boolean = false,
    val refreshing: Boolean = false,
    val message: String? = null,
    val conflict: SyncConflictUi? = null,
)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    val repository = SubscriptionRepository.get(application)
    private val _state = MutableStateFlow(repository.snapshot.toUiState())
    val state: StateFlow<AppUiState> = _state.asStateFlow()
    private var syncJob: Job? = null

    init {
        SyncScheduler.apply(getApplication(), _state.value.syncFrequency, localMutation = _state.value.dirty)
        if (_state.value.session != null) refresh(silent = true)
    }

    fun migrateLegacy(result: LegacyCaptureResult) {
        viewModelScope.launch {
            when (result) {
                LegacyCaptureResult.Empty -> {
                    repository.completeLegacyMigration()
                }
                is LegacyCaptureResult.Failed -> {
                    update { copy(message = "旧版数据暂未迁移：${result.reason}，下次启动会重试") }
                    return@launch
                }
                is LegacyCaptureResult.Ready -> {
                    val signedIn = try {
                        repository.captureLegacy(result.payload)
                    } catch (conflict: SyncConflictException) {
                        if (result.payload.unresolvedEntries.isEmpty()) repository.completeLegacyMigration()
                        publish(repository.snapshot)
                        update {
                            copy(
                                conflict = SyncConflictUi(conflict.local, conflict.remote),
                                message = "旧版账本与云端都有数据，请选择本次保留的版本",
                            )
                        }
                        SyncScheduler.apply(
                            getApplication(),
                            repository.snapshot.syncFrequency,
                            localMutation = repository.snapshot.dirty,
                        )
                        return@launch
                    } catch (error: Throwable) {
                        // The raw legacy snapshot has already been staged. Leave the one-time
                        // bridge incomplete so the next launch retries account adoption.
                        update {
                            copy(message = "旧版数据已安全暂存，本次匹配未完成：${readableError(error)}")
                        }
                        return@launch
                    }
                    val unresolvedCount = result.payload.unresolvedEntries.size
                    if (unresolvedCount == 0) repository.completeLegacyMigration()
                    publish(
                        repository.snapshot,
                        message = when {
                            unresolvedCount > 0 -> "已迁移可识别数据；另有 $unresolvedCount 项旧记录无法识别，原始快照已保留并会在下次启动重试"
                            signedIn -> "旧版账本已迁移并完成本机备份"
                            else -> "旧版账本已安全暂存，登录后自动导入"
                        },
                    )
                    SyncScheduler.apply(
                        getApplication(),
                        repository.snapshot.syncFrequency,
                        localMutation = repository.snapshot.dirty,
                    )
                }
            }
            if (_state.value.session != null) refresh(silent = true)
        }
    }

    fun setAuthMode(mode: AuthMode) = update { copy(authMode = mode, message = null) }
    fun navigate(destination: MainDestination) = update { copy(destination = destination, message = null) }
    fun clearMessage() = update { copy(message = null) }
    fun showMessage(value: String) = update { copy(message = value) }

    fun sendCode(email: String) = launchBusy {
        val purpose = if (_state.value.authMode == AuthMode.RESET) "reset" else "register"
        repository.sendCode(email, purpose)
    }

    fun authenticate(email: String, password: String, code: String, displayName: String) = launchBusy {
        require(email.contains('@')) { "请输入有效邮箱" }
        require(password.length >= 8) { "密码至少需要 8 位" }
        val snapshot = when (_state.value.authMode) {
            AuthMode.LOGIN -> repository.login(email, password)
            AuthMode.REGISTER -> repository.register(email, password, code, displayName)
            AuthMode.RESET -> repository.resetPassword(email, password, code)
        }
        SyncScheduler.apply(getApplication(), snapshot.syncFrequency, localMutation = snapshot.dirty)
        publish(snapshot, message = if (snapshot.subscriptions.isEmpty()) "账本为空，可以开始添加订阅" else "已载入云端订阅")
    }

    fun refresh(silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent) update { copy(refreshing = true, message = null) }
            try {
                publish(repository.refresh())
            } catch (conflict: SyncConflictException) {
                update { copy(conflict = SyncConflictUi(conflict.local, conflict.remote), message = "检测到多设备修改，请选择保留版本") }
            } catch (error: Throwable) {
                if (!silent) update { copy(message = readableError(error)) }
            } finally {
                update { copy(refreshing = false) }
            }
        }
    }

    fun saveSubscription(subscription: Subscription) {
        repository.saveSubscription(subscription)
        publish(repository.snapshot, message = "已保存到本机")
        scheduleSync()
    }

    fun deleteSubscription(id: String) {
        repository.deleteSubscription(id)
        publish(repository.snapshot, message = "订阅已删除，可从本机备份恢复")
        scheduleSync()
    }

    fun syncNow(forceLocal: Boolean = false) {
        viewModelScope.launch {
            update { copy(refreshing = true, message = null) }
            try {
                publish(repository.sync(forceLocal), message = "已同步到云端")
            } catch (conflict: SyncConflictException) {
                update { copy(conflict = SyncConflictUi(conflict.local, conflict.remote), message = "云端有不同版本") }
            } catch (error: Throwable) {
                update { copy(message = readableError(error)) }
            } finally {
                update { copy(refreshing = false) }
            }
        }
    }

    fun resolveConflictUseCloud() {
        val conflict = _state.value.conflict ?: return
        repository.acceptCloud(conflict.remote)
        publish(repository.snapshot, message = "已保留云端版本，本机版本已备份")
        update { copy(conflict = null) }
    }

    fun resolveConflictUseLocal() {
        update { copy(conflict = null) }
        syncNow(forceLocal = true)
    }

    fun updateDisplayName(value: String) = launchBusy {
        require(value.isNotBlank()) { "请输入昵称" }
        publish(repository.updateDisplayName(value), message = "昵称已更新")
        "昵称已更新"
    }

    fun updateCurrency(value: String) = launchBusy {
        publish(repository.updateCurrency(value), message = "显示货币已更新")
        "显示货币已更新"
    }

    fun setSyncFrequency(value: SyncFrequency) {
        repository.setSyncFrequency(value)
        SyncScheduler.apply(getApplication(), value)
        publish(repository.snapshot, message = "已设置为${value.label}")
        scheduleSync()
    }

    fun exportBackup(): String = repository.exportBackup()

    fun importBackup(raw: String) {
        runCatching { repository.importBackup(raw) }
            .onSuccess { count -> publish(repository.snapshot, message = "已导入 $count 项订阅，等待同步") ; scheduleSync() }
            .onFailure { update { copy(message = readableError(it)) } }
    }

    fun restoreLatestBackup() {
        val restored = repository.restoreLatestLocalBackup()
        publish(repository.snapshot, message = if (restored) "已恢复最近一次本机备份" else "暂无可恢复的本机备份")
        if (restored) scheduleSync()
    }

    fun importUnassignedLegacy() {
        runCatching { repository.importUnassignedLegacy() }
            .onSuccess { count ->
                publish(repository.snapshot, message = if (count > 0) "已合并 $count 项旧版订阅" else "没有可导入的旧版账本")
                if (count > 0) scheduleSync()
            }
            .onFailure { update { copy(message = readableError(it)) } }
    }

    fun logout() {
        viewModelScope.launch {
            repository.logout()
            SyncScheduler.apply(getApplication(), SyncFrequency.OFF)
            _state.value = AppUiState()
        }
    }

    private fun scheduleSync() {
        syncJob?.cancel()
        val frequency = repository.snapshot.syncFrequency
        SyncScheduler.apply(getApplication(), frequency, localMutation = true)
        if (frequency != SyncFrequency.REALTIME) return
        val delayMillis = when (frequency) {
            SyncFrequency.REALTIME -> 700L
            else -> return
        }
        syncJob = viewModelScope.launch { delay(delayMillis); syncNow() }
    }

    private fun launchBusy(block: suspend () -> Any?) {
        viewModelScope.launch {
            update { copy(busy = true, message = null) }
            try {
                val result = block()
                if (result is String && _state.value.message == null) update { copy(message = result) }
            } catch (conflict: SyncConflictException) {
                publish(repository.snapshot)
                update {
                    copy(
                        conflict = SyncConflictUi(conflict.local, conflict.remote),
                        message = "检测到其他设备的账本更新，请选择保留版本",
                    )
                }
            } catch (error: Throwable) {
                update { copy(message = readableError(error)) }
            } finally {
                update { copy(busy = false) }
            }
        }
    }

    private fun publish(snapshot: RepositorySnapshot, message: String? = null) {
        val previous = _state.value
        _state.value = snapshot.toUiState().copy(
            authMode = previous.authMode,
            destination = previous.destination,
            message = message ?: previous.message,
            conflict = previous.conflict,
        )
    }

    private fun RepositorySnapshot.toUiState(): AppUiState = AppUiState(
        session = session,
        subscriptions = subscriptions,
        templates = templates,
        currencyRates = currencyRates,
        cloudUpdatedAt = cloudUpdatedAt,
        lastSyncedAt = lastSyncedAt,
        syncFrequency = syncFrequency,
        dirty = dirty,
        hasUnassignedLegacy = hasUnassignedLegacy,
    )

    private fun update(transform: AppUiState.() -> AppUiState) {
        _state.value = _state.value.transform()
    }

    private fun readableError(error: Throwable): String = when (error) {
        is ApiException -> error.message ?: "请求未完成"
        is IllegalArgumentException -> error.message ?: "输入内容有误"
        else -> error.message ?: "暂时无法连接，数据仍保存在本机"
    }
}
