package com.addressiq.android.geofence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.addressiq.android.work.TelemetrySyncWorker
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent

/**
 * Receives geofence transition broadcasts from Play Services. On any
 * transition we enqueue a one-shot WorkManager sync job — the actual
 * event upload happens off the broadcast receiver's strict execution
 * budget.
 */
public class GeofenceTransitionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) return
        val transition = event.geofenceTransition
        if (transition != Geofence.GEOFENCE_TRANSITION_ENTER &&
            transition != Geofence.GEOFENCE_TRANSITION_EXIT &&
            transition != Geofence.GEOFENCE_TRANSITION_DWELL
        ) {
            return
        }
        WorkManager.getInstance(context).enqueue(
            OneTimeWorkRequestBuilder<TelemetrySyncWorker>().build()
        )
    }
}
