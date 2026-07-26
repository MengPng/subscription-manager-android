package com.netkaize.subscription.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject

class LocalStore(
    context: Context,
    databaseName: String = DATABASE_NAME,
) : SQLiteOpenHelper(context, databaseName, null, DATABASE_VERSION) {
    override fun onCreate(db: SQLiteDatabase) = ensureLatestSchema(db)

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Schema repair is intentionally idempotent. Some early APKs reported the same database
        // version while carrying slightly different columns, so version-only ALTER statements
        // are not safe enough for real devices.
        ensureLatestSchema(db)
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        // Repair same-version databases as well. A few development builds used the same schema
        // version with different columns; relying only on onUpgrade would leave those devices
        // unable to open their cached account after installing the signed release.
        if (!db.isReadOnly) ensureLatestSchema(db)
    }

    private fun ensureLatestSchema(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS account_cache (
                user_id TEXT PRIMARY KEY,
                user_json TEXT NOT NULL,
                subscriptions_json TEXT NOT NULL DEFAULT '[]',
                templates_json TEXT NOT NULL DEFAULT '[]',
                rates_json TEXT NOT NULL DEFAULT '{}',
                cloud_revision INTEGER NOT NULL DEFAULT 0,
                cloud_updated_at TEXT,
                dirty INTEGER NOT NULL DEFAULT 0,
                last_synced_at TEXT,
                sync_frequency TEXT NOT NULL DEFAULT 'realtime',
                pending_mutation_id TEXT,
                pending_payload_sha256 TEXT,
                pending_conflict INTEGER NOT NULL DEFAULT 0,
                recovery_required INTEGER NOT NULL DEFAULT 0,
                recovery_reason TEXT,
                quarantine_id INTEGER,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        ensureColumn(db, "account_cache", "user_json", "TEXT NOT NULL DEFAULT '{}'")
        ensureColumn(db, "account_cache", "subscriptions_json", "TEXT NOT NULL DEFAULT '[]'")
        ensureColumn(db, "account_cache", "templates_json", "TEXT NOT NULL DEFAULT '[]'")
        ensureColumn(db, "account_cache", "rates_json", "TEXT NOT NULL DEFAULT '{}'")
        ensureColumn(db, "account_cache", "cloud_revision", "INTEGER NOT NULL DEFAULT 0")
        ensureColumn(db, "account_cache", "cloud_updated_at", "TEXT")
        ensureColumn(db, "account_cache", "dirty", "INTEGER NOT NULL DEFAULT 0")
        ensureColumn(db, "account_cache", "last_synced_at", "TEXT")
        ensureColumn(db, "account_cache", "sync_frequency", "TEXT NOT NULL DEFAULT 'realtime'")
        ensureColumn(db, "account_cache", "pending_mutation_id", "TEXT")
        ensureColumn(db, "account_cache", "pending_payload_sha256", "TEXT")
        ensureColumn(db, "account_cache", "pending_conflict", "INTEGER NOT NULL DEFAULT 0")
        ensureColumn(db, "account_cache", "recovery_required", "INTEGER NOT NULL DEFAULT 0")
        ensureColumn(db, "account_cache", "recovery_reason", "TEXT")
        ensureColumn(db, "account_cache", "quarantine_id", "INTEGER")
        ensureColumn(db, "account_cache", "updated_at", "INTEGER NOT NULL DEFAULT 0")

        db.execSQL("CREATE TABLE IF NOT EXISTS metadata (entry_key TEXT PRIMARY KEY, entry_value TEXT NOT NULL)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS backup_history (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id TEXT NOT NULL,
                subscriptions_json TEXT NOT NULL,
                reason TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        ensureColumn(db, "backup_history", "user_id", "TEXT NOT NULL DEFAULT ''")
        ensureColumn(db, "backup_history", "subscriptions_json", "TEXT NOT NULL DEFAULT '[]'")
        ensureColumn(db, "backup_history", "reason", "TEXT NOT NULL DEFAULT 'legacy'")
        ensureColumn(db, "backup_history", "created_at", "INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS backup_history_user_created " +
                "ON backup_history(user_id, created_at DESC, id DESC)",
        )
        createLegacyTables(db)
        createQuarantineTable(db)
    }

    private fun createLegacyTables(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS legacy_account_cache (
                user_id TEXT PRIMARY KEY,
                subscriptions_json TEXT NOT NULL DEFAULT '[]',
                source_subscriptions_json TEXT NOT NULL DEFAULT '[]',
                decode_errors_json TEXT NOT NULL DEFAULT '[]',
                subscriptions_sha256 TEXT NOT NULL DEFAULT '',
                subscription_ids_json TEXT NOT NULL DEFAULT '[]',
                dirty INTEGER NOT NULL DEFAULT 0,
                sync_frequency TEXT NOT NULL DEFAULT 'realtime',
                snapshot_sha256 TEXT NOT NULL,
                captured_at INTEGER NOT NULL,
                imported_at INTEGER
            )
            """.trimIndent(),
        )
        ensureColumn(db, "legacy_account_cache", "subscriptions_json", "TEXT NOT NULL DEFAULT '[]'")
        ensureColumn(db, "legacy_account_cache", "source_subscriptions_json", "TEXT NOT NULL DEFAULT '[]'")
        ensureColumn(db, "legacy_account_cache", "decode_errors_json", "TEXT NOT NULL DEFAULT '[]'")
        ensureColumn(db, "legacy_account_cache", "subscriptions_sha256", "TEXT NOT NULL DEFAULT ''")
        ensureColumn(db, "legacy_account_cache", "subscription_ids_json", "TEXT NOT NULL DEFAULT '[]'")
        ensureColumn(db, "legacy_account_cache", "dirty", "INTEGER NOT NULL DEFAULT 0")
        ensureColumn(db, "legacy_account_cache", "sync_frequency", "TEXT NOT NULL DEFAULT 'realtime'")
        ensureColumn(db, "legacy_account_cache", "snapshot_sha256", "TEXT NOT NULL DEFAULT ''")
        ensureColumn(db, "legacy_account_cache", "captured_at", "INTEGER NOT NULL DEFAULT 0")
        ensureColumn(db, "legacy_account_cache", "imported_at", "INTEGER")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS legacy_capture (
                snapshot_sha256 TEXT PRIMARY KEY,
                raw_snapshot_json TEXT NOT NULL,
                decode_errors_json TEXT NOT NULL DEFAULT '[]',
                captured_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        ensureColumn(db, "legacy_capture", "raw_snapshot_json", "TEXT NOT NULL DEFAULT '{}'")
        ensureColumn(db, "legacy_capture", "decode_errors_json", "TEXT NOT NULL DEFAULT '[]'")
        ensureColumn(db, "legacy_capture", "captured_at", "INTEGER NOT NULL DEFAULT 0")
    }

    private fun createQuarantineTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS corrupt_data_quarantine (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id TEXT NOT NULL,
                source TEXT NOT NULL,
                raw_json TEXT NOT NULL,
                reason TEXT NOT NULL,
                content_sha256 TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                UNIQUE(user_id, source, content_sha256)
            )
            """.trimIndent(),
        )
    }

    private fun ensureColumn(db: SQLiteDatabase, table: String, column: String, definition: String) {
        val exists = db.rawQuery("PRAGMA table_info($table)", null).use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            generateSequence { if (cursor.moveToNext()) cursor.getString(nameIndex) else null }
                .any { it == column }
        }
        if (!exists) db.execSQL("ALTER TABLE $table ADD COLUMN $column $definition")
    }

    fun loadAccount(userId: String): CachedAccount? {
        val raw = readableDatabase.query(
            "account_cache",
            null,
            "user_id=?",
            arrayOf(userId),
            null,
            null,
            null,
        ).use { cursor ->
            if (!cursor.moveToFirst()) return null
            fun string(name: String): String = cursor.getString(cursor.getColumnIndexOrThrow(name))
            fun nullableString(name: String): String? =
                cursor.getColumnIndexOrThrow(name).let { index -> if (cursor.isNull(index)) null else cursor.getString(index) }
            fun nullableLong(name: String): Long? =
                cursor.getColumnIndexOrThrow(name).let { index -> if (cursor.isNull(index)) null else cursor.getLong(index) }
            RawAccountRow(
                userId = userId,
                userJson = string("user_json"),
                subscriptionsJson = string("subscriptions_json"),
                templatesJson = string("templates_json"),
                ratesJson = string("rates_json"),
                cloudRevision = cursor.getInt(cursor.getColumnIndexOrThrow("cloud_revision")),
                cloudUpdatedAt = nullableString("cloud_updated_at"),
                dirty = cursor.getInt(cursor.getColumnIndexOrThrow("dirty")) == 1,
                lastSyncedAt = nullableString("last_synced_at"),
                syncFrequency = SyncFrequency.fromWire(string("sync_frequency")),
                pendingMutationId = nullableString("pending_mutation_id"),
                pendingPayloadSha256 = nullableString("pending_payload_sha256"),
                pendingConflict = cursor.getInt(cursor.getColumnIndexOrThrow("pending_conflict")) == 1,
                recoveryRequired = cursor.getInt(cursor.getColumnIndexOrThrow("recovery_required")) == 1,
                recoveryReason = nullableString("recovery_reason"),
                quarantineId = nullableLong("quarantine_id"),
            )
        }
        return try {
            decodeAccount(raw)
        } catch (error: Throwable) {
            recoverAndQuarantine(raw, error.message ?: error.javaClass.simpleName)
        }
    }

    private fun decodeAccount(raw: RawAccountRow): CachedAccount {
        val user = JsonCodec.userFromJson(JSONObject(raw.userJson))
        require(user.id == raw.userId && user.id.isNotBlank()) { "账户缓存身份不匹配" }
        val subscriptions = JsonCodec.subscriptionsFromJsonStrict(JSONArray(raw.subscriptionsJson))
        val templateArray = JSONArray(raw.templatesJson)
        requireObjectArray(templateArray, "服务模板")
        val templates = JsonCodec.templatesFromJson(templateArray)
        val rates = JsonCodec.ratesFromJson(JSONObject(raw.ratesJson))

        val pendingId = raw.pendingMutationId?.takeIf(String::isNotBlank)
        val pendingHash = raw.pendingPayloadSha256?.takeIf(String::isNotBlank)
        val pendingIsCurrent = pendingId != null && pendingHash == JsonCodec.subscriptionsSha256(subscriptions)
        if ((pendingId != null || pendingHash != null) && !pendingIsCurrent) {
            clearPendingMutation(raw.userId)
        }
        return CachedAccount(
            user = user,
            subscriptions = subscriptions,
            templates = templates,
            currencyRates = rates,
            cloudRevision = raw.cloudRevision,
            cloudUpdatedAt = raw.cloudUpdatedAt,
            dirty = raw.dirty,
            lastSyncedAt = raw.lastSyncedAt,
            syncFrequency = raw.syncFrequency,
            pendingMutationId = pendingId.takeIf { pendingIsCurrent },
            pendingPayloadSha256 = pendingHash.takeIf { pendingIsCurrent },
            pendingConflict = raw.pendingConflict,
            recoveryRequired = raw.recoveryRequired,
            recoveryReason = raw.recoveryReason,
            quarantineId = raw.quarantineId,
        )
    }

    private fun recoverAndQuarantine(raw: RawAccountRow, reason: String): CachedAccount {
        val quarantineId = quarantineAccount(raw, reason)
        val recoveredSubscriptions = runCatching {
            JsonCodec.subscriptionsFromJsonStrict(JSONArray(raw.subscriptionsJson))
        }.getOrNull() ?: latestBackup(raw.userId).orEmpty()
        val recoveredUser = runCatching { JsonCodec.userFromJson(JSONObject(raw.userJson)) }
            .getOrNull()
            ?.takeIf { it.id == raw.userId }
            ?: AccountUser(raw.userId, "", "订阅用户", "", false)
        val recoveredTemplates = runCatching {
            JSONArray(raw.templatesJson).also { requireObjectArray(it, "服务模板") }
                .let(JsonCodec::templatesFromJson)
        }.getOrElse { emptyList() }
        val recoveredRates = runCatching { JsonCodec.ratesFromJson(JSONObject(raw.ratesJson)) }
            .getOrElse { CurrencyRates() }
        return CachedAccount(
            user = recoveredUser,
            subscriptions = recoveredSubscriptions,
            templates = recoveredTemplates,
            currencyRates = recoveredRates,
            cloudRevision = raw.cloudRevision,
            cloudUpdatedAt = raw.cloudUpdatedAt,
            dirty = true,
            lastSyncedAt = raw.lastSyncedAt,
            syncFrequency = raw.syncFrequency,
            pendingConflict = raw.pendingConflict,
            recoveryRequired = true,
            recoveryReason = reason,
            quarantineId = quarantineId,
        )
    }

    private fun quarantineAccount(raw: RawAccountRow, reason: String): Long {
        val payload = JSONObject()
            .put("user_id", raw.userId)
            .put("user_json", raw.userJson)
            .put("subscriptions_json", raw.subscriptionsJson)
            .put("templates_json", raw.templatesJson)
            .put("rates_json", raw.ratesJson)
            .put("cloud_revision", raw.cloudRevision)
            .put("cloud_updated_at", raw.cloudUpdatedAt ?: JSONObject.NULL)
            .put("dirty", raw.dirty)
            .put("last_synced_at", raw.lastSyncedAt ?: JSONObject.NULL)
            .put("sync_frequency", raw.syncFrequency.wireValue)
            .put("pending_mutation_id", raw.pendingMutationId ?: JSONObject.NULL)
            .put("pending_payload_sha256", raw.pendingPayloadSha256 ?: JSONObject.NULL)
            .put("pending_conflict", raw.pendingConflict)
            .toString()
        val checksum = JsonCodec.textSha256(payload)
        val database = writableDatabase
        database.beginTransaction()
        try {
            var quarantineId = database.query(
                "corrupt_data_quarantine",
                arrayOf("id"),
                "user_id=? AND source=? AND content_sha256=?",
                arrayOf(raw.userId, ACCOUNT_CACHE_SOURCE, checksum),
                null,
                null,
                null,
                "1",
            ).use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else null }
            if (quarantineId == null) {
                quarantineId = database.insertOrThrow(
                    "corrupt_data_quarantine",
                    null,
                    ContentValues().apply {
                        put("user_id", raw.userId)
                        put("source", ACCOUNT_CACHE_SOURCE)
                        put("raw_json", payload)
                        put("reason", reason)
                        put("content_sha256", checksum)
                        put("created_at", System.currentTimeMillis())
                    },
                )
            }
            check(database.update(
                "account_cache",
                ContentValues().apply {
                    put("recovery_required", 1)
                    put("recovery_reason", reason)
                    put("quarantine_id", quarantineId)
                },
                "user_id=?",
                arrayOf(raw.userId),
            ) == 1) { "损坏缓存隔离标记写入失败" }
            database.setTransactionSuccessful()
            return quarantineId
        } finally {
            database.endTransaction()
        }
    }

    fun saveAccount(account: CachedAccount) {
        upsertAccount(writableDatabase, account)
    }

    private fun upsertAccount(database: SQLiteDatabase, account: CachedAccount) {
        val values = accountValues(account)
        val updated = database.update("account_cache", values, "user_id=?", arrayOf(account.user.id))
        if (updated == 0) database.insertOrThrow("account_cache", null, values)
        else check(updated == 1) { "账户缓存写入数量异常" }
    }

    private fun accountValues(account: CachedAccount): ContentValues = ContentValues().apply {
        put("user_id", account.user.id)
        put("user_json", JsonCodec.userToJson(account.user).toString())
        put("subscriptions_json", JsonCodec.subscriptionsToJson(account.subscriptions).toString())
        put("templates_json", JsonCodec.templatesToJson(account.templates).toString())
        put("rates_json", JsonCodec.ratesToJson(account.currencyRates).toString())
        put("cloud_revision", account.cloudRevision)
        put("cloud_updated_at", account.cloudUpdatedAt)
        put("dirty", if (account.dirty) 1 else 0)
        put("last_synced_at", account.lastSyncedAt)
        put("sync_frequency", account.syncFrequency.wireValue)
        put("pending_mutation_id", account.pendingMutationId)
        put("pending_payload_sha256", account.pendingPayloadSha256)
        put("pending_conflict", if (account.pendingConflict) 1 else 0)
        put("recovery_required", if (account.recoveryRequired) 1 else 0)
        put("recovery_reason", account.recoveryReason)
        account.quarantineId?.let { put("quarantine_id", it) } ?: putNull("quarantine_id")
        put("updated_at", System.currentTimeMillis())
    }

    fun ensurePendingMutation(
        userId: String,
        payloadSha256: String,
        proposedMutationId: String,
    ): PendingMutationIdentity {
        val database = writableDatabase
        database.beginTransaction()
        try {
            val existing = database.query(
                "account_cache",
                arrayOf("pending_mutation_id", "pending_payload_sha256"),
                "user_id=?",
                arrayOf(userId),
                null,
                null,
                null,
                "1",
            ).use { cursor ->
                check(cursor.moveToFirst()) { "账户缓存不存在，无法创建同步凭据" }
                val id = if (cursor.isNull(0)) null else cursor.getString(0)
                val hash = if (cursor.isNull(1)) null else cursor.getString(1)
                id to hash
            }
            val identity = selectPendingMutationIdentity(
                existingId = existing.first,
                existingPayloadSha256 = existing.second,
                payloadSha256 = payloadSha256,
                newId = { proposedMutationId },
            )
            check(database.update(
                "account_cache",
                ContentValues().apply {
                    put("pending_mutation_id", identity.id)
                    put("pending_payload_sha256", identity.payloadSha256)
                    put("updated_at", System.currentTimeMillis())
                },
                "user_id=?",
                arrayOf(userId),
            ) == 1) { "同步凭据持久化失败" }
            database.setTransactionSuccessful()
            return identity
        } finally {
            database.endTransaction()
        }
    }

    fun acknowledgeSync(
        userId: String,
        mutationId: String,
        payloadSha256: String,
        revision: Int,
        updatedAt: String?,
    ): Boolean {
        val database = writableDatabase
        database.beginTransaction()
        try {
            val matches = database.query(
                "account_cache",
                arrayOf("subscriptions_json", "pending_mutation_id", "pending_payload_sha256"),
                "user_id=?",
                arrayOf(userId),
                null,
                null,
                null,
                "1",
            ).use { cursor ->
                if (!cursor.moveToFirst()) false else runCatching {
                    val subscriptions = JsonCodec.subscriptionsFromJsonStrict(JSONArray(cursor.getString(0)))
                    JsonCodec.subscriptionsSha256(subscriptions) == payloadSha256 &&
                        !cursor.isNull(1) && cursor.getString(1) == mutationId &&
                        !cursor.isNull(2) && cursor.getString(2) == payloadSha256
                }.getOrDefault(false)
            }
            val values = ContentValues().apply {
                put("cloud_revision", revision)
                put("cloud_updated_at", updatedAt)
                put("last_synced_at", updatedAt)
                if (matches) {
                    put("dirty", 0)
                    putNull("pending_mutation_id")
                    putNull("pending_payload_sha256")
                    put("pending_conflict", 0)
                    put("recovery_required", 0)
                    putNull("recovery_reason")
                    putNull("quarantine_id")
                }
                put("updated_at", System.currentTimeMillis())
            }
            val updated = database.update("account_cache", values, "user_id=?", arrayOf(userId))
            check(updated in 0..1) { "同步确认写入数量异常" }
            database.setTransactionSuccessful()
            return matches && updated == 1
        } finally {
            database.endTransaction()
        }
    }

    private fun clearPendingMutation(userId: String) {
        writableDatabase.update(
            "account_cache",
            ContentValues().apply {
                putNull("pending_mutation_id")
                putNull("pending_payload_sha256")
            },
            "user_id=?",
            arrayOf(userId),
        )
    }

    fun saveBackup(userId: String, subscriptions: List<Subscription>, reason: String) {
        val database = writableDatabase
        database.beginTransaction()
        try {
            database.insertOrThrow(
                "backup_history",
                null,
                ContentValues().apply {
                    put("user_id", userId)
                    put("subscriptions_json", JsonCodec.subscriptionsToJson(subscriptions).toString())
                    put("reason", reason)
                    put("created_at", System.currentTimeMillis())
                },
            )
            database.execSQL(
                "DELETE FROM backup_history WHERE user_id=? AND id NOT IN " +
                    "(SELECT id FROM backup_history WHERE user_id=? ORDER BY created_at DESC, id DESC LIMIT 30)",
                arrayOf(userId, userId),
            )
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
    }

    fun latestBackup(userId: String): List<Subscription>? = readableDatabase.query(
        "backup_history",
        arrayOf("subscriptions_json"),
        "user_id=? AND reason NOT IN (?,?,?)",
        arrayOf(
            userId,
            "sync-conflict-cloud",
            "corrupt-cache-cloud-recovery",
            "force-local-replaced-cloud",
        ),
        null,
        null,
        "created_at DESC, id DESC",
        "30",
    ).use { cursor ->
        while (cursor.moveToNext()) {
            runCatching {
                JsonCodec.subscriptionsFromJsonStrict(JSONArray(cursor.getString(0)))
            }.getOrNull()?.let { return@use it }
        }
        null
    }

    fun stageLegacyAccounts(payload: LegacyPayload) {
        val database = writableDatabase
        val capturedAt = System.currentTimeMillis()
        database.beginTransaction()
        try {
            val rawSnapshot = payload.rawSnapshotJson.ifBlank { legacyPayloadJson(payload).toString() }
            val captureExists = database.query(
                "legacy_capture",
                arrayOf("snapshot_sha256"),
                "snapshot_sha256=?",
                arrayOf(payload.snapshotSha256),
                null,
                null,
                null,
                "1",
            ).use { it.moveToFirst() }
            if (!captureExists) {
                database.insertOrThrow(
                    "legacy_capture",
                    null,
                    ContentValues().apply {
                        put("snapshot_sha256", payload.snapshotSha256)
                        put("raw_snapshot_json", rawSnapshot)
                        put("decode_errors_json", JSONArray(payload.decodeErrors).toString())
                        put("captured_at", capturedAt)
                    },
                )
            }
            payload.accounts.forEach { account ->
                val normalized = JsonCodec.subscriptionsToJson(account.subscriptions).toString()
                val hash = JsonCodec.subscriptionsSha256(account.subscriptions)
                val ids = JSONArray(account.subscriptions.map { it.id }).toString()
                val existing = database.query(
                    "legacy_account_cache",
                    arrayOf("snapshot_sha256", "imported_at"),
                    "user_id=?",
                    arrayOf(account.userId),
                    null,
                    null,
                    null,
                    "1",
                ).use { cursor ->
                    if (!cursor.moveToFirst()) null else cursor.getString(0) to
                        if (cursor.isNull(1)) null else cursor.getLong(1)
                }
                val values = ContentValues().apply {
                    put("user_id", account.userId)
                    put("subscriptions_json", normalized)
                    put("source_subscriptions_json", account.rawSubscriptionsJson.ifBlank { normalized })
                    put("decode_errors_json", JSONArray(account.decodeErrors).toString())
                    put("subscriptions_sha256", hash)
                    put("subscription_ids_json", ids)
                    put("dirty", if (account.dirty) 1 else 0)
                    put("sync_frequency", account.syncFrequency.wireValue)
                    put("snapshot_sha256", payload.snapshotSha256)
                    put("captured_at", capturedAt)
                    if (existing?.first == payload.snapshotSha256 && existing.second != null) {
                        put("imported_at", existing.second)
                    } else {
                        putNull("imported_at")
                    }
                }
                if (existing == null) database.insertOrThrow("legacy_account_cache", null, values)
                else check(database.update("legacy_account_cache", values, "user_id=?", arrayOf(account.userId)) == 1)
            }
            setMetadata(database, LEGACY_SNAPSHOT_HASH_KEY, payload.snapshotSha256)
            setMetadata(database, LEGACY_CAPTURED_ACCOUNTS_KEY, payload.accounts.size.toString())
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
    }

    fun pendingLegacyAccount(userId: String): LegacyAccountPayload? {
        val row = readableDatabase.query(
            "legacy_account_cache",
            arrayOf(
                "subscriptions_json",
                "dirty",
                "sync_frequency",
                "source_subscriptions_json",
                "decode_errors_json",
                "subscriptions_sha256",
                "subscription_ids_json",
            ),
            "user_id=? AND imported_at IS NULL",
            arrayOf(userId),
            null,
            null,
            null,
            "1",
        ).use { cursor ->
            if (!cursor.moveToFirst()) return null
            LegacyRow(
                subscriptionsJson = cursor.getString(0),
                dirty = cursor.getInt(1) == 1,
                syncFrequency = SyncFrequency.fromWire(cursor.getString(2)),
                sourceSubscriptionsJson = cursor.getString(3),
                decodeErrorsJson = cursor.getString(4),
                subscriptionsSha256 = cursor.getString(5),
                subscriptionIdsJson = cursor.getString(6),
            )
        }
        val subscriptions = JsonCodec.subscriptionsFromJsonStrict(JSONArray(row.subscriptionsJson))
        val hash = JsonCodec.subscriptionsSha256(subscriptions)
        val ids = subscriptions.map { it.id }
        if (row.subscriptionsSha256.isBlank()) {
            check(writableDatabase.update(
                "legacy_account_cache",
                ContentValues().apply {
                    put("subscriptions_sha256", hash)
                    put("subscription_ids_json", JSONArray(ids).toString())
                },
                "user_id=? AND imported_at IS NULL",
                arrayOf(userId),
            ) == 1) { "旧版账本校验信息补全失败" }
        } else {
            require(row.subscriptionsSha256 == hash) { "旧版账本内容校验失败" }
            require(jsonStringList(row.subscriptionIdsJson) == ids) { "旧版账本 ID 校验失败" }
        }
        return LegacyAccountPayload(
            userId = userId,
            subscriptions = subscriptions,
            dirty = row.dirty,
            syncFrequency = row.syncFrequency,
            rawSubscriptionsJson = row.sourceSubscriptionsJson,
            decodeErrors = jsonStringList(row.decodeErrorsJson),
        )
    }

    fun hasPendingLegacyAccount(userId: String): Boolean = readableDatabase.query(
        "legacy_account_cache",
        arrayOf("user_id"),
        "user_id=? AND imported_at IS NULL",
        arrayOf(userId),
        null,
        null,
        null,
        "1",
    ).use { it.moveToFirst() }

    fun saveAccountAndMarkLegacyImported(
        account: CachedAccount,
        legacyUserId: String,
        sourceSubscriptions: List<Subscription>,
    ) {
        val database = writableDatabase
        database.beginTransaction()
        try {
            upsertAccount(database, account)
            val persisted = database.query(
                "account_cache",
                arrayOf("subscriptions_json"),
                "user_id=?",
                arrayOf(account.user.id),
                null,
                null,
                null,
                "1",
            ).use { cursor ->
                check(cursor.moveToFirst()) { "旧版账本目标账户不存在" }
                JsonCodec.subscriptionsFromJsonStrict(JSONArray(cursor.getString(0)))
            }
            require(JsonCodec.subscriptionsSha256(persisted) == JsonCodec.subscriptionsSha256(account.subscriptions)) {
                "旧版账本目标内容校验失败"
            }
            require(persisted.map { it.id } == account.subscriptions.map { it.id }) { "旧版账本目标 ID 校验失败" }

            val staged = database.query(
                "legacy_account_cache",
                arrayOf("subscriptions_sha256", "subscription_ids_json"),
                "user_id=? AND imported_at IS NULL",
                arrayOf(legacyUserId),
                null,
                null,
                null,
                "1",
            ).use { cursor ->
                check(cursor.moveToFirst()) { "待导入旧版账本不存在" }
                cursor.getString(0) to jsonStringList(cursor.getString(1))
            }
            val sourceHash = JsonCodec.subscriptionsSha256(sourceSubscriptions)
            require(staged.first == sourceHash) { "旧版账本来源内容校验失败" }
            require(staged.second == sourceSubscriptions.map { it.id }) { "旧版账本来源 ID 校验失败" }

            check(database.update(
                "legacy_account_cache",
                ContentValues().apply { put("imported_at", System.currentTimeMillis()) },
                "user_id=? AND imported_at IS NULL",
                arrayOf(legacyUserId),
            ) == 1) { "旧版账本导入状态写入失败" }
            setMetadata(database, "$LEGACY_IMPORTED_COUNT_PREFIX$legacyUserId", sourceSubscriptions.size.toString())
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
    }

    fun metadata(key: String): String? = readableDatabase.query(
        "metadata",
        arrayOf("entry_value"),
        "entry_key=?",
        arrayOf(key),
        null,
        null,
        null,
    ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }

    fun setMetadata(key: String, value: String) = setMetadata(writableDatabase, key, value)

    private fun setMetadata(database: SQLiteDatabase, key: String, value: String) {
        val values = ContentValues().apply {
            put("entry_key", key)
            put("entry_value", value)
        }
        val updated = database.update("metadata", values, "entry_key=?", arrayOf(key))
        if (updated == 0) database.insertOrThrow("metadata", null, values)
        else check(updated == 1) { "元数据写入数量异常" }
    }

    private fun requireObjectArray(values: JSONArray, label: String) {
        for (index in 0 until values.length()) {
            require(values.optJSONObject(index) != null) { "$label 第 ${index + 1} 项格式无效" }
        }
    }

    private fun jsonStringList(raw: String): List<String> {
        val values = JSONArray(raw)
        return buildList {
            for (index in 0 until values.length()) {
                val value = values.opt(index)
                require(value is String) { "校验 ID 格式无效" }
                add(value)
            }
        }
    }

    private fun legacyPayloadJson(payload: LegacyPayload): JSONObject = JSONObject()
        .put("snapshotSha256", payload.snapshotSha256)
        .put("user", payload.user?.let(JsonCodec::userToJson) ?: JSONObject.NULL)
        .put("decodeErrors", JSONArray(payload.decodeErrors))
        .put("unresolvedEntries", JSONArray(payload.unresolvedEntries))
        .put("accounts", JSONArray(payload.accounts.map { account ->
            JSONObject()
                .put("userId", account.userId)
                .put("subscriptions", JsonCodec.subscriptionsToJson(account.subscriptions))
                .put("dirty", account.dirty)
                .put("syncFrequency", account.syncFrequency.wireValue)
        }))

    private data class RawAccountRow(
        val userId: String,
        val userJson: String,
        val subscriptionsJson: String,
        val templatesJson: String,
        val ratesJson: String,
        val cloudRevision: Int,
        val cloudUpdatedAt: String?,
        val dirty: Boolean,
        val lastSyncedAt: String?,
        val syncFrequency: SyncFrequency,
        val pendingMutationId: String?,
        val pendingPayloadSha256: String?,
        val pendingConflict: Boolean,
        val recoveryRequired: Boolean,
        val recoveryReason: String?,
        val quarantineId: Long?,
    )

    private data class LegacyRow(
        val subscriptionsJson: String,
        val dirty: Boolean,
        val syncFrequency: SyncFrequency,
        val sourceSubscriptionsJson: String,
        val decodeErrorsJson: String,
        val subscriptionsSha256: String,
        val subscriptionIdsJson: String,
    )

    companion object {
        private const val DATABASE_NAME = "dingyue-native.db"
        private const val DATABASE_VERSION = 5
        private const val ACCOUNT_CACHE_SOURCE = "account_cache"
        const val LEGACY_MIGRATION_KEY = "legacy_webview_migration_v1"
        const val LEGACY_SNAPSHOT_HASH_KEY = "legacy_webview_snapshot_sha256"
        const val LEGACY_CAPTURED_ACCOUNTS_KEY = "legacy_webview_captured_accounts"
        const val LEGACY_IMPORTED_COUNT_PREFIX = "legacy_webview_imported_count_"
    }
}
