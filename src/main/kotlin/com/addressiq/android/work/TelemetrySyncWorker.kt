package com.addressiq.android.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.addressiq.android.AddressIQ

/**
 * Background sync worker. Enqueued from
 *  - geofence transitions (immediate)
 *  - periodic schedule registered in `AddressIQ.initialize` (default every
 *    15 minutes when WorkManager constraints are met)
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
            AddressIQ.sync()
            Result.success()
        } catch (_: Throwable) {
            Result.retry()
        }
}
