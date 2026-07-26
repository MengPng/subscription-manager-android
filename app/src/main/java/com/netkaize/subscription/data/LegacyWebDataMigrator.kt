package com.netkaize.subscription.data

import android.annotation.SuppressLint
import android.app.Activity
import android.webkit.WebView
import android.webkit.WebViewClient
import com.netkaize.subscription.BuildConfig
import org.json.JSONObject
import org.json.JSONTokener
import java.security.MessageDigest

/**
 * One-time bridge for V1 upgrades. It reads the existing WebView localStorage for the production
 * origin without rendering the legacy app. New installs complete immediately with no data.
 */
class LegacyWebDataMigrator(
    private val activity: Activity,
    private val repository: SubscriptionRepository,
) {
    @SuppressLint("SetJavaScriptEnabled")
    fun run(onComplete: (LegacyCaptureResult) -> Unit) {
        if (repository.hasCompletedLegacyMigration()) return onComplete(LegacyCaptureResult.Empty)
        val webView = WebView(activity)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                view.evaluateJavascript(
                    """
                    JSON.stringify({
                      token: localStorage.getItem('subscription_manager_auth_v1') || '',
                      userCache: localStorage.getItem('subscription_manager_auth_user_cache_v1') || '{}',
                      accountCache: localStorage.getItem('subscription_manager_account_cache_v1') || '{}',
                      syncPreferences: localStorage.getItem('subscription_manager_sync_preferences_v1') || '{}',
                      subscriptions: localStorage.getItem('subscription_manager_v2') || '[]'
                    })
                    """.trimIndent(),
                ) { encoded ->
                    val result = runCatching { migrate(encoded) }
                        .getOrElse { LegacyCaptureResult.Failed(it.message ?: "旧版数据读取失败") }
                    view.destroy()
                    onComplete(result)
                }
            }
        }
        webView.loadDataWithBaseURL(
            BuildConfig.APP_URL,
            "<!doctype html><html><head><meta charset=\"utf-8\"></head><body></body></html>",
            "text/html",
            "UTF-8",
            null,
        )
    }

    private fun migrate(encoded: String): LegacyCaptureResult {
        val raw = JSONTokener(encoded).nextValue() as? String
            ?: return LegacyCaptureResult.Failed("旧版数据格式无法识别")
        val snapshot = JSONObject(raw)
        val snapshotErrors = mutableListOf<String>()
        val unresolvedEntries = mutableListOf<String>()
        val token = snapshot.optString("token").takeIf { it.isNotBlank() }
        val userCache = runCatching { JSONObject(snapshot.optString("userCache", "{}")) }.getOrElse {
            snapshotErrors += "userCache: ${it.message ?: "格式无效"}"
            JSONObject()
        }
        val user = userCache.optJSONObject("user")
            ?.takeIf { token != null && userCache.optString("token") == token }
            ?.let(JsonCodec::userFromJson)
            ?.takeIf { it.id.isNotBlank() }
        val accountCache = runCatching { JSONObject(snapshot.optString("accountCache", "{}")) }.getOrElse {
            val message = "accountCache: ${it.message ?: "格式无效"}"
            snapshotErrors += message
            unresolvedEntries += message
            JSONObject()
        }
        val preferences = runCatching { JSONObject(snapshot.optString("syncPreferences", "{}")) }.getOrElse {
            snapshotErrors += "syncPreferences: ${it.message ?: "格式无效"}"
            JSONObject()
        }
        val fallbackRaw = snapshot.optString("subscriptions", "[]")
        val fallbackDecoded = runCatching { decodeLegacySubscriptions(org.json.JSONArray(fallbackRaw), "subscriptions") }
            .getOrElse {
                val message = "subscriptions: ${it.message ?: "格式无效"}"
                snapshotErrors += message
                unresolvedEntries += message
                val detail = it.message ?: "全局订阅格式无效"
                DecodedLegacy(emptyList(), listOf(detail), listOf(detail))
            }
        val fallback = fallbackDecoded.subscriptions
        snapshotErrors += fallbackDecoded.errors
        unresolvedEntries += fallbackDecoded.unresolvedErrors
        val accounts = buildList {
            for (userId in accountCache.keys()) {
                val account = accountCache.optJSONObject(userId)
                if (account == null) {
                    val message = "accountCache.$userId: 账户记录不是对象"
                    snapshotErrors += message
                    unresolvedEntries += message
                    continue
                }
                val values = account.optJSONArray("subscriptions")
                if (values == null) {
                    val message = "accountCache.$userId: 缺少订阅数组"
                    snapshotErrors += message
                    unresolvedEntries += message
                    continue
                }
                val decoded = decodeLegacySubscriptions(values, "accountCache.$userId")
                snapshotErrors += decoded.errors
                unresolvedEntries += decoded.unresolvedErrors
                add(
                    LegacyAccountPayload(
                        userId = userId,
                        subscriptions = decoded.subscriptions,
                        dirty = account.optBoolean("dirty", false),
                        syncFrequency = SyncFrequency.fromWire(preferences.optString(userId)),
                        rawSubscriptionsJson = values.toString(),
                        decodeErrors = decoded.errors,
                    ),
                )
            }
            if (user != null && none { it.userId == user.id } && fallback.isNotEmpty()) {
                add(
                    LegacyAccountPayload(
                        userId = user.id,
                        subscriptions = fallback,
                        // The global V1 store had no reliable server revision. Keep it as an
                        // explicit local change so it can never be discarded silently.
                        dirty = true,
                        syncFrequency = SyncFrequency.fromWire(preferences.optString(user.id)),
                        rawSubscriptionsJson = fallbackRaw,
                        decodeErrors = fallbackDecoded.errors,
                    ),
                )
            }
            if (user == null && fallback.isNotEmpty()) {
                add(
                    LegacyAccountPayload(
                        userId = LEGACY_UNASSIGNED_USER_ID,
                        subscriptions = fallback,
                        dirty = true,
                        syncFrequency = SyncFrequency.OFF,
                        rawSubscriptionsJson = fallbackRaw,
                        decodeErrors = fallbackDecoded.errors,
                    ),
                )
            }
        }
        if (token == null && user == null && accounts.isEmpty() && fallback.isEmpty() && unresolvedEntries.isEmpty()) {
            return LegacyCaptureResult.Empty
        }
        return LegacyCaptureResult.Ready(LegacyPayload(
            token = token,
            user = user,
            accounts = accounts,
            snapshotSha256 = MessageDigest.getInstance("SHA-256")
                .digest(raw.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) },
            rawSnapshotJson = raw,
            decodeErrors = snapshotErrors.distinct(),
            unresolvedEntries = unresolvedEntries.distinct(),
        ))
    }
}

internal fun decodeLegacySubscriptions(values: org.json.JSONArray, source: String): DecodedLegacy {
    val errors = mutableListOf<String>()
    val unresolvedErrors = mutableListOf<String>()
    val decoded = buildList {
        val usedIds = mutableSetOf<String>()
        for (index in 0 until values.length()) {
            val item = values.optJSONObject(index)
            if (item == null) {
                val message = "$source[$index]: 记录不是对象"
                errors += message
                unresolvedErrors += message
                continue
            }
            val hasReliableDate = listOf("startDate", "createdAt", "nextDate").any { field ->
                item.optString(field).take(10).matches(Regex("\\d{4}-\\d{2}-\\d{2}")) &&
                    runCatching { java.time.LocalDate.parse(item.optString(field).take(10)) }.isSuccess
            }
            if (!hasReliableDate) {
                val message = "$source[$index]: 缺少可确认的首次订阅日期"
                errors += message
                unresolvedErrors += message
                continue
            }
            val value = runCatching { JsonCodec.subscriptionFromJsonStrict(item) }.getOrElse { error ->
                errors += "$source[$index]: ${error.message ?: "已按旧版规则修复"}"
                JsonCodec.subscriptionFromJson(item)
            }
            if (usedIds.add(value.id)) {
                add(value)
            } else {
                errors += "$source[$index]: 重复 ID ${value.id} 已重命名"
                var suffix = index.coerceAtLeast(2)
                var repairedId: String
                do {
                    repairedId = "${value.id}-legacy-$suffix"
                    suffix += 1
                } while (!usedIds.add(repairedId))
                add(value.copy(id = repairedId))
            }
        }
    }
    return DecodedLegacy(decoded, errors, unresolvedErrors)
}

internal data class DecodedLegacy(
    val subscriptions: List<Subscription>,
    val errors: List<String>,
    val unresolvedErrors: List<String>,
)
