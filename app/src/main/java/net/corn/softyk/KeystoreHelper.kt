package net.corn.softyk

import android.content.Context
import android.security.keystore.KeyProperties
import android.security.keystore.KeyProtection
import android.util.Log
import java.security.KeyStore
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * Manages the HMAC-SHA1 secret in the Android Keystore.
 *
 * The key is stored non-exportable: once imported, raw bytes can never be retrieved.
 */
object KeystoreHelper {

    private const val PROVIDER = "AndroidKeyStore"
    private const val KEY_ALIAS = "softyk_hmac_secret"
    private const val PREFS_NAME = "softyk_prefs"
    private const val PREF_LT64 = "hmac_lt64"

    /** Persists whether the slot behaves as HMAC_LT64 (variable-length) or fixed 64-byte. */
    private fun setLt64(context: Context, lt64: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(PREF_LT64, lt64).apply()
    }

    /** Defaults to HMAC_LT64 (the Yubikey default for variable-input slots). */
    fun isLt64(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(PREF_LT64, true)

    /**
     * Imports a hex-encoded HMAC-SHA1 secret into the Keystore.
     *
     * Accepts plain hex (aabbcc), colon-separated (AA:BB:CC), or space-separated (AA BB CC).
     *
     * @param lt64 true for HMAC_LT64 (variable-length challenge), false for fixed 64-byte challenge.
     * @throws IllegalArgumentException on malformed input.
     * @throws Exception on Keystore failure.
     */
    fun importSecret(context: Context, hexInput: String, lt64: Boolean) {
        val hex = hexInput.trim()
            .replace(":", "")
            .replace(" ", "")
            .lowercase()

        require(hex.isNotEmpty()) { "Secret must not be empty" }
        require(hex.length % 2 == 0) { "Hex string must have an even number of characters" }
        require(hex.all { it in '0'..'9' || it in 'a'..'f' }) {
            "Input contains invalid hex characters"
        }

        val secretBytes = ByteArray(hex.length / 2) { i ->
            hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }

        val secretKeySpec = SecretKeySpec(secretBytes, "HmacSHA1")
        val keyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }

        val protection = KeyProtection.Builder(KeyProperties.PURPOSE_SIGN).build()
        keyStore.setEntry(KEY_ALIAS, KeyStore.SecretKeyEntry(secretKeySpec), protection)
        setLt64(context, lt64)
    }

    /** Returns true if a key is currently stored in the Keystore. */
    fun hasKey(): Boolean = try {
        KeyStore.getInstance(PROVIDER).apply { load(null) }.containsAlias(KEY_ALIAS)
    } catch (e: Exception) {
        false
    }

    /**
     * Computes HMAC-SHA1 of [challenge] using the stored key.
     * The HMAC operation executes inside the hardware security module;
     * the key material never leaves it.
     */
    fun computeHmac(context: Context, challenge: ByteArray): ByteArray {
        val keyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }
        val key = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
            ?: throw IllegalStateException(
                "No HMAC key found in Keystore. Open SoftYK and import your secret first."
            )

        // Mirror the Yubikey firmware:
        //  - HMAC_LT64 (variable input): only a full 64-byte challenge is truncated. The key strips
        //    every trailing byte equal to the 64th byte. A challenge shorter than 64 bytes is
        //    used unmodified.
        //  - Fixed 64-byte: a challenge shorter than 64 bytes is zero-padded to exactly 64 bytes.
        val trimmed = if (isLt64(context) && challenge.size >= 64) {
            val pad = challenge[63]
            challenge.copyOf(64).dropLastWhile { it == pad }.toByteArray()
        } else if (!isLt64(context) && challenge.size < 64) {
            challenge.copyOf(64)  // zero-pads
        } else {
            challenge
        }
        val hex = trimmed.joinToString("") { "%02x".format(it) }
        Log.i("SoftYK", "Computing hmac for challenge: $hex");

        val response = Mac.getInstance("HmacSHA1").run {
            init(key)
            doFinal(trimmed)
        }

        val hexresponse = response.joinToString("") { "%02x".format(it) }
        Log.i("SoftYK", "Response: $hexresponse")
        return response
    }

    /**
     * Removes the stored key from the Keystore.
     * After this, KeePass2Android will no longer be able to unlock using SoftYK
     * until a new secret is imported.
     */
    fun deleteKey() {
        try {
            KeyStore.getInstance(PROVIDER).apply { load(null) }.deleteEntry(KEY_ALIAS)
        } catch (e: Exception) {
            // Nothing to delete or Keystore unavailable — safe to ignore
        }
    }
}
