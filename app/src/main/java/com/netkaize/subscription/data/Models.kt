package com.netkaize.subscription.data

import java.time.LocalDate
import java.util.UUID

const val LEGACY_UNASSIGNED_USER_ID = "__legacy_unassigned__"

enum class BillingCycle(val wireValue: String, val label: String) {
    MONTHLY("monthly", "每月"),
    YEARLY("yearly", "每年"),
    ONCE("once", "一次性");

    companion object {
        fun fromWire(value: String?): BillingCycle = entries.firstOrNull { it.wireValue == value } ?: MONTHLY
    }
}

enum class SubscriptionStatus(val wireValue: String, val label: String) {
    ACTIVE("active", "生效中"),
    PAUSED("paused", "已暂停"),
    CANCELED("canceled", "已取消");

    companion object {
        fun fromWire(value: String?): SubscriptionStatus = entries.firstOrNull { it.wireValue == value } ?: ACTIVE
    }
}

enum class SyncFrequency(val wireValue: String, val label: String) {
    REALTIME("realtime", "实时同步"),
    HOURS_24("24", "每 24 小时"),
    HOURS_72("72", "每 72 小时"),
    OFF("off", "不同步");

    companion object {
        fun fromWire(value: String?): SyncFrequency = entries.firstOrNull { it.wireValue == value } ?: REALTIME
    }
}

data class PausePeriod(
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
)

data class Subscription(
    val id: String = "sub-${UUID.randomUUID()}",
    val name: String,
    val category: String = "其他",
    val note: String = "",
    val priceCny: Double = 0.0,
    val cycle: BillingCycle = BillingCycle.MONTHLY,
    val startDate: LocalDate = LocalDate.now(),
    val nextDate: LocalDate? = null,
    val renewalAnchorDate: LocalDate? = null,
    val status: SubscriptionStatus = SubscriptionStatus.ACTIVE,
    val usageCount: Int = 0,
    val officialUrl: String = "",
    val manageUrl: String = "",
    val lastReviewedAt: LocalDate? = null,
    val canceledAt: LocalDate? = null,
    val scheduledCancelDate: LocalDate? = null,
    val pauses: List<PausePeriod> = emptyList(),
    val icon: String = name.take(1),
    val iconKey: String? = null,
    val image: String = "",
    val color: String = "#007AFF",
    val extrasJson: String = "{}",
)

data class LegacyAccountPayload(
    val userId: String,
    val subscriptions: List<Subscription>,
    val dirty: Boolean,
    val syncFrequency: SyncFrequency,
    val rawSubscriptionsJson: String = "",
    val decodeErrors: List<String> = emptyList(),
)

data class LegacyPayload(
    val token: String?,
    val user: AccountUser?,
    val accounts: List<LegacyAccountPayload>,
    val snapshotSha256: String,
    val rawSnapshotJson: String = "",
    val decodeErrors: List<String> = emptyList(),
    val unresolvedEntries: List<String> = emptyList(),
)

sealed interface LegacyCaptureResult {
    data object Empty : LegacyCaptureResult
    data class Ready(val payload: LegacyPayload) : LegacyCaptureResult
    data class Failed(val reason: String) : LegacyCaptureResult
}

data class ServiceTemplate(
    val id: String,
    val name: String,
    val category: String,
    val priceCny: Double,
    val cycle: BillingCycle,
    val icon: String,
    val color: String,
    val image: String,
    val officialUrl: String,
    val manageUrl: String,
    val description: String,
    val isOfficial: Boolean,
    val isActive: Boolean = true,
    val sortOrder: Int = 0,
)

data class AccountUser(
    val id: String,
    val email: String,
    val displayName: String,
    val createdAt: String,
    val isAdmin: Boolean,
    val currencyCode: String = "CNY",
)

data class Session(
    val token: String,
    val user: AccountUser,
)

data class CurrencyInfo(
    val code: String,
    val name: String,
    val symbol: String = code,
)

data class CurrencyRates(
    val base: String = "CNY",
    val rates: Map<String, Double> = mapOf("CNY" to 1.0),
    val currencies: List<CurrencyInfo> = listOf(CurrencyInfo("CNY", "人民币", "¥")),
    val source: String = "",
    val updatedAt: String = "",
    val cached: Boolean = true,
)

data class CachedAccount(
    val user: AccountUser,
    val subscriptions: List<Subscription>,
    val templates: List<ServiceTemplate>,
    val currencyRates: CurrencyRates,
    val cloudRevision: Int,
    val cloudUpdatedAt: String?,
    val dirty: Boolean,
    val lastSyncedAt: String?,
    val syncFrequency: SyncFrequency,
    val pendingMutationId: String? = null,
    val pendingPayloadSha256: String? = null,
    val pendingConflict: Boolean = false,
    val recoveryRequired: Boolean = false,
    val recoveryReason: String? = null,
    val quarantineId: Long? = null,
)

data class PendingMutationIdentity(
    val id: String,
    val payloadSha256: String,
)

internal fun selectPendingMutationIdentity(
    existingId: String?,
    existingPayloadSha256: String?,
    payloadSha256: String,
    newId: () -> String = { UUID.randomUUID().toString() },
): PendingMutationIdentity {
    val reusableId = existingId?.takeIf(String::isNotBlank)
    return if (reusableId != null && existingPayloadSha256 == payloadSha256) {
        PendingMutationIdentity(reusableId, payloadSha256)
    } else {
        PendingMutationIdentity(newId(), payloadSha256)
    }
}

data class BackupEnvelope(
    val schemaVersion: Int = 3,
    val exportedAt: String,
    val accountEmail: String,
    val baseCurrency: String = "CNY",
    val billingRulesVersion: Int = 1,
    val subscriptions: List<Subscription>,
)
