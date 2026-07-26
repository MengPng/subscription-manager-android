package com.netkaize.subscription.data

internal enum class RemoteRefreshDecision {
    APPLY,
    IGNORE_STALE,
    CONFLICT_EQUAL_REVISION,
}

/**
 * Keeps cloud revisions monotonic while still allowing a dirty local payload to differ from its
 * last synced revision. Equal clean revisions must describe the same payload; otherwise neither
 * side is silently chosen.
 */
internal fun remoteRefreshDecision(
    currentRevision: Int,
    currentDirty: Boolean,
    currentPayloadSha256: String,
    remoteRevision: Int,
    remotePayloadSha256: String,
): RemoteRefreshDecision = when {
    remoteRevision < currentRevision -> RemoteRefreshDecision.IGNORE_STALE
    !currentDirty && remoteRevision == currentRevision && remotePayloadSha256 != currentPayloadSha256 ->
        RemoteRefreshDecision.CONFLICT_EQUAL_REVISION
    else -> RemoteRefreshDecision.APPLY
}

internal fun shouldAttemptSync(
    dirty: Boolean,
    forceLocal: Boolean,
    pendingConflict: Boolean,
    recoveryRequired: Boolean,
): Boolean = dirty || (forceLocal && (pendingConflict || recoveryRequired))
