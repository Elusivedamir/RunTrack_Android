package com.runtrack.prototype.domain

import org.junit.Assert.*
import org.junit.Test

class BackupCryptoTest {
    @Test fun roundTripAndAuthentication() {
        val source = "sensitive GPS backup".toByteArray()
        val encrypted = PortableBackupCrypto.encrypt(source, "correct horse".toCharArray())
        assertFalse(source.contentEquals(encrypted))
        assertArrayEquals(source, PortableBackupCrypto.decrypt(encrypted, "correct horse".toCharArray()))
        assertThrows(IllegalArgumentException::class.java) {
            PortableBackupCrypto.decrypt(encrypted, "wrong pass".toCharArray())
        }
    }

    @Test fun corruptedPayloadIsRejected() {
        val encrypted = PortableBackupCrypto.encrypt(byteArrayOf(1, 2, 3, 4), "12345678".toCharArray())
        encrypted[encrypted.lastIndex] = (encrypted.last().toInt() xor 0x01).toByte()
        assertThrows(IllegalArgumentException::class.java) {
            PortableBackupCrypto.decrypt(encrypted, "12345678".toCharArray())
        }
    }
}
