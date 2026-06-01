package com.addressiq.android.storage

import android.content.ContentValues
import android.content.Context
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SQLiteOpenHelper

/**
 * SQLCipher-backed FIFO queue for telemetry events the SDK couldn't
 * immediately ship to the API (offline / retry-backoff / WorkManager
 * deferral). The database file lives in the app's `cacheDir/addressiq/`.
 *
 * The encryption key is generated once on first run (by `SecureKeyValueStore`)
 * and stored as a Keystore-backed secret. Rotating the key requires
 * `wipe()` + re-init.
 */
public class AddressIQTelemetryQueue private constructor(
    context: Context,
    private val cipherKey: String,
) {
    public companion object {
        @Volatile private var INSTANCE: AddressIQTelemetryQueue? = null

        public fun init(context: Context, cipherKey: String): AddressIQTelemetryQueue {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AddressIQTelemetryQueue(context.applicationContext, cipherKey).also { INSTANCE = it }
            }
        }

        public fun shared(): AddressIQTelemetryQueue =
            INSTANCE ?: error("AddressIQTelemetryQueue.init(context, cipherKey) must be called first")
    }

    private val helper = QueueOpenHelper(context)

    public data class Entry(
        val rowId: Long,
        val eventId: String,
        val payload: String,
    )

    public fun enqueue(eventId: String, payload: String) {
        val db = helper.getWritableDatabase(cipherKey)
        val values = ContentValues().apply {
            put("event_id", eventId)
            put("payload", payload)
            put("queued_at", System.currentTimeMillis())
        }
        db.insertWithOnConflict("events", null, values, SQLiteDatabase.CONFLICT_IGNORE)
    }

    public fun dequeue(batchSize: Int = 50): List<Entry> {
        val db = helper.getReadableDatabase(cipherKey)
        val out = mutableListOf<Entry>()
        db.query("events", arrayOf("rowid", "event_id", "payload"), null, null, null, null, "rowid ASC", batchSize.toString())
            .use { cursor ->
                while (cursor.moveToNext()) {
                    out += Entry(cursor.getLong(0), cursor.getString(1), cursor.getString(2))
                }
            }
        return out
    }

    public fun acknowledge(rowIds: List<Long>) {
        if (rowIds.isEmpty()) return
        val db = helper.getWritableDatabase(cipherKey)
        val placeholders = rowIds.joinToString(",") { "?" }
        db.execSQL("DELETE FROM events WHERE rowid IN ($placeholders)", rowIds.map { it.toString() }.toTypedArray())
    }

    public fun count(): Int {
        val db = helper.getReadableDatabase(cipherKey)
        db.rawQuery("SELECT count(*) FROM events", null).use {
            return if (it.moveToFirst()) it.getInt(0) else 0
        }
    }

    public fun wipe() {
        helper.getWritableDatabase(cipherKey).execSQL("DELETE FROM events")
    }

    private class QueueOpenHelper(context: Context) :
        SQLiteOpenHelper(context, "addressiq-telemetry.db", null, 1) {

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE events (
                    event_id TEXT PRIMARY KEY,
                    payload TEXT NOT NULL,
                    queued_at INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX idx_events_queued_at ON events(queued_at)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            // No migrations yet; bump schema version + add ALTERs here.
        }
    }
}
