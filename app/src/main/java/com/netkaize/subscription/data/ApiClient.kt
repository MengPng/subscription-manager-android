package com.netkaize.subscription.data

import com.netkaize.subscription.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class ApiException(
    message: String,
    val statusCode: Int,
    val code: String = "",
    val payload: JSONObject = JSONObject(),
) : Exception(message)

data class RemoteSubscriptions(
    val subscriptions: List<Subscription>,
    val revision: Int,
    val updatedAt: String?,
)

data class SubscriptionWriteResult(
    val revision: Int,
    val updatedAt: String?,
)

class ApiClient(private val baseUrl: String = BuildConfig.API_URL) {
    fun login(email: String, password: String): Session = request(
        "/api/auth/login",
        "POST",
        body = JSONObject().put("email", email.trim()).put("password", password),
    ).let(JsonCodec::sessionFromJson)

    fun register(email: String, password: String, code: String, displayName: String): Session = request(
        "/api/auth/register",
        "POST",
        body = JSONObject().put("email", email.trim()).put("password", password).put("code", code.trim()).put("displayName", displayName.trim()),
    ).let(JsonCodec::sessionFromJson)

    fun resetPassword(email: String, password: String, code: String): Session = request(
        "/api/auth/reset-password",
        "POST",
        body = JSONObject().put("email", email.trim()).put("password", password).put("code", code.trim()),
    ).let(JsonCodec::sessionFromJson)

    fun sendCode(email: String, purpose: String): String = request(
        "/api/auth/send-code",
        "POST",
        body = JSONObject().put("email", email.trim()).put("purpose", purpose),
    ).optString("message", "验证码已发送")

    fun me(token: String): AccountUser = JsonCodec.userFromJson(request("/api/auth/me", token = token).getJSONObject("user"))

    fun logout(token: String) {
        request("/api/auth/logout", "POST", token)
    }

    fun templates(token: String? = null): List<ServiceTemplate> {
        val values = request("/api/templates", token = token).optJSONArray("templates") ?: JSONArray()
        return JsonCodec.templatesFromJson(values)
    }

    fun subscriptions(token: String): RemoteSubscriptions {
        val value = request("/api/subscriptions", token = token)
        return remoteSubscriptions(value)
    }

    fun remoteSubscriptions(value: JSONObject): RemoteSubscriptions {
        requiredSchemaVersion(value)
        return RemoteSubscriptions(
            subscriptions = JsonCodec.subscriptionsFromJsonStrict(
                value.opt("subscriptions") as? JSONArray
                    ?: throw IllegalArgumentException("云端订阅响应缺少有效数组"),
            ),
            revision = requiredRevision(value),
            updatedAt = value.optString("updatedAt").takeIf { it.isNotBlank() && it != "null" },
        )
    }

    fun putSubscriptions(
        token: String,
        subscriptions: List<Subscription>,
        baseRevision: Int,
        clientMutationId: String,
    ): SubscriptionWriteResult {
        val body = JSONObject()
            .put("subscriptions", JsonCodec.subscriptionsToJson(subscriptions))
            .put("schemaVersion", 1)
            .put("baseRevision", baseRevision)
            .put("clientMutationId", clientMutationId)
        val value = request("/api/subscriptions", "PUT", token, body)
        requiredSchemaVersion(value)
        require(value.optString("clientMutationId") == clientMutationId) { "同步回执标识不匹配" }
        return SubscriptionWriteResult(
            revision = requiredRevision(value),
            updatedAt = value.optString("updatedAt").takeIf { it.isNotBlank() && it != "null" },
        )
    }

    fun currencyRates(token: String, force: Boolean = false): CurrencyRates = JsonCodec.ratesFromJson(
        request("/api/currency/rates${if (force) "?refresh=1" else ""}", token = token),
    )

    fun updateProfile(token: String, displayName: String): AccountUser = JsonCodec.userFromJson(
        request("/api/profile", "PATCH", token, JSONObject().put("displayName", displayName.trim())).getJSONObject("user"),
    )

    fun updateCurrency(token: String, currencyCode: String): AccountUser = JsonCodec.userFromJson(
        request("/api/profile/currency", "PATCH", token, JSONObject().put("currencyCode", currencyCode)).getJSONObject("user"),
    )

    private fun request(
        path: String,
        method: String = "GET",
        token: String? = null,
        body: JSONObject? = null,
    ): JSONObject {
        val connection = URL("$baseUrl$path").openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = method
            connection.connectTimeout = 8_000
            connection.readTimeout = 12_000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "DingYueNative/${BuildConfig.VERSION_NAME}")
            token?.takeIf(String::isNotBlank)?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val raw = stream?.use { input -> BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readText() }.orEmpty()
            val value = raw.takeIf(String::isNotBlank)?.let(::JSONObject) ?: JSONObject()
            if (status !in 200..299) {
                throw ApiException(
                    message = value.optString("message", "请求未完成"),
                    statusCode = status,
                    code = value.optString("code"),
                    payload = value,
                )
            }
            value
        } finally {
            connection.disconnect()
        }
    }

    private fun requiredRevision(value: JSONObject): Int {
        require(value.has("revision") && !value.isNull("revision")) { "云端响应缺少同步版本" }
        val raw = value.get("revision")
        require(raw is Number) { "云端同步版本格式无效" }
        val number = raw.toDouble()
        require(number.isFinite() && number >= 0.0 && number % 1.0 == 0.0 && number <= Int.MAX_VALUE) {
            "云端同步版本无效"
        }
        return number.toInt()
    }

    private fun requiredSchemaVersion(value: JSONObject): Int {
        require(value.has("schemaVersion") && !value.isNull("schemaVersion")) { "云端响应缺少数据版本" }
        val raw = value.get("schemaVersion")
        require(raw is Number) { "云端数据版本格式无效" }
        val number = raw.toDouble()
        require(number.isFinite() && number % 1.0 == 0.0 && number.toInt() == SUBSCRIPTION_SCHEMA_VERSION) {
            "暂不支持云端订阅数据版本"
        }
        return number.toInt()
    }

    private companion object {
        const val SUBSCRIPTION_SCHEMA_VERSION = 1
    }
}
