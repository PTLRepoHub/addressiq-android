package com.addressiq.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.addressiq.android.theme.LocalAddressIQTheme
import com.addressiq.android.ui.AddressDraft
import com.addressiq.android.ui.AddressIQVerifyInput
import com.addressiq.android.ui.components.AddressIQButton
import com.addressiq.android.ui.components.AddressIQButtonVariant
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

private enum class Stage { Loading, Permission, Address, Streetview, Details, Consent, Submitting, Success, Error }

/**
 * State machine + backend wiring for the verify flow. Holds the
 * widget-session token, drives screen transitions, and surfaces typed
 * results back to [com.addressiq.android.ui.AddressIQVerifyActivity].
 */
@Composable
fun AddressIQVerifyOrchestrator(
    input: AddressIQVerifyInput,
    onCompleted: (locationCode: String, formattedAddress: String?) -> Unit,
    onCancelled: () -> Unit,
    onFailed: (code: String, message: String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val http = remember {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }
    val apiBase = input.environment.defaultApiUrl()

    var stage by rememberSaveable { mutableStateOf(Stage.Loading) }
    var sessionToken by rememberSaveable { mutableStateOf<String?>(null) }
    var address by rememberSaveable { mutableStateOf(AddressDraft()) }
    var submitting by remember { mutableStateOf(false) }
    // (locationCode, formattedAddress) — collect-only; no verification yet.
    var result by rememberSaveable { mutableStateOf<Pair<String, String?>?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    suspend fun createSession() {
        try {
            val body = JSONObject().apply {
                put("appUserId", input.appUserId)
                input.phone?.let { put("phone", it) }
                input.firstName?.let { put("firstName", it) }
                input.lastName?.let { put("lastName", it) }
                input.email?.let { put("email", it) }
            }.toString()

            val req = Request.Builder()
                .url("$apiBase/api/v1/widget/sessions/create")
                .post(body.toRequestBody("application/json".toMediaType()))
                .header("x-api-key", input.apiKey)
                .build()

            val token = withContext(Dispatchers.IO) {
                http.newCall(req).execute().use { resp ->
                    val raw = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        throw RuntimeException("Session create failed (${resp.code}): ${raw.take(200)}")
                    }
                    JSONObject(raw).getString("sessionToken")
                }
            }
            sessionToken = token
            stage = Stage.Permission
        } catch (e: Throwable) {
            errorMessage = e.message ?: "Could not start verification session."
            stage = Stage.Error
        }
    }

    LaunchedEffect(Unit) {
        if (sessionToken == null) {
            stage = Stage.Loading
            createSession()
        }
    }

    suspend fun submit() {
        val token = sessionToken ?: run {
            errorMessage = "No session token"
            stage = Stage.Error
            return
        }
        submitting = true
        try {
            val body = JSONObject().apply {
                address.lat?.let { put("lat", it) }
                address.lon?.let { put("lon", it) }
                put("placeId", address.placeId ?: "sdk_android_manual")
                address.formattedAddress?.let { put("formattedAddress", it) }
                address.propertyNumber?.let { put("propertyNumber", it) }
                address.streetName?.let { put("streetName", it) }
                address.buildingColor?.let { put("buildingColor", it) }
                address.directions?.let { put("directions", it) }
            }.toString()
            // Collect-only endpoint: creates the Location and returns its
            // locationCode. It does NOT start a verification or wire collection —
            // the host owns that via AddressIQ.startVerification(...) from the
            // result callback (contract §collect-verify split).
            val req = Request.Builder()
                .url("$apiBase/api/v1/widget/collect")
                .post(body.toRequestBody("application/json".toMediaType()))
                .header("Authorization", "Bearer $token")
                // The backend rejects state-creating POSTs without an idempotency key.
                .header(
                    "Idempotency-Key",
                    "iqidem_android_widget_collect_${System.currentTimeMillis()}",
                )
                .build()
            val resp = withContext(Dispatchers.IO) {
                http.newCall(req).execute().use { r ->
                    val raw = r.body?.string().orEmpty()
                    if (!r.isSuccessful) throw RuntimeException("Collect failed (${r.code})")
                    JSONObject(raw)
                }
            }
            val locationCode = resp.getString("locationCode")
            val formattedAddress = resp.optString("formattedAddress", null) ?: address.formattedAddress
            result = Pair(locationCode, formattedAddress)
            // No collection wiring here — verification (and its geofence +
            // background collection) is started by the host from the result.
            stage = Stage.Success
        } catch (e: Throwable) {
            errorMessage = e.message ?: "Submission failed"
            stage = Stage.Error
        } finally {
            submitting = false
        }
    }

    @Suppress("MissingPermission")
    suspend fun fetchLocation(): Pair<Double, Double>? = suspendCancellableCoroutine { cont ->
        val client = LocationServices.getFusedLocationProviderClient(context)
        try {
            client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { loc -> cont.resume(loc?.let { it.latitude to it.longitude }) }
                .addOnFailureListener { cont.resume(null) }
        } catch (_: SecurityException) {
            cont.resume(null)
        }
    }

    // Step indicator (§6.6): the 4 numbered capture steps 5–8 are
    // address(1/4) → streetview(2/4) → details(3/4) → consent(4/4). Permission
    // (step 4) and loading/submitting/success/error are not numbered → -1.
    val stepIndex = when (stage) {
        Stage.Address -> 0
        Stage.Streetview -> 1
        Stage.Details -> 2
        Stage.Consent -> 3
        else -> -1
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (stepIndex >= 0) {
            StepIndicator(current = stepIndex, total = 4)
        }
        Box(modifier = Modifier.weight(1f)) {
            when (stage) {
        Stage.Loading -> LoadingView()
        Stage.Permission -> LocationPermissionScreen(
            onGranted = { stage = Stage.Address },
            onCancel = onCancelled,
        )
        Stage.Address -> AddressScreen(
            initial = address,
            googleMapsApiKey = input.googleMapsApiKey,
            onNext = { address = it; stage = Stage.Details },
            onStreetView = { address = it; stage = Stage.Streetview },
            onCancel = onCancelled,
            fetchLocation = { fetchLocation() },
        )
        Stage.Streetview -> StreetViewScreen(
            apiKey = input.googleMapsApiKey ?: "",
            lat = address.lat ?: 0.0,
            lon = address.lon ?: 0.0,
            onConfirm = { stage = Stage.Details },
            onBack = { stage = Stage.Address },
            onCancel = onCancelled,
        )
        Stage.Details -> PropertyDetailsScreen(
            initial = address,
            onNext = { address = it; stage = Stage.Consent },
            onBack = { stage = Stage.Address },
            onCancel = onCancelled,
        )
        Stage.Consent -> ConsentScreen(
            address = address,
            submitting = submitting,
            privacyPolicyUrl = input.privacyPolicyUrl,
            termsUrl = input.termsUrl,
            onSubmit = { scope.launch { submit() } },
            onBack = { stage = Stage.Details },
            onCancel = onCancelled,
        )
        Stage.Submitting -> LoadingView(message = "Submitting…")
        Stage.Success -> {
            val r = result
            if (r != null) {
                SuccessScreen(locationCode = r.first, onDone = { onCompleted(r.first, r.second) })
            } else {
                LoadingView()
            }
        }
        Stage.Error -> ErrorView(
            message = errorMessage ?: "Something went wrong",
            onRetry = { scope.launch { createSession() } },
            onCancel = onCancelled,
        )
            }
        }
    }
}

@Composable
private fun LoadingView(message: String = "Setting up…") {
    val theme = LocalAddressIQTheme.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = theme.primary)
            Spacer(modifier = Modifier.height(12.dp))
            Text(message, color = theme.textSecondary, fontSize = 14.sp)
        }
    }
}

@Composable
private fun ErrorView(message: String, onRetry: () -> Unit, onCancel: () -> Unit) {
    val theme = LocalAddressIQTheme.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.size(120.dp))
        Text("Something went wrong", color = theme.error, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))
        Text(message, color = theme.text, fontSize = 15.sp)
        Spacer(modifier = Modifier.height(20.dp))
        AddressIQButton(text = "Try again", onClick = onRetry)
        Spacer(modifier = Modifier.height(10.dp))
        AddressIQButton(text = "Close", onClick = onCancel, variant = AddressIQButtonVariant.Outline)
    }
}

/**
 * Slim progress indicator shown atop the Collect UI multi-step flow (P1-2).
 * A dot per step (the active dot widens) plus a "Step X of N" label, themed
 * via [LocalAddressIQTheme]. Mirrors the React Native `<IQLocationManager>`,
 * Flutter `AddressIQVerify`, and iOS `AddressIQVerifyView` indicators.
 */
@Composable
private fun StepIndicator(current: Int, total: Int) {
    val theme = LocalAddressIQTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            for (i in 0 until total) {
                Box(
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .height(8.dp)
                        .width(if (i == current) 20.dp else 8.dp)
                        .background(
                            color = if (i <= current) theme.primary else theme.textSecondary.copy(alpha = 0.25f),
                            shape = RoundedCornerShape(4.dp),
                        ),
                )
            }
        }
        Text(
            "Step ${current + 1} of $total",
            color = theme.textSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
