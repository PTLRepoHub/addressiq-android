package com.addressiq.android.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import java.util.Base64

/**
 * Storage abstraction the rest of the SDK depends on. Partners can swap the
 * primary impl out via the public `AddressIQ.configure { secureStore(...) }`
 * DSL — for example to wire EnterpriseKMS or a custom vault.
 *
 * The default implementation, [TinkSecureKeyValueStore], encrypts every value
 * using a Tink AEAD primitive bound to a master key stored in the Android
 * Keystore (StrongBox where available). This replaces the deprecated
 * `EncryptedSharedPreferences` / Jetsec stack the older RN SDK relied on.
 */
public interface SecureKeyValueStore {
    public fun put(key: String, value: String)
    public fun get(key: String): String?
    public fun remove(key: String)
    public fun clear()
}

internal class TinkSecureKeyValueStore(
    context: Context,
    prefsName: String = "addressiq_secure",
) : SecureKeyValueStore {

    private val prefs: SharedPreferences = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
    private val aead: Aead

    init {
        AeadConfig.register()
        val keysetHandle = AndroidKeysetManager.Builder()
            .withSharedPref(context, "addressiq_master_keyset", prefsName)
            .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
            .withMasterKeyUri("android-keystore://addressiq_master_key")
            .build()
            .keysetHandle
        aead = keysetHandle.getPrimitive(Aead::class.java)
    }

    override fun put(key: String, value: String) {
        val ciphertext = aead.encrypt(value.toByteArray(Charsets.UTF_8), key.toByteArray(Charsets.UTF_8))
        prefs.edit { putString(key, Base64.getEncoder().encodeToString(ciphertext)) }
    }

    override fun get(key: String): String? {
        val encoded = prefs.getString(key, null) ?: return null
        return runCatching {
            val ciphertext = Base64.getDecoder().decode(encoded)
            val plaintext = aead.decrypt(ciphertext, key.toByteArray(Charsets.UTF_8))
            String(plaintext, Charsets.UTF_8)
        }.getOrNull()
    }

    override fun remove(key: String) {
        prefs.edit { remove(key) }
    }

    override fun clear() {
        prefs.edit { clear() }
    }
}
