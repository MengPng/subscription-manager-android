package com.netkaize.subscription.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.security.MessageDigest

object JsonCodec {
    private fun JSONObject.stringOrEmpty(name: String): String = optString(name, "").trim()
    private fun date(value: String?): LocalDate? = try {
        value?.takeIf { it.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) }?.let(LocalDate::parse)
    } catch (_: DateTimeParseException) {
        null
    }

    fun userFromJson(value: JSONObject): AccountUser = AccountUser(
        id = value.stringOrEmpty("id"),
        email = value.stringOrEmpty("email"),
        displayName = value.stringOrEmpty("displayName").ifEmpty { "订阅用户" },
        createdAt = value.stringOrEmpty("createdAt"),
        isAdmin = value.optBoolean("isAdmin", false),
        currencyCode = value.stringOrEmpty("currencyCode").ifEmpty { "CNY" },
    )

    fun userToJson(value: AccountUser): JSONObject = JSONObject()
        .put("id", value.id)
        .put("email", value.email)
        .put("displayName", value.displayName)
        .put("createdAt", value.createdAt)
        .put("isAdmin", value.isAdmin)
        .put("currencyCode", value.currencyCode)

    fun sessionFromJson(value: JSONObject): Session = Session(
        token = value.stringOrEmpty("token"),
        user = userFromJson(value.getJSONObject("user")),
    )

    fun sessionToJson(value: Session): JSONObject = JSONObject()
        .put("token", value.token)
        .put("user", userToJson(value.user))

    fun subscriptionFromJson(value: JSONObject): Subscription {
        val cycle = BillingCycle.fromWire(value.optString("cycle"))
        val start = date(value.optString("startDate"))
            ?: date(value.optString("createdAt").take(10))
            ?: date(value.optString("nextDate"))
            ?: LocalDate.now()
        val defaultNext = when (cycle) {
            BillingCycle.MONTHLY -> start.plusMonths(1)
            BillingCycle.YEARLY -> start.plusYears(1)
            BillingCycle.ONCE -> null
        }
        val savedNext = date(value.optString("nextDate"))?.takeIf { it > start } ?: defaultNext
        val pauseValues = value.optJSONArray("pauses") ?: JSONArray()
        val pauses = buildList {
            for (index in 0 until pauseValues.length()) {
                val item = pauseValues.optJSONObject(index) ?: continue
                val pauseStart = date(item.optString("startDate")) ?: continue
                add(PausePeriod(pauseStart, date(item.optString("endDate"))))
            }
        }
        val name = value.stringOrEmpty("name").ifEmpty { "未命名服务" }
        val status = SubscriptionStatus.fromWire(value.optString("status"))
        val scheduledCancelDate = date(value.optString("scheduledCancelDate"))
        val canceledAt = date(value.optString("canceledAt")) ?: if (status == SubscriptionStatus.CANCELED) {
            // Older Web records did not always persist canceledAt. Use a deterministic boundary
            // rather than LocalDate.now(), otherwise the same cloud document changes its lifetime
            // spend every time it is decoded on a later day.
            scheduledCancelDate
                ?: date(value.optString("updatedAt").take(10))
                ?: savedNext
                ?: start.plusDays(1)
        } else {
            null
        }
        val knownKeys = setOf(
            "id", "name", "category", "note", "price", "cycle", "startDate", "createdAt", "nextDate",
            "renewalAnchorDate", "status", "usageCount", "officialUrl", "manageUrl", "lastReviewedAt",
            "canceledAt", "scheduledCancelDate", "pauses", "icon", "iconKey", "image", "color",
        )
        val extras = JSONObject()
        for (key in value.keys()) if (key !in knownKeys) extras.put(key, value.opt(key))
        return Subscription(
            id = value.stringOrEmpty("id").ifEmpty { "legacy-${textSha256(value.toString()).take(24)}" },
            name = name,
            category = value.stringOrEmpty("category").ifEmpty { "其他" },
            note = value.stringOrEmpty("note"),
            priceCny = value.optDouble("price", 0.0).takeIf { it.isFinite() && it >= 0 } ?: 0.0,
            cycle = cycle,
            startDate = start,
            nextDate = savedNext,
            renewalAnchorDate = date(value.optString("renewalAnchorDate")) ?: if (savedNext == defaultNext) start else savedNext,
            status = status,
            usageCount = value.optInt("usageCount", 0).coerceAtLeast(0),
            officialUrl = value.stringOrEmpty("officialUrl"),
            manageUrl = value.stringOrEmpty("manageUrl"),
            lastReviewedAt = date(value.optString("lastReviewedAt")),
            canceledAt = canceledAt,
            scheduledCancelDate = scheduledCancelDate,
            pauses = pauses,
            icon = value.stringOrEmpty("icon").ifEmpty { name.take(1) },
            iconKey = value.optString("iconKey").takeIf { it.isNotBlank() && it != "null" },
            image = value.stringOrEmpty("image"),
            color = value.stringOrEmpty("color").takeIf { it.matches(Regex("#[0-9A-Fa-f]{6}")) } ?: "#007AFF",
            extrasJson = extras.toString(),
        )
    }

    /**
     * Decodes native/server data without silently repairing it. The tolerant decoder above is
     * intentionally retained for the one-time WebView migration, where preserving recoverable
     * legacy rows is preferable to rejecting the whole snapshot.
     */
    fun subscriptionFromJsonStrict(value: JSONObject): Subscription {
        fun requiredString(name: String): String {
            require(value.has(name) && !value.isNull(name)) { "订阅缺少 $name" }
            val raw = value.get(name)
            require(raw is String && raw.isNotBlank()) { "订阅字段 $name 无效" }
            return raw.trim()
        }

        fun optionalString(name: String) {
            if (value.has(name) && !value.isNull(name)) {
                require(value.get(name) is String) { "订阅字段 $name 类型无效" }
            }
        }

        fun requiredDate(name: String): LocalDate {
            val raw = requiredString(name)
            return date(raw) ?: throw IllegalArgumentException("订阅字段 $name 日期无效")
        }

        fun optionalDate(name: String): LocalDate? {
            if (!value.has(name) || value.isNull(name)) return null
            val raw = value.get(name)
            require(raw is String) { "订阅字段 $name 类型无效" }
            if (raw.isBlank()) return null
            return date(raw) ?: throw IllegalArgumentException("订阅字段 $name 日期无效")
        }

        requiredString("id")
        requiredString("name")
        val cycle = requiredString("cycle")
        require(BillingCycle.entries.any { it.wireValue == cycle }) { "订阅周期无效" }
        val status = requiredString("status")
        require(SubscriptionStatus.entries.any { it.wireValue == status }) { "订阅状态无效" }
        val startDate = requiredDate("startDate")

        require(value.has("price") && !value.isNull("price")) { "订阅缺少 price" }
        val price = value.get("price")
        val numericPrice = when (price) {
            is Number -> price.toDouble()
            is String -> price.trim().takeIf(String::isNotEmpty)?.toDoubleOrNull()
            else -> null
        }
        require(numericPrice != null && numericPrice.isFinite() && numericPrice >= 0.0) { "订阅价格无效" }

        val nextDate = optionalDate("nextDate")
        if (nextDate != null) {
            val valid = if (cycle == BillingCycle.ONCE.wireValue) {
                // The previous Web client stored one-time purchases as nextDate=startDate.
                // Accept that legacy representation; the tolerant mapper below normalizes it
                // to null so the native client never presents it as a future renewal.
                nextDate >= startDate
            } else {
                nextDate > startDate
            }
            require(valid) { "下一次扣费日必须晚于首次订阅日" }
        }
        optionalDate("renewalAnchorDate")
        optionalDate("lastReviewedAt")
        optionalDate("canceledAt")
        optionalDate("scheduledCancelDate")

        listOf("category", "note", "officialUrl", "manageUrl", "icon", "iconKey", "image", "color")
            .forEach(::optionalString)

        if (value.has("usageCount") && !value.isNull("usageCount")) {
            val usageCount = value.get("usageCount")
            val numericUsage = when (usageCount) {
                is Number -> usageCount.toDouble()
                is String -> usageCount.trim().takeIf(String::isNotEmpty)?.toDoubleOrNull()
                else -> null
            }
            require(
                numericUsage != null &&
                    numericUsage.isFinite() &&
                    numericUsage >= 0.0 &&
                    numericUsage % 1.0 == 0.0,
            ) { "使用次数无效" }
        }

        if (value.has("pauses") && !value.isNull("pauses")) {
            val pauses = value.get("pauses")
            require(pauses is JSONArray) { "暂停记录格式无效" }
            for (index in 0 until pauses.length()) {
                val item = pauses.optJSONObject(index)
                    ?: throw IllegalArgumentException("第 ${index + 1} 条暂停记录格式无效")
                val rawStart = item.opt("startDate")
                require(rawStart is String) { "暂停开始日期无效" }
                val pauseStart = date(rawStart) ?: throw IllegalArgumentException("暂停开始日期无效")
                if (item.has("endDate") && !item.isNull("endDate")) {
                    val rawEnd = item.opt("endDate")
                    require(rawEnd is String) { "暂停结束日期无效" }
                    if (rawEnd.isBlank()) continue
                    val pauseEnd = date(rawEnd) ?: throw IllegalArgumentException("暂停结束日期无效")
                    require(pauseEnd >= pauseStart) { "暂停结束日期不能早于开始日期" }
                }
            }
        }
        return subscriptionFromJson(value)
    }

    fun subscriptionToJson(value: Subscription): JSONObject = runCatching { JSONObject(value.extrasJson) }.getOrElse { JSONObject() }
        .put("id", value.id)
        .put("name", value.name)
        .put("category", value.category)
        .put("note", value.note)
        .put("price", value.priceCny)
        .put("cycle", value.cycle.wireValue)
        .put("startDate", value.startDate.toString())
        .put("nextDate", value.nextDate?.toString() ?: JSONObject.NULL)
        .put("renewalAnchorDate", value.renewalAnchorDate?.toString() ?: JSONObject.NULL)
        .put("status", value.status.wireValue)
        .put("usageCount", value.usageCount)
        .put("officialUrl", value.officialUrl)
        .put("manageUrl", value.manageUrl)
        .put("lastReviewedAt", value.lastReviewedAt?.toString() ?: JSONObject.NULL)
        .put("canceledAt", value.canceledAt?.toString() ?: JSONObject.NULL)
        .put("scheduledCancelDate", value.scheduledCancelDate?.toString() ?: JSONObject.NULL)
        .put("pauses", JSONArray(value.pauses.map { JSONObject().put("startDate", it.startDate.toString()).put("endDate", it.endDate?.toString() ?: JSONObject.NULL) }))
        .put("icon", value.icon)
        .put("iconKey", value.iconKey ?: JSONObject.NULL)
        .put("image", value.image)
        .put("color", value.color)

    fun subscriptionsFromJson(value: JSONArray): List<Subscription> = buildList {
        for (index in 0 until value.length()) value.optJSONObject(index)?.let { add(subscriptionFromJson(it)) }
    }

    fun subscriptionsFromJsonStrict(value: JSONArray): List<Subscription> {
        val decoded = buildList {
            for (index in 0 until value.length()) {
                val item = value.optJSONObject(index)
                    ?: throw IllegalArgumentException("第 ${index + 1} 条订阅不是对象")
                add(subscriptionFromJsonStrict(item))
            }
        }
        require(decoded.map { it.id }.toSet().size == decoded.size) { "订阅 ID 重复" }
        return decoded
    }

    fun subscriptionsToJson(values: List<Subscription>): JSONArray = JSONArray(values.map(::subscriptionToJson))

    fun subscriptionsSha256(values: List<Subscription>): String =
        jsonSha256(subscriptionsToJson(values))

    fun jsonSha256(value: Any?): String = sha256(canonicalJson(value))

    fun textSha256(value: String): String = sha256(value)

    fun templateFromJson(value: JSONObject): ServiceTemplate = ServiceTemplate(
        id = value.stringOrEmpty("id"),
        name = value.stringOrEmpty("name"),
        category = value.stringOrEmpty("category").ifEmpty { "其他" },
        priceCny = value.optDouble("price", 0.0),
        cycle = BillingCycle.fromWire(value.optString("cycle")),
        icon = value.stringOrEmpty("icon").ifEmpty { value.stringOrEmpty("name").take(1) },
        color = value.stringOrEmpty("color").ifEmpty { "#007AFF" },
        image = value.stringOrEmpty("image"),
        officialUrl = value.stringOrEmpty("officialUrl"),
        manageUrl = value.stringOrEmpty("manageUrl"),
        description = value.stringOrEmpty("description"),
        isOfficial = value.optBoolean("isOfficial", false),
        isActive = value.optBoolean("isActive", true),
        sortOrder = value.optInt("sortOrder", 0),
    )

    fun templateToJson(value: ServiceTemplate): JSONObject = JSONObject()
        .put("id", value.id)
        .put("name", value.name)
        .put("category", value.category)
        .put("price", value.priceCny)
        .put("cycle", value.cycle.wireValue)
        .put("icon", value.icon)
        .put("color", value.color)
        .put("image", value.image)
        .put("officialUrl", value.officialUrl)
        .put("manageUrl", value.manageUrl)
        .put("description", value.description)
        .put("isOfficial", value.isOfficial)
        .put("isActive", value.isActive)
        .put("sortOrder", value.sortOrder)

    fun templatesFromJson(value: JSONArray): List<ServiceTemplate> = buildList {
        for (index in 0 until value.length()) value.optJSONObject(index)?.let { add(templateFromJson(it)) }
    }

    fun templatesToJson(values: List<ServiceTemplate>): JSONArray = JSONArray(values.map(::templateToJson))

    fun ratesFromJson(value: JSONObject): CurrencyRates {
        val rateObject = value.optJSONObject("rates") ?: JSONObject().put("CNY", 1.0)
        val rates = buildMap {
            for (key in rateObject.keys()) rateObject.optDouble(key).takeIf { it > 0 }?.let { put(key, it) }
            put("CNY", 1.0)
        }
        val currencyArray = value.optJSONArray("currencies") ?: JSONArray()
        val currencies = buildList {
            for (index in 0 until currencyArray.length()) {
                val item = currencyArray.optJSONObject(index) ?: continue
                add(CurrencyInfo(item.stringOrEmpty("code"), item.stringOrEmpty("name"), item.stringOrEmpty("symbol")))
            }
        }
        return CurrencyRates(
            base = value.stringOrEmpty("base").ifEmpty { "CNY" },
            rates = rates,
            currencies = currencies.ifEmpty { listOf(CurrencyInfo("CNY", "人民币", "¥")) },
            source = value.stringOrEmpty("source"),
            updatedAt = value.stringOrEmpty("updatedAt"),
            cached = value.optBoolean("cached", false),
        )
    }

    fun ratesToJson(value: CurrencyRates): JSONObject = JSONObject()
        .put("base", value.base)
        .put("rates", JSONObject(value.rates))
        .put("currencies", JSONArray(value.currencies.map { JSONObject().put("code", it.code).put("name", it.name).put("symbol", it.symbol) }))
        .put("source", value.source)
        .put("updatedAt", value.updatedAt)
        .put("cached", value.cached)

    fun backupToJson(value: BackupEnvelope): String {
        val subscriptions = subscriptionsToJson(value.subscriptions)
        return JSONObject()
            .put("schemaVersion", value.schemaVersion)
            .put("exportedAt", value.exportedAt)
            .put("accountEmail", value.accountEmail)
            .put("baseCurrency", value.baseCurrency)
            .put("billingRulesVersion", value.billingRulesVersion)
            .put("subscriptionsSha256", jsonSha256(subscriptions))
            .put("subscriptions", subscriptions)
            .toString(2)
    }

    fun backupFromJson(raw: String): BackupEnvelope {
        val value = JSONObject(raw)
        val schemaVersion = value.optInt("schemaVersion", 1)
        val subscriptions = value.optJSONArray("subscriptions") ?: throw IllegalArgumentException("备份中没有订阅数据")
        val expectedChecksum = value.stringOrEmpty("subscriptionsSha256")
        if (schemaVersion >= 3) {
            require(expectedChecksum.matches(Regex("[0-9A-Fa-f]{64}"))) { "备份缺少有效的 SHA-256 校验值" }
        }
        if (expectedChecksum.isNotBlank()) {
            val canonicalChecksum = jsonSha256(subscriptions)
            val legacyChecksum = sha256(subscriptions.toString())
            require(
                expectedChecksum.equals(canonicalChecksum, ignoreCase = true) ||
                    expectedChecksum.equals(legacyChecksum, ignoreCase = true),
            ) {
                "备份校验失败，文件可能不完整"
            }
        }
        val decoded = subscriptionsFromJsonStrict(subscriptions)
        return BackupEnvelope(
            schemaVersion = schemaVersion,
            exportedAt = value.stringOrEmpty("exportedAt"),
            accountEmail = value.stringOrEmpty("accountEmail"),
            baseCurrency = value.stringOrEmpty("baseCurrency").ifEmpty { "CNY" },
            billingRulesVersion = value.optInt("billingRulesVersion", 1),
            subscriptions = decoded,
        )
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private fun canonicalJson(value: Any?): String = when (value) {
        null, JSONObject.NULL -> "null"
        is JSONObject -> value.keys().asSequence().toList().sorted().joinToString(",", "{", "}") { key ->
            "${JSONObject.quote(key)}:${canonicalJson(value.get(key))}"
        }
        is JSONArray -> (0 until value.length()).joinToString(",", "[", "]") { index ->
            canonicalJson(value.get(index))
        }
        is String -> JSONObject.quote(value)
        is Number -> {
            val number = value.toDouble()
            require(number.isFinite()) { "JSON 数值无效" }
            JSONObject.numberToString(value)
        }
        is Boolean -> value.toString()
        else -> JSONObject.quote(value.toString())
    }
}
