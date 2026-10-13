package com.example.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Small encrypted store for user secrets (the Gemini API key). Values are AES-GCM encrypted with a
 * key that never leaves the Android Keystore, and the file is excluded from backups
 * (see res/xml/backup_rules.xml and data_extraction_rules.xml).
 */
class SecretStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val legacyPrefs = context.applicationContext.getSharedPreferences("focusflow_prefs", Context.MODE_PRIVATE)

    init {
        // One-time move of the key that older versions stored in plain text.
        legacyPrefs.getString(LEGACY_GEMINI_KEY, null)?.let { plain ->
            if (plain.isNotBlank() && geminiApiKey.isBlank()) geminiApiKey = plain
            legacyPrefs.edit().remove(LEGACY_GEMINI_KEY).apply()
        }
    }

    var geminiApiKey: String
        get() = read(GEMINI_KEY)
        set(value) = write(GEMINI_KEY, value.trim())

    private fun read(name: String): String {
        val stored = prefs.getString(name, null) ?: return ""
        return runCatching {
            val bytes = Base64.decode(stored, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, IV_SIZE))
            String(cipher.doFinal(bytes, IV_SIZE, bytes.size - IV_SIZE), Charsets.UTF_8)
        }.getOrElse {
            Log.w(TAG, "Stored secret unreadable (e.g. restored on a new device); clearing it")
            prefs.edit().remove(name).apply()
            ""
        }
    }

    private fun write(name: String, value: String) {
        if (value.isEmpty()) {
            prefs.edit().remove(name).apply()
            return
        }
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val encrypted = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        prefs.edit().putString(name, Base64.encodeToString(encrypted, Base64.NO_WRAP)).apply()
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).apply {
            init(
                KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
        }.generateKey()
    }

    companion object {
        const val FILE = "focusflow_secure"
        private const val TAG = "SecretStore"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "focus_island_secrets"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_SIZE = 12
        private const val GEMINI_KEY = "gemini_api_key"
        private const val LEGACY_GEMINI_KEY = "gemini_api_key"
    }
}
