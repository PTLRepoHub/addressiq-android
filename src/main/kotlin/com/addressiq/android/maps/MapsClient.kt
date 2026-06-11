package com.addressiq.android.maps

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/** A Places Autocomplete suggestion. */
data class PlaceSuggestion(val placeId: String, val primaryText: String, val secondaryText: String?)

/** A place resolved to a canonical formatted address + coordinates. */
data class ResolvedPlace(val formattedAddress: String, val lat: Double, val lon: Double)

/** Street View coverage from the (free) metadata endpoint. */
data class StreetViewCoverage(val available: Boolean, val panoId: String?)

/**
 * Google Maps Platform REST clients for the Collect UI map flow.
 *
 * Pure OkHttp — no native Google Maps SDK (the map + Street View render in a
 * built-in [android.webkit.WebView]). Every call is best-effort: on any error
 * it resolves to an empty/null result so the address step degrades to manual
 * entry rather than throwing.
 */
class MapsClient(
    private val apiKey: String,
    private val http: OkHttpClient = OkHttpClient(),
) {
    private companion object {
        const val PLACES_BASE = "https://places.googleapis.com/v1"
        const val MAPS_BASE = "https://maps.googleapis.com/maps/api"
    }

    suspend fun autocomplete(input: String, sessionToken: String?): List<PlaceSuggestion> =
        withContext(Dispatchers.IO) {
            if (input.isBlank() || apiKey.isEmpty()) return@withContext emptyList()
            runCatching {
                val body = JSONObject().apply {
                    put("input", input)
                    sessionToken?.let { put("sessionToken", it) }
                }.toString()
                val req = Request.Builder()
                    .url("$PLACES_BASE/places:autocomplete")
                    .post(body.toRequestBody("application/json".toMediaType()))
                    .header("X-Goog-Api-Key", apiKey)
                    .build()
                http.newCall(req).execute().use { r ->
                    if (!r.isSuccessful) return@runCatching emptyList<PlaceSuggestion>()
                    val json = JSONObject(r.body?.string().orEmpty())
                    val suggestions: JSONArray = json.optJSONArray("suggestions")
                        ?: return@runCatching emptyList<PlaceSuggestion>()
                    (0 until suggestions.length()).mapNotNull { i ->
                        val p = suggestions.getJSONObject(i).optJSONObject("placePrediction")
                            ?: return@mapNotNull null
                        val id = p.optString("placeId")
                        if (id.isEmpty()) return@mapNotNull null
                        val sf = p.optJSONObject("structuredFormat")
                        val main = sf?.optJSONObject("mainText")?.optString("text")
                        val sec = sf?.optJSONObject("secondaryText")?.optString("text")
                        val text = p.optJSONObject("text")?.optString("text")
                        PlaceSuggestion(id, main ?: text ?: "", if (sec.isNullOrEmpty()) null else sec)
                    }
                }
            }.getOrDefault(emptyList())
        }

    suspend fun placeDetails(placeId: String, sessionToken: String?): ResolvedPlace? =
        withContext(Dispatchers.IO) {
            if (placeId.isEmpty() || apiKey.isEmpty()) return@withContext null
            runCatching {
                val url = buildString {
                    append("$PLACES_BASE/places/$placeId")
                    if (sessionToken != null) append("?sessionToken=$sessionToken")
                }
                val req = Request.Builder()
                    .url(url)
                    .header("X-Goog-Api-Key", apiKey)
                    .header("X-Goog-FieldMask", "formattedAddress,location")
                    .build()
                http.newCall(req).execute().use { r ->
                    if (!r.isSuccessful) return@runCatching null
                    val json = JSONObject(r.body?.string().orEmpty())
                    val loc = json.optJSONObject("location") ?: return@runCatching null
                    if (!loc.has("latitude") || !loc.has("longitude")) return@runCatching null
                    ResolvedPlace(
                        formattedAddress = json.optString("formattedAddress", ""),
                        lat = loc.getDouble("latitude"),
                        lon = loc.getDouble("longitude"),
                    )
                }
            }.getOrNull()
        }

    suspend fun reverseGeocode(lat: Double, lon: Double): String? =
        withContext(Dispatchers.IO) {
            if (apiKey.isEmpty()) return@withContext null
            runCatching {
                val req = Request.Builder()
                    .url("$MAPS_BASE/geocode/json?latlng=$lat,$lon&key=$apiKey")
                    .build()
                http.newCall(req).execute().use { r ->
                    if (!r.isSuccessful) return@runCatching null
                    val json = JSONObject(r.body?.string().orEmpty())
                    if (json.optString("status") != "OK") return@runCatching null
                    val results = json.optJSONArray("results") ?: return@runCatching null
                    if (results.length() == 0) return@runCatching null
                    results.getJSONObject(0).optString("formatted_address").ifEmpty { null }
                }
            }.getOrNull()
        }

    suspend fun streetViewCoverage(lat: Double, lon: Double): StreetViewCoverage =
        withContext(Dispatchers.IO) {
            if (apiKey.isEmpty()) return@withContext StreetViewCoverage(false, null)
            runCatching {
                val req = Request.Builder()
                    .url("$MAPS_BASE/streetview/metadata?location=$lat,$lon&key=$apiKey")
                    .build()
                http.newCall(req).execute().use { r ->
                    val json = JSONObject(r.body?.string().orEmpty())
                    if (json.optString("status") != "OK") return@runCatching StreetViewCoverage(false, null)
                    StreetViewCoverage(true, json.optString("pano_id").ifEmpty { null })
                }
            }.getOrDefault(StreetViewCoverage(false, null))
        }
}
