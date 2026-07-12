package com.addressiq.android.geofence

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices

/**
 * Wraps Google Play Services `GeofencingClient`. The SDK registers a single
 * geofence per active verification at the adaptive radius the backend
 * returned for the location (see Phase 3 §adaptive geofencing).
 *
 * On geofence transitions, Play Services delivers a broadcast to
 * `GeofenceTransitionReceiver` which enqueues a WorkManager job to flush
 * telemetry. Foreground services are NOT used by default — partners opt in
 * via `AddressIQConfig.foregroundServiceNotification(...)`.
 */
public class AddressIQGeofenceController(private val context: Context) {
    private val client: GeofencingClient = LocationServices.getGeofencingClient(context)

    @SuppressLint("MissingPermission")
    public fun register(
        identifier: String,
        lat: Double,
        lon: Double,
        radiusMeters: Float,
    ) {
        val fence = Geofence.Builder()
            .setRequestId(identifier)
            .setCircularRegion(lat, lon, radiusMeters)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(
                Geofence.GEOFENCE_TRANSITION_ENTER or
                    Geofence.GEOFENCE_TRANSITION_EXIT or
                    Geofence.GEOFENCE_TRANSITION_DWELL
            )
            .setLoiteringDelay(60_000)
            .build()

        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofence(fence)
            .build()

        client.addGeofences(request, pendingIntent())
    }

    @SuppressLint("MissingPermission")
    public fun unregister(identifier: String) {
        client.removeGeofences(listOf(identifier))
    }

    public fun unregisterAll() {
        client.removeGeofences(pendingIntent())
    }

    /**
     * Required runtime permissions:
     *  - [Manifest.permission.ACCESS_FINE_LOCATION] (foreground)
     *  - [Manifest.permission.ACCESS_BACKGROUND_LOCATION] (Android 10+ when monitoring outside the app)
     */
    public companion object {
        public val REQUIRED_PERMISSIONS: Array<String> = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            // Literal rather than Manifest.permission.ACCESS_BACKGROUND_LOCATION
            // (a field added in API 29) — the constant is compile-time-inlined
            // to this exact string anyway, and the literal avoids a NewApi lint
            // error on minSdk 24. Harmlessly ignored by the OS below API 29.
            "android.permission.ACCESS_BACKGROUND_LOCATION",
        )
    }

    private fun pendingIntent(): PendingIntent {
        val intent = Intent(context, GeofenceTransitionReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            42,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
    }
}
