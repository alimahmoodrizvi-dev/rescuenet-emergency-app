package com.rescuenet.app.data.offline

import android.content.Context
import androidx.work.*
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules SyncWorker. Deliberately battery-aware (Part 30): a network-constrained one-shot
 * request fires as soon as connectivity returns instead of polling, plus a lightweight
 * periodic backstop in case a network callback is missed by the OS.
 */
@Singleton
class OfflineQueueManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun scheduleSyncAsSoonAsOnline() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val oneShot = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "rescuenet_sync_now",
            ExistingWorkPolicy.KEEP,
            oneShot,
        )

        val periodic = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "rescuenet_sync_periodic",
            ExistingPeriodicWorkPolicy.KEEP,
            periodic,
        )
    }

    /**
     * Demo Mode (Part 16) hook: forces an immediate sync attempt instead of waiting for
     * WorkManager's normal scheduling latency, so a judge presentation doesn't sit idle for
     * a noticeable moment after the "internet returns" step. Uses REPLACE rather than KEEP
     * so it actually preempts a still-pending backoff retry — this is a real sync attempt,
     * not a fake progress animation; it still genuinely requires network connectivity and
     * will genuinely fail/retry like any other run of SyncWorker if the backend is
     * unreachable.
     */
    fun runSyncNow() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val immediate = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "rescuenet_sync_now",
            ExistingWorkPolicy.REPLACE,
            immediate,
        )
    }
}
