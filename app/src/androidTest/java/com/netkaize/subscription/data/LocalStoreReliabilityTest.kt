package com.netkaize.subscription.data

import android.content.ContentValues
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalStoreReliabilityTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val databaseName = "local-store-reliability-test.db"
    private lateinit var store: LocalStore

    @Before
    fun setUp() {
        context.deleteDatabase(databaseName)
        store = LocalStore(context, databaseName)
    }

    @After
    fun tearDown() {
        store.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun responseForOldPayloadCannotClearNewDirtyPayload() {
        val initial = account(listOf(Subscription(id = "sub-1", name = "A")))
        store.saveAccount(initial)
        val firstHash = JsonCodec.subscriptionsSha256(initial.subscriptions)
        val first = store.ensurePendingMutation("user-1", firstHash, "mutation-1")
        val retry = store.ensurePendingMutation("user-1", firstHash, "mutation-2")
        assertEquals(first.id, retry.id)

        val changed = initial.copy(
            subscriptions = listOf(Subscription(id = "sub-2", name = "B")),
            pendingMutationId = null,
            pendingPayloadSha256 = null,
        )
        store.saveAccount(changed)
        val secondHash = JsonCodec.subscriptionsSha256(changed.subscriptions)
        val second = store.ensurePendingMutation("user-1", secondHash, "mutation-2")

        assertFalse(store.acknowledgeSync("user-1", first.id, firstHash, 2, "2026-07-26T00:00:00Z"))
        val afterOldResponse = store.loadAccount("user-1")!!
        assertTrue(afterOldResponse.dirty)
        assertEquals(second.id, afterOldResponse.pendingMutationId)
        assertTrue(store.acknowledgeSync("user-1", second.id, secondHash, 3, "2026-07-26T00:01:00Z"))
        assertFalse(store.loadAccount("user-1")!!.dirty)
    }

    @Test
    fun successfulAcknowledgementClearsPersistedConflictAtomically() {
        val conflicted = account(listOf(Subscription(id = "sub-1", name = "A"))).copy(
            pendingConflict = true,
        )
        store.saveAccount(conflicted)
        val hash = JsonCodec.subscriptionsSha256(conflicted.subscriptions)
        val pending = store.ensurePendingMutation("user-1", hash, "mutation-1")

        assertTrue(store.acknowledgeSync("user-1", pending.id, hash, 2, "2026-07-26T00:00:00Z"))

        store.close()
        store = LocalStore(context, databaseName)
        val reloaded = store.loadAccount("user-1")!!
        assertFalse(reloaded.dirty)
        assertFalse(reloaded.pendingConflict)
        assertEquals(null, reloaded.pendingMutationId)
    }

    @Test
    fun corruptDirtyCacheIsQuarantinedAndReturnedAsRecoveryInsteadOfMissing() {
        store.saveAccount(account(listOf(Subscription(id = "sub-1", name = "A"))))
        store.writableDatabase.update(
            "account_cache",
            ContentValues().apply { put("subscriptions_json", "[{not-json]") },
            "user_id=?",
            arrayOf("user-1"),
        )

        val recovered = store.loadAccount("user-1")

        assertNotNull(recovered)
        assertTrue(recovered!!.dirty)
        assertTrue(recovered.recoveryRequired)
        assertNotNull(recovered.quarantineId)
        val quarantined = store.readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM corrupt_data_quarantine WHERE user_id=?",
            arrayOf("user-1"),
        ).use { cursor -> cursor.moveToFirst(); cursor.getInt(0) }
        assertEquals(1, quarantined)
    }

    @Test
    fun latestBackupBreaksSameMillisecondTieByDescendingId() {
        val database = store.writableDatabase
        fun insert(id: String, name: String, reason: String = "test") {
            database.insertOrThrow(
                "backup_history",
                null,
                ContentValues().apply {
                    put("user_id", "user-1")
                    put("subscriptions_json", JsonCodec.subscriptionsToJson(listOf(Subscription(id = id, name = name))).toString())
                    put("reason", reason)
                    put("created_at", 1000L)
                },
            )
        }
        insert("sub-1", "old")
        insert("sub-2", "new")
        insert("cloud-only", "remote comparison", "sync-conflict-cloud")

        assertEquals("sub-2", store.latestBackup("user-1")!!.single().id)
    }

    @Test
    fun stagedLegacyRemainsPendingUntilAtomicAdoptionCompletes() {
        val legacySubscription = Subscription(id = "legacy-sub", name = "旧版订阅")
        store.stageLegacyAccounts(
            LegacyPayload(
                token = null,
                user = null,
                accounts = listOf(
                    LegacyAccountPayload(
                        userId = "user-1",
                        subscriptions = listOf(legacySubscription),
                        dirty = true,
                        syncFrequency = SyncFrequency.HOURS_24,
                    ),
                ),
                snapshotSha256 = "snapshot-1",
            ),
        )
        assertTrue(store.hasPendingLegacyAccount("user-1"))
        val adopted = account(listOf(legacySubscription)).copy(syncFrequency = SyncFrequency.HOURS_24)

        store.saveAccountAndMarkLegacyImported(adopted, "user-1", listOf(legacySubscription))

        assertFalse(store.hasPendingLegacyAccount("user-1"))
        assertEquals("legacy-sub", store.loadAccount("user-1")!!.subscriptions.single().id)
        assertEquals(SyncFrequency.HOURS_24, store.loadAccount("user-1")!!.syncFrequency)
    }

    @Test
    fun oldAndSameVersionDatabasesAreRepairedWithoutDroppingAccountData() {
        store.close()
        context.deleteDatabase(databaseName)
        val database = context.openOrCreateDatabase(databaseName, android.content.Context.MODE_PRIVATE, null)
        database.execSQL(
            """
            CREATE TABLE account_cache (
                user_id TEXT PRIMARY KEY,
                user_json TEXT NOT NULL,
                subscriptions_json TEXT NOT NULL DEFAULT '[]',
                dirty INTEGER NOT NULL DEFAULT 0,
                updated_at INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
        val subscription = Subscription(id = "legacy-sub", name = "保留的订阅")
        database.insertOrThrow(
            "account_cache",
            null,
            ContentValues().apply {
                put("user_id", "user-1")
                put(
                    "user_json",
                    JsonCodec.userToJson(AccountUser("user-1", "owner@example.com", "Owner", "2026-01-01", false)).toString(),
                )
                put("subscriptions_json", JsonCodec.subscriptionsToJson(listOf(subscription)).toString())
                put("dirty", 1)
                put("updated_at", 1000L)
            },
        )
        // Version 5 deliberately simulates an early build that claimed the latest version while
        // missing columns. onOpen must repair it even though onUpgrade will not run.
        database.version = 5
        database.close()

        store = LocalStore(context, databaseName)
        val recovered = store.loadAccount("user-1")

        assertNotNull(recovered)
        assertEquals("legacy-sub", recovered!!.subscriptions.single().id)
        assertTrue(recovered.dirty)
        val columns = store.readableDatabase.rawQuery("PRAGMA table_info(account_cache)", null).use { cursor ->
            buildSet {
                val nameIndex = cursor.getColumnIndexOrThrow("name")
                while (cursor.moveToNext()) add(cursor.getString(nameIndex))
            }
        }
        assertTrue(columns.containsAll(setOf("templates_json", "rates_json", "cloud_revision", "sync_frequency", "pending_mutation_id", "pending_conflict", "recovery_required")))
    }

    private fun account(subscriptions: List<Subscription>) = CachedAccount(
        user = AccountUser("user-1", "owner@example.com", "Owner", "2026-01-01", false),
        subscriptions = subscriptions,
        templates = emptyList(),
        currencyRates = CurrencyRates(),
        cloudRevision = 1,
        cloudUpdatedAt = "2026-07-25T00:00:00Z",
        dirty = true,
        lastSyncedAt = "2026-07-25T00:00:00Z",
        syncFrequency = SyncFrequency.HOURS_72,
    )
}
