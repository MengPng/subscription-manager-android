package com.netkaize.subscription

import com.netkaize.subscription.data.BackupEnvelope
import com.netkaize.subscription.data.BillingCycle
import com.netkaize.subscription.data.JsonCodec
import com.netkaize.subscription.data.Subscription
import com.netkaize.subscription.data.selectPendingMutationIdentity
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class JsonCodecTest {
    @Test
    fun unknownSubscriptionFieldsSurviveNativeRoundTrip() {
        val source = JSONObject(
            """
            {
              "id":"sub-1",
              "name":"未来服务",
              "category":"AI",
              "price":42,
              "cycle":"monthly",
              "startDate":"2026-01-31",
              "nextDate":"2026-02-28",
              "status":"active",
              "pauses":[],
              "futureMetadata":{"source":"web","nested":{"kept":true}}
            }
            """.trimIndent(),
        )

        val encoded = JsonCodec.subscriptionToJson(JsonCodec.subscriptionFromJson(source))
        assertEquals("web", encoded.getJSONObject("futureMetadata").getString("source"))
        assertTrue(encoded.getJSONObject("futureMetadata").getJSONObject("nested").getBoolean("kept"))
    }

    @Test
    fun backupChecksumDetectsIncompleteOrModifiedFiles() {
        val raw = JsonCodec.backupToJson(
            BackupEnvelope(
                exportedAt = Instant.parse("2026-07-26T00:00:00Z").toString(),
                accountEmail = "owner@example.com",
                subscriptions = listOf(Subscription(name = "账本")),
            ),
        )
        val modified = JSONObject(raw).apply {
            getJSONArray("subscriptions").getJSONObject(0).put("price", 999)
        }.toString()

        assertThrows(IllegalArgumentException::class.java) {
            JsonCodec.backupFromJson(modified)
        }
    }

    @Test
    fun schemaThreeBackupRequiresChecksumWhileLegacyBackupRemainsImportable() {
        val subscription = JsonCodec.subscriptionToJson(Subscription(id = "sub-1", name = "账本"))
        val modernWithoutChecksum = JSONObject()
            .put("schemaVersion", 3)
            .put("subscriptions", JSONArray().put(subscription))
            .toString()
        val legacyWithoutChecksum = JSONObject()
            .put("schemaVersion", 2)
            .put("subscriptions", JSONArray().put(subscription))
            .toString()

        assertThrows(IllegalArgumentException::class.java) {
            JsonCodec.backupFromJson(modernWithoutChecksum)
        }
        assertEquals(1, JsonCodec.backupFromJson(legacyWithoutChecksum).subscriptions.size)
    }

    @Test
    fun canonicalSubscriptionHashIgnoresJsonObjectKeyOrder() {
        val first = JSONObject(
            """{"id":"sub-1","name":"AI","price":9,"cycle":"monthly","startDate":"2026-01-01","status":"active","pauses":[],"future":{"b":2,"a":1}}""",
        )
        val second = JSONObject(
            """{"future":{"a":1,"b":2},"pauses":[],"status":"active","startDate":"2026-01-01","cycle":"monthly","price":9,"name":"AI","id":"sub-1"}""",
        )

        val firstHash = JsonCodec.subscriptionsSha256(listOf(JsonCodec.subscriptionFromJsonStrict(first)))
        val secondHash = JsonCodec.subscriptionsSha256(listOf(JsonCodec.subscriptionFromJsonStrict(second)))

        assertEquals(firstHash, secondHash)
    }

    @Test
    fun strictDecoderRejectsRowsThatTolerantLegacyDecoderWouldRepairOrSkip() {
        val malformed = JSONArray(
            """[{"name":"缺少 ID","price":-1,"cycle":"mystery","startDate":"not-a-date","status":"active"},42]""",
        )

        assertThrows(IllegalArgumentException::class.java) {
            JsonCodec.subscriptionsFromJsonStrict(malformed)
        }
    }

    @Test
    fun strictDecoderAcceptsAndNormalizesLegacyOneTimeNextDate() {
        val legacy = JSONObject(
            """{"id":"once-1","name":"买断服务","price":299,"cycle":"once","startDate":"2025-04-18","nextDate":"2025-04-18","status":"active","pauses":[]}""",
        )

        val decoded = JsonCodec.subscriptionFromJsonStrict(legacy)

        assertEquals(BillingCycle.ONCE, decoded.cycle)
        assertEquals(null, decoded.nextDate)
    }

    @Test
    fun canceledSubscriptionWithoutCanceledAtGetsStableHistoricalBoundary() {
        val legacy = JSONObject(
            """{"id":"canceled-1","name":"已取消服务","price":100,"cycle":"monthly","startDate":"2026-01-05","nextDate":"2026-04-05","status":"canceled","pauses":[]}""",
        )

        val decoded = JsonCodec.subscriptionFromJsonStrict(legacy)

        assertEquals(java.time.LocalDate.of(2026, 4, 5), decoded.canceledAt)
    }

    @Test
    fun strictDecoderAcceptsServerLegacyScalarShapesAndNormalizesThem() {
        val legacy = JSONObject(
            """{"id":"legacy-scalars","name":"旧数据","price":"19.90","cycle":"monthly","startDate":"2026-01-01","nextDate":"2026-02-01","lastReviewedAt":"","status":"active","usageCount":"3","pauses":[{"startDate":"2026-01-10","endDate":""}]}""",
        )

        val decoded = JsonCodec.subscriptionFromJsonStrict(legacy)

        assertEquals(19.9, decoded.priceCny, 0.0001)
        assertEquals(3, decoded.usageCount)
        assertEquals(null, decoded.lastReviewedAt)
        assertEquals(null, decoded.pauses.single().endDate)
    }

    @Test
    fun pendingMutationIdIsReusedOnlyForTheExactSamePayload() {
        val retry = selectPendingMutationIdentity(
            existingId = "mutation-1",
            existingPayloadSha256 = "hash-a",
            payloadSha256 = "hash-a",
            newId = { "mutation-2" },
        )
        val changed = selectPendingMutationIdentity(
            existingId = "mutation-1",
            existingPayloadSha256 = "hash-a",
            payloadSha256 = "hash-b",
            newId = { "mutation-2" },
        )

        assertEquals("mutation-1", retry.id)
        assertEquals("mutation-2", changed.id)
        assertEquals("hash-b", changed.payloadSha256)
    }
}
