package com.lancefer.lancedrop.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.AEADBadTagException

class CryptoStreamManagerTest {

    @Test
    fun testSessionKeyGeneration() {
        val key = CryptoStreamManager.generateSessionKey()
        assertEquals(64, key.length)
        assertTrue(key.matches(Regex("^[0-9a-fA-F]+$")))
    }

    @Test
    fun testSessionKeyNormalization() {
        val hexKey = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        val normalized = CryptoStreamManager.normalizeSessionKey(hexKey)
        assertEquals(32, normalized.size)

        val stringKey = "passphrase_pour_transfert_local"
        val derived = CryptoStreamManager.normalizeSessionKey(stringKey)
        assertEquals(32, derived.size)
    }

    @Test
    fun testEncryptAndDecryptChunk() {
        val sessionKey = CryptoStreamManager.generateSessionKey()
        val plainText = "Test confidentiel LanceDrop sécurisé par AES-256-GCM"
        val plainBytes = plainText.toByteArray(Charsets.UTF_8)

        val encrypted = CryptoStreamManager.encryptChunk(plainBytes, sessionKey, chunkIndex = 3)
        assertEquals(CryptoStreamManager.IV_LENGTH + plainBytes.size + CryptoStreamManager.AUTH_TAG_LENGTH, encrypted.size)

        val decrypted = CryptoStreamManager.decryptChunk(encrypted, sessionKey)
        assertArrayEquals(plainBytes, decrypted)
        assertEquals(plainText, String(decrypted, Charsets.UTF_8))
    }

    @Test(expected = Exception::class)
    fun testTamperingDetectionThrows() {
        val sessionKey = CryptoStreamManager.generateSessionKey()
        val plainBytes = "Secret non falsifiable".toByteArray(Charsets.UTF_8)

        val encrypted = CryptoStreamManager.encryptChunk(plainBytes, sessionKey, chunkIndex = 0)
        val tampered = encrypted.clone()
        // Altération du dernier octet (dans le tag d'authentification)
        tampered[tampered.size - 1] = (tampered[tampered.size - 1].toInt() xor 0xFF).toByte()

        CryptoStreamManager.decryptChunk(tampered, sessionKey)
    }

    @Test(expected = SecurityException::class)
    fun testReplayOrReorderingDetectionThrows() {
        val sessionKey = CryptoStreamManager.generateSessionKey()
        val plainBytes = "Secret chunk 5".toByteArray(Charsets.UTF_8)

        val encrypted = CryptoStreamManager.encryptChunk(plainBytes, sessionKey, chunkIndex = 5)
        // Tentative de déchiffrement en prétendant être le chunk 4 (rejeu / réordonnancement)
        CryptoStreamManager.decryptChunk(encrypted, sessionKey, expectedChunkIndex = 4)
    }

    @Test
    fun testMatchingChunkIndexSucceeds() {
        val sessionKey = CryptoStreamManager.generateSessionKey()
        val plainBytes = "Secret chunk 5".toByteArray(Charsets.UTF_8)

        val encrypted = CryptoStreamManager.encryptChunk(plainBytes, sessionKey, chunkIndex = 5)
        val decrypted = CryptoStreamManager.decryptChunk(encrypted, sessionKey, expectedChunkIndex = 5)
        assertArrayEquals(plainBytes, decrypted)
    }
}
