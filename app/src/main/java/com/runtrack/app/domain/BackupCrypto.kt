package com.runtrack.app.domain

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Pure JVM portable backup cryptography, isolated for deterministic unit testing. */
object PortableBackupCrypto {
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val GCM_TAG_BITS = 128
    private const val PBKDF2_ITERATIONS = 210_000
    private val MAGIC = "RTBK1\u0000".toByteArray(StandardCharsets.US_ASCII)

    fun encrypt(plain: ByteArray, passphrase: CharArray, random: SecureRandom = SecureRandom()): ByteArray {
        require(passphrase.size >= 8) { "Backup passphrase must be at least 8 characters" }
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val key = deriveKey(passphrase, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        cipher.updateAAD(MAGIC)
        val ciphertext = cipher.doFinal(plain)
        return ByteBuffer.allocate(MAGIC.size + salt.size + iv.size + ciphertext.size)
            .put(MAGIC).put(salt).put(iv).put(ciphertext).array()
    }

    fun decrypt(packageBytes: ByteArray, passphrase: CharArray): ByteArray {
        require(passphrase.size >= 8) { "Backup passphrase must be at least 8 characters" }
        require(packageBytes.size > MAGIC.size + SALT_BYTES + IV_BYTES + 16) { "Backup package is too short" }
        val buffer = ByteBuffer.wrap(packageBytes)
        val magic = ByteArray(MAGIC.size).also(buffer::get)
        require(magic.contentEquals(MAGIC)) { "Invalid backup header" }
        val salt = ByteArray(SALT_BYTES).also(buffer::get)
        val iv = ByteArray(IV_BYTES).also(buffer::get)
        val ciphertext = ByteArray(buffer.remaining()).also(buffer::get)
        val key = deriveKey(passphrase, salt)
        return try {
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
                updateAAD(MAGIC)
                doFinal(ciphertext)
            }
        } catch (t: Throwable) {
            throw IllegalArgumentException("Wrong passphrase or corrupted backup", t)
        }
    }

    private fun deriveKey(passphrase: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, PBKDF2_ITERATIONS, 256)
        return try {
            SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }
}
