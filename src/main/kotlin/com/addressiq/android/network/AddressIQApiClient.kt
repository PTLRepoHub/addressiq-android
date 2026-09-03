package com.addressiq.android.network

import com.addressiq.android.AddressIQError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * The SDK's JSON transport, behind one seam.
 *
 * [baseUrl] and [apiKey] are constructor parameters rather than values read from
 * [com.addressiq.android.AddressIQDeployment], which resolves its hosts from
 * constants baked at build time. That is the whole point of this class: a test
 * can point it at a local server by passing a URL, so the SDK needs no
 * test-only override, no mutable client field, and no way for a shipped build
 * to be aimed at an arbitrary host.
 *
 * Mirrors the Flutter SDK's `lib/src/data/api_client.dart`.
 */
internal class AddressIQApiClient(
    private val apiKey: String,
    private val baseUrl: String,
    private val http: OkHttpClient = defaultHttpClient(),
    private val json: Json = defaultJson(),
) {
    /**
     * POSTs [body] as JSON and decodes the response object.
     *
     * A non-2xx throws [AddressIQError.Http] carrying the server's `code` and
     * `message` when it sent them, so callers see the API's own error rather
     * than a status line. An empty body decodes to an empty map — cancel and
     * other 204-shaped endpoints are not errors.
     */
    suspend fun post(
        path: String,
        body: Map<String, Any?>,
        idempotencyKey: String? = null,
        branchId: String? = null,
    ): Map<String, Any?> = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url(baseUrl + path)
            .post(JsonAny.toJson(body).toString().toRequestBody(JSON_MEDIA_TYPE))
            .header("x-api-key", apiKey)
            .header("idempotency-key", idempotencyKey ?: makeIdempotencyKey())
            .apply { branchId?.let { header("x-branch-id", it) } }
            .build()
        http.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            val parsed = if (raw.isBlank()) emptyMap() else JsonAny.decodeObject(json, raw)
            if (!resp.isSuccessful) {
                throw AddressIQError.Http(
                    resp.code,
                    parsed["code"] as? String,
                    parsed["message"] as? String ?: resp.message,
                )
            }
            parsed
        }
    }

    /** GETs a JSON array of objects. */
    suspend fun getList(path: String): List<Map<String, Any?>> = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url(baseUrl + path)
            .header("x-api-key", apiKey)
            .build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw AddressIQError.Http(resp.code, null, resp.message)
            JsonAny.decodeObjectArray(json, resp.body?.string().orEmpty())
        }
    }

    /**
     * DELETEs with a JSON body. Fire-and-forget: the response is drained and
     * discarded, and no idempotency key is sent — session teardown is naturally
     * idempotent server-side.
     */
    suspend fun delete(path: String, body: Map<String, Any?>) = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url(baseUrl + path)
            .delete(JsonAny.toJson(body).toString().toRequestBody(JSON_MEDIA_TYPE))
            .header("x-api-key", apiKey)
            .build()
        http.newCall(req).execute().close()
    }

    /**
     * POSTs an already-serialized body and reports only whether it landed.
     *
     * Telemetry batches are assembled as raw JSON text (the queue stores each
     * event's payload as a string, so re-parsing them only to re-encode would be
     * wasted work) and must never throw: a failed upload cannot be allowed to
     * break collection. Failures are logged and reported as `false` so the
     * caller leaves the batch on the queue for the next flush.
     */
    suspend fun postRaw(path: String, rawJsonBody: String): Boolean = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url(baseUrl + path)
            .post(rawJsonBody.toRequestBody(JSON_MEDIA_TYPE))
            .header("x-api-key", apiKey)
            .build()
        runCatching {
            http.newCall(req).execute().use { response ->
                if (!response.isSuccessful) {
                    android.util.Log.w(
                        "AddressIQ",
                        "telemetry flush rejected: HTTP ${response.code} from ${req.url}",
                    )
                }
                response.isSuccessful
            }
        }.getOrElse { error ->
            android.util.Log.w("AddressIQ", "telemetry flush failed for ${req.url}", error)
            false
        }
    }

    private fun makeIdempotencyKey(): String =
        "iqidem_android_${UUID.randomUUID().toString().replace("-", "").take(16)}"

    internal companion object {
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        fun defaultJson(): Json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    }
}

/**
 * Tiny adapter between `Map<String, Any?>` — the shape the public API speaks —
 * and kotlinx.serialization's typed [JsonElement] tree. Booleans and numbers
 * must survive the round trip in both directions: the API sends
 * `{"isExisting": false}` and expects `{"startDigital": true}`, so neither side
 * may flatten values to strings.
 */
internal object JsonAny {
    fun toJson(value: Map<String, Any?>): JsonObject =
        JsonObject(value.mapValues { (_, v) -> toElement(v) })

    private fun toElement(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is JsonElement -> value
        is String -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is Map<*, *> -> JsonObject(value.entries.associate { (k, v) -> k.toString() to toElement(v) })
        is Iterable<*> -> JsonArray(value.map { toElement(it) })
        else -> JsonPrimitive(value.toString())
    }

    /** Decodes a JSON object body into plain Kotlin values (String/Boolean/Long/Double/Map/List/null). */
    fun decodeObject(json: Json, raw: String): Map<String, Any?> =
        json.parseToJsonElement(raw).jsonObject.mapValues { (_, v) -> fromElement(v) }

    /** Decodes a JSON array body whose entries are objects. */
    fun decodeObjectArray(json: Json, raw: String): List<Map<String, Any?>> =
        json.parseToJsonElement(raw).jsonArray.map { el ->
            el.jsonObject.mapValues { (_, v) -> fromElement(v) }
        }

    private fun fromElement(element: JsonElement): Any? = when (element) {
        is JsonNull -> null
        is JsonPrimitive ->
            if (element.isString) element.content
            else element.booleanOrNull
                ?: element.longOrNull
                ?: element.doubleOrNull
                ?: element.content
        is JsonObject -> element.mapValues { (_, v) -> fromElement(v) }
        is JsonArray -> element.map { fromElement(it) }
    }
}
