package com.mathapplocker.foss.data

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * PIN storage uses salted PBKDF2-HMAC-SHA256 instead of the old app's unsalted
 * single-round SHA-256, so a stolen preferences file cannot be brute-forced cheaply.
 */
object PinHasher {

    fun create(
        pin: String,
        iterations: Int = Prefs.DEFAULT_ITERATIONS,
    ): StoredPin {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val hash = derive(pin, salt, iterations)
        return StoredPin(
            saltBase64 = Base64.getEncoder().encodeToString(salt),
            hashBase64 = Base64.getEncoder().encodeToString(hash),
            iterations = iterations,
        )
    }

    fun verify(pin: String, saltBase64: String?, hashBase64: String?, iterations: Int): Boolean {
        if (saltBase64.isNullOrEmpty() || hashBase64.isNullOrEmpty()) return false
        return runCatching {
            val salt = Base64.getDecoder().decode(saltBase64)
            val expected = Base64.getDecoder().decode(hashBase64)
            val actual = derive(pin, salt, iterations)
            MessageDigest.isEqual(expected, actual)
        }.getOrDefault(false)
    }

    private fun derive(pin: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec)
                .encoded
        } finally {
            spec.clearPassword()
        }
    }

    data class StoredPin(val saltBase64: String, val hashBase64: String, val iterations: Int)

    private const val SALT_BYTES = 16
    private const val KEY_BITS = 256
}
