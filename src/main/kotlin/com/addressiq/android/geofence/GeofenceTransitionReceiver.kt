package com.addressiq.android.geofence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.addressiq.android.AddressIQ
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
        val eventType = when (event.geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> "GEOFENCE_ENTER"
            Geofence.GEOFENCE_TRANSITION_EXIT -> "GEOFENCE_EXIT"
            Geofence.GEOFENCE_TRANSITION_DWELL -> "DWELL"
            else -> return
        }

        // Producer: serialize each triggering geofence into a transit event and
        // persist it to the SQLCipher queue BEFORE scheduling the drain. The
        // geofence `requestId` is the verification/location code the fence was
        // registered under (see AddressIQGeofenceController.register).
        val location = event.triggeringLocation
        event.triggeringGeofences.orEmpty().forEach { fence ->
            AddressIQ.enqueueTransitEvent(
                context = context,
                locationCode = fence.requestId,
                eventType = eventType,
                lat = location?.latitude,
                lon = location?.longitude,
                accuracyM = location?.accuracy?.toDouble(),
            )
        }

        WorkManager.getInstance(context).enqueue(
            OneTimeWorkRequestBuilder<TelemetrySyncWorker>().build()
        )
    }
}
