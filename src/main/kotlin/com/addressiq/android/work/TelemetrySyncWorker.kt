package com.addressiq.android.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.addressiq.android.AddressIQ

/**
 * Background sync worker. Enqueued from
 *  - geofence transitions (immediate)
 *  - a periodic schedule started when a verification begins collecting
 *    (every 15 minutes, WorkManager's floor, subject to Doze batching)
 *
 * The worker drains the SQLCipher telemetry queue in batches and lets the
 * AddressIQ object coordinate which verifications are still active.
 */
public class TelemetrySyncWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result =
        try {
            // Record before draining so the reading ships in the same run.
            AddressIQ.recordBackgroundCheck(applicationContext)
            AddressIQ.sync(applicationContext)
            AddressIQ.stopPeriodicSyncIfIdle(applicationContext)
            Result.success()
        } catch (_: Throwable) {
            Result.retry()
        }
}
