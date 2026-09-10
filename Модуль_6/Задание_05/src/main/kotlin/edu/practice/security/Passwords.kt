package edu.practice.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object Passwords {
    private fun derive(password: String, salt: ByteArray, iterations: Int) =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(password.toCharArray(), salt, iterations, 256))
            .encoded

    fun hash(password: String): String {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        return "210000:" +
            Base64.getEncoder().encodeToString(salt) +
            ":" +
            Base64.getEncoder().encodeToString(derive(password, salt, 210000))
    }

    fun verify(password: String, stored: String): Boolean =
        try {
            val parts = stored.split(':')
            MessageDigest.isEqual(
                Base64.getDecoder().decode(parts[2]),
                derive(password, Base64.getDecoder().decode(parts[1]), parts[0].toInt()),
            )
        } catch (e: Exception) {
            false
        }
}
