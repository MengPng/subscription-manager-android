package com.netkaize.subscription.data

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class SyncWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val repository = SubscriptionRepository.get(applicationContext)
        if (repository.snapshot.session == null || repository.snapshot.syncFrequency == SyncFrequency.OFF) {
            return Result.success()
        }
        if (repository.snapshot.pendingConflict || repository.snapshot.recoveryRequired) {
            // Conflict decisions are user actions. Background work must not upload either side
            // while the user has not yet chosen which version to retain.
            return Result.success()
        }
        return try {
            if (repository.snapshot.dirty) {
                // Retry the durable mutation id before doing a read. If the previous PUT reached
                // the server but its response was lost, this receives the idempotent acknowledgement
                // instead of misclassifying our own revision as another-device conflict.
                repository.sync()
            } else {
                // Periodic work is also a cloud pull. A clean device must still observe edits made
                // on another device instead of waiting until its next local mutation.
                repository.refresh()
            }
            Result.success()
        } catch (_: SyncConflictException) {
            // Keep the dirty local copy and the two safety snapshots. The foreground UI will ask
            // the user which version to retain; background work must never choose silently.
            Result.success()
        } catch (error: ApiException) {
            when (error.statusCode) {
                401, 403 -> Result.failure()
                in 400..499 -> Result.failure()
                else -> Result.retry()
            }
        } catch (_: Throwable) {
            Result.retry()
        }
    }
}

object SyncScheduler {
    private const val REALTIME_WORK = "subscription-realtime-sync"
    private const val PERIODIC_WORK = "subscription-periodic-sync"

    private val networkConstraint = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun apply(context: Context, frequency: SyncFrequency, localMutation: Boolean = false) {
        val manager = WorkManager.getInstance(context)
        when (frequency) {
            SyncFrequency.OFF -> {
                manager.cancelUniqueWork(REALTIME_WORK)
                manager.cancelUniqueWork(PERIODIC_WORK)
            }
            SyncFrequency.REALTIME -> {
                manager.cancelUniqueWork(PERIODIC_WORK)
                if (localMutation) enqueueRealtime(manager)
            }
            SyncFrequency.HOURS_24 -> {
                manager.cancelUniqueWork(REALTIME_WORK)
                enqueuePeriodic(manager, 24)
            }
            SyncFrequency.HOURS_72 -> {
                manager.cancelUniqueWork(REALTIME_WORK)
                enqueuePeriodic(manager, 72)
            }
        }
    }

    private fun enqueueRealtime(manager: WorkManager) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(networkConstraint)
            .setInitialDelay(2, TimeUnit.SECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .build()
        manager.enqueueUniqueWork(REALTIME_WORK, ExistingWorkPolicy.REPLACE, request)
    }

    private fun enqueuePeriodic(manager: WorkManager, hours: Long) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(hours, TimeUnit.HOURS)
            .setConstraints(networkConstraint)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .build()
        manager.enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}
