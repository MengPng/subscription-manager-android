package com.netkaize.subscription.data

/**
 * Produces a lossless and retry-stable local candidate when both the native cache and the staged
 * legacy account contain edits. Native rows keep their ids; colliding legacy rows get a stable
 * suffix, and a retry never inserts the same logical row twice.
 */
internal fun mergeLegacySubscriptions(
    nativeSubscriptions: List<Subscription>,
    legacySubscriptions: List<Subscription>,
): List<Subscription> {
    val merged = nativeSubscriptions.toMutableList()
    legacySubscriptions.forEach { incoming ->
        if (merged.any { existing -> existing == incoming }) return@forEach
        if (merged.none { it.id == incoming.id }) {
            merged += incoming
            return@forEach
        }
        val suffix = JsonCodec.subscriptionsSha256(listOf(incoming)).take(10)
        val baseId = "${incoming.id}-legacy-$suffix"
        var candidateId = baseId
        var collision = 2
        while (true) {
            val existing = merged.firstOrNull { it.id == candidateId }
            if (existing == null) {
                merged += incoming.copy(id = candidateId)
                break
            }
            if (existing.copy(id = incoming.id) == incoming) break
            candidateId = "$baseId-$collision"
            collision += 1
        }
    }
    return merged
}
