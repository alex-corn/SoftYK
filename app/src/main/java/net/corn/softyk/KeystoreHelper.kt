package net.corn.softyk

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

    /**
     * Imports a hex-encoded HMAC-SHA1 secret into the Keystore.
     *
     * Accepts plain hex (aabbcc), colon-separated (AA:BB:CC), or space-separated (AA BB CC).
     *
     * @throws IllegalArgumentException on malformed input.
     * @throws Exception on Keystore failure.
     */
    fun importSecret(hexInput: String) {
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
    fun computeHmac(challenge: ByteArray): ByteArray {
        val keyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }
        val key = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
            ?: throw IllegalStateException(
                "No HMAC key found in Keystore. Open SoftYK and import your secret first."
            )

        // Keepass2Android sends a 32-byte challenge padded to 64 bytes by appending spaces (0x20).
        // But the yubikey trims trailing 0x20 bytes, even if the result ends up being shorter than 32 bytes.
        // So we have to trim trailing 0x20 bytes rather than simply taking the first 32 bytes.
        val trimmed = challenge.dropLastWhile { it == 0x20.toByte() }.toByteArray()
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
