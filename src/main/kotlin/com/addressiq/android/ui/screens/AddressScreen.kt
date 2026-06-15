package com.addressiq.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.addressiq.android.maps.MapsClient
import com.addressiq.android.maps.PlaceSuggestion
import com.addressiq.android.theme.LocalAddressIQTheme
import com.addressiq.android.ui.AddressDraft
import com.addressiq.android.ui.components.AddressIQButton
import com.addressiq.android.ui.components.ScreenScaffold
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Address capture for the Collect UI.
 *
 * Map flow (with a Google Maps key): Places autocomplete / current location →
 * interactive [MapWebView] centre pin → auto-derived (read-only) formatted
 * address → Street View pin-confirm where coverage exists. Falls back to GPS +
 * a manual formatted-address field when no key is available.
 */
@Composable
fun AddressScreen(
    initial: AddressDraft,
    googleMapsApiKey: String?,
    onNext: (AddressDraft) -> Unit,
    onStreetView: (AddressDraft) -> Unit,
    onCancel: () -> Unit,
    fetchLocation: suspend () -> Pair<Double, Double>?,
) {
    val theme = LocalAddressIQTheme.current
    val scope = rememberCoroutineScope()
    val mapsKey = googleMapsApiKey?.takeIf { it.isNotEmpty() }
    val maps = remember(mapsKey) { mapsKey?.let { MapsClient(it) } }
    val sessionToken = remember { System.currentTimeMillis().toString() }

    var lat by remember { mutableStateOf(initial.lat) }
    var lon by remember { mutableStateOf(initial.lon) }
    var formatted by remember { mutableStateOf(initial.formattedAddress.orEmpty()) }
    var placeId by remember { mutableStateOf(initial.placeId) }
    var loading by remember { mutableStateOf(false) }
    var resolving by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf<List<PlaceSuggestion>>(emptyList()) }
    var suppressGeocode by remember { mutableStateOf(false) }
    // §6.6 step 5: "Current Location | Search Address" input tabs.
    var mode by remember { mutableStateOf("current") }

    suspend fun capture() {
        loading = true
        try {
            val reading = fetchLocation()
            if (reading != null) {
                suppressGeocode = false
                lat = reading.first
                lon = reading.second
            }
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) { if (lat == null || lon == null) capture() }

    // Debounced reverse-geocode when the pin moves (map flow only).
    val centerKey = if (lat != null && lon != null) "%.5f,%.5f".format(lat, lon) else ""
    LaunchedEffect(centerKey) {
        val client = maps ?: return@LaunchedEffect
        val la = lat ?: return@LaunchedEffect
        val lo = lon ?: return@LaunchedEffect
        if (suppressGeocode) { suppressGeocode = false; return@LaunchedEffect }
        delay(600)
        resolving = true
        val addr = client.reverseGeocode(la, lo)
        resolving = false
        if (addr != null) formatted = addr
    }

    // Address draft (no Street View fields — those are set by the next stage).
    fun buildDraft(): AddressDraft = initial.copy(
        lat = lat,
        lon = lon,
        formattedAddress = formatted.trim(),
        placeId = placeId,
    )

    val canContinue = lat != null && lon != null && formatted.trim().isNotEmpty()

    ScreenScaffold(
        title = "Confirm your address",
        subtitle = "Search for your address or drop a pin on the map. We'll use this to verify where you live.",
        onClose = onCancel,
        footer = {
            AddressIQButton(
                text = if (resolving) "Loading…" else "Continue",
                onClick = {
                    val client = maps
                    val la = lat
                    val lo = lon
                    // §6.6 step 6 is coverage-gated: route to the Street View
                    // stage when a panorama exists, else advance to details.
                    if (client != null && la != null && lo != null) {
                        scope.launch {
                            resolving = true
                            val cov = client.streetViewCoverage(la, lo)
                            resolving = false
                            if (cov.available) onStreetView(buildDraft()) else onNext(buildDraft())
                        }
                    } else {
                        onNext(buildDraft())
                    }
                },
                enabled = canContinue && !resolving,
            )
        },
    ) {
        if (maps != null) {
            // §6.6 step 5: Current Location | Search Address tabs.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                listOf("current" to "Current Location", "search" to "Search Address").forEach { (key, label) ->
                    val on = mode == key
                    TextButton(
                        onClick = { mode = key },
                        modifier = Modifier
                            .weight(1f)
                            .border(1.dp, if (on) theme.primary else theme.border, RoundedCornerShape(10.dp)),
                    ) {
                        Text(label, color = if (on) theme.primary else theme.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            if (mode == "search") {
            // Search + autocomplete.
            OutlinedTextField(
                value = query,
                onValueChange = { text ->
                    query = text
                    scope.launch {
                        suggestions = if (text.trim().length >= 3) maps.autocomplete(text, sessionToken) else emptyList()
                    }
                },
                placeholder = { Text("Search your address", color = theme.inputPlaceholder) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = theme.inputText,
                    unfocusedTextColor = theme.inputText,
                    focusedBorderColor = theme.borderFocused,
                    unfocusedBorderColor = theme.inputBorder,
                    cursorColor = theme.primary,
                ),
            )
            if (suggestions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(theme.surface, RoundedCornerShape(12.dp))
                        .border(1.dp, theme.border, RoundedCornerShape(12.dp)),
                ) {
                    suggestions.forEach { s ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    suggestions = emptyList()
                                    scope.launch {
                                        val p = maps.placeDetails(s.placeId, sessionToken)
                                        if (p != null) {
                                            suppressGeocode = true
                                            lat = p.lat
                                            lon = p.lon
                                            placeId = s.placeId
                                            formatted = p.formattedAddress
                                        }
                                    }
                                }
                                .padding(horizontal = 14.dp, vertical = 11.dp),
                        ) {
                            Text(s.primaryText, color = theme.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            s.secondaryText?.let { Text(it, color = theme.textSecondary, fontSize = 12.sp) }
                        }
                    }
                }
            }
            }

            Spacer(modifier = Modifier.height(14.dp))
            if (loading || lat == null || lon == null) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(120.dp).background(theme.surface, RoundedCornerShape(theme.borderRadiusLg)),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(color = theme.primary, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.size(10.dp))
                    Text("Reading GPS…", color = theme.textSecondary, fontSize = 13.sp)
                }
            } else {
                MapWebView(
                    apiKey = mapsKey!!,
                    lat = lat!!,
                    lon = lon!!,
                    onCenterChanged = { la, lo -> lat = la; lon = lo },
                    modifier = Modifier.fillMaxWidth().height(220.dp),
                )
            }

            if (mode == "current") {
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(onClick = { scope.launch { capture() } }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Filled.MyLocation, contentDescription = null, tint = theme.primary, modifier = Modifier.size(14.dp))
                        Text("Use my current location", color = theme.primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text("Formatted address", color = theme.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(6.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(theme.surfaceSecondary, RoundedCornerShape(12.dp))
                    .border(1.dp, theme.border, RoundedCornerShape(12.dp))
                    .padding(14.dp),
            ) {
                if (resolving) {
                    CircularProgressIndicator(color = theme.primary, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(
                        text = formatted.ifEmpty { "Move the map or search to set your address." },
                        color = theme.text,
                        fontSize = 15.sp,
                    )
                }
            }
        } else {
            // No key → GPS + manual entry fallback.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(theme.surface, RoundedCornerShape(theme.borderRadiusLg))
                    .border(1.dp, theme.border, RoundedCornerShape(theme.borderRadiusLg))
                    .padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.MyLocation, contentDescription = null, tint = theme.primary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("CURRENT LOCATION", color = theme.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(10.dp))
                if (loading) {
                    Text("Reading GPS…", color = theme.textSecondary, fontSize = 13.sp)
                } else if (lat != null && lon != null) {
                    Text("%.6f, %.6f".format(lat, lon), color = theme.text, fontSize = 16.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { scope.launch { capture() } }) {
                        Text("Read again", color = theme.primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Spacer(modifier = Modifier.height(18.dp))
            Text("Formatted address", color = theme.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = formatted,
                onValueChange = { formatted = it },
                placeholder = { Text("e.g. 1 Marina, Lagos Island, Lagos", color = theme.inputPlaceholder) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth().height(80.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = theme.inputText,
                    unfocusedTextColor = theme.inputText,
                    focusedBorderColor = theme.borderFocused,
                    unfocusedBorderColor = theme.inputBorder,
                    cursorColor = theme.primary,
                ),
            )
        }
    }
}
