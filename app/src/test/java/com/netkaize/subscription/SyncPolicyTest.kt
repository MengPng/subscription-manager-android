package com.netkaize.subscription

import com.netkaize.subscription.data.RemoteRefreshDecision
import com.netkaize.subscription.data.remoteRefreshDecision
import com.netkaize.subscription.data.shouldAttemptSync
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncPolicyTest {
    @Test
    fun olderRefreshCannotRollBackSuccessfulSync() {
        assertEquals(
            RemoteRefreshDecision.IGNORE_STALE,
            remoteRefreshDecision(8, false, "new", 7, "old"),
        )
    }

    @Test
    fun cleanEqualRevisionWithDifferentPayloadRequiresUserChoice() {
        assertEquals(
            RemoteRefreshDecision.CONFLICT_EQUAL_REVISION,
            remoteRefreshDecision(8, false, "local", 8, "remote"),
        )
    }

    @Test
    fun dirtyLocalPayloadMayDifferAtItsBaseRevision() {
        assertEquals(
            RemoteRefreshDecision.APPLY,
            remoteRefreshDecision(8, true, "local-edit", 8, "last-cloud"),
        )
    }

    @Test
    fun choosingLocalForCleanConflictStillUploadsTheCandidate() {
        assertEquals(true, shouldAttemptSync(false, true, true, false))
        assertEquals(false, shouldAttemptSync(false, false, true, false))
    }
}
