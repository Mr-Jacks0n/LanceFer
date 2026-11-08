package com.lancefer.lancedrop.data

import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * LanceDrop Android — CryptoStreamManager
 * Chiffrement authentifié (AEAD) AES-256-GCM pour chaque chunk de transfert FDSP.
 * 100% compatible avec le format binaire de Node.js :
 * [IV: 12 octets] + [Ciphertext: N octets] + [AuthTag: 16 octets]
 */
object CryptoStreamManager {

    const val IV_LENGTH = 12
    const val AUTH_TAG_LENGTH = 16
    private const val GCM_TAG_BITS = 128

    private val secureRandom = SecureRandom()

    /**
     * Normalise une clé de session en tableau de 32 octets (AES-256).
     */
    fun normalizeSessionKey(key: String): ByteArray {
        val trimmed = key.trim()
        if (trimmed.length == 64 && trimmed.matches(Regex("^[0-9a-fA-F]+$"))) {
            val result = ByteArray(32)
            for (i in 0 until 32) {
                val byteVal = trimmed.substring(i * 2, i * 2 + 2).toInt(16)
                result[i] = byteVal.toByte()
            }
            return result
        }
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(trimmed.toByteArray(Charsets.UTF_8))
    }

    /**
     * Génère une clé de session éphémère de 256 bits au format hexadécimal (64 caractères).
     */
    fun generateSessionKey(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Chiffre un chunk avec AES-256-GCM.
     * Produit : [IV: 12 octets] + [Ciphertext + Tag: N+16 octets]
     */
    fun encryptChunk(plainChunk: ByteArray, sessionKey: String, chunkIndex: Int = 0): ByteArray {
        val keyBytes = normalizeSessionKey(sessionKey)
        val iv = ByteArray(IV_LENGTH)
        secureRandom.nextBytes(iv)

        // Injecter l'index de chunk dans les 4 derniers octets de l'IV
        if (chunkIndex >= 0) {
            val buffer = ByteBuffer.wrap(iv)
            buffer.putInt(8, chunkIndex)
        }

        val keySpec = SecretKeySpec(keyBytes, "AES")
        val gcmSpec = GCMParameterSpec(GCM_TAG_BITS, iv)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)

        val cipherAndTag = cipher.doFinal(plainChunk)

        val result = ByteArray(IV_LENGTH + cipherAndTag.size)
        System.arraycopy(iv, 0, result, 0, IV_LENGTH)
        System.arraycopy(cipherAndTag, 0, result, IV_LENGTH, cipherAndTag.size)
        return result
    }

    /**
     * Déchiffre et authentifie un chunk reçu.
     * Attend : [IV: 12 octets] + [Ciphertext: N octets] + [AuthTag: 16 octets]
     * @param expectedChunkIndex Index attendu du chunk (vérification anti-rejeu)
     * @throws javax.crypto.AEADBadTagException si la donnée a été modifiée
     * @throws SecurityException si l'index dans l'IV ne correspond pas à expectedChunkIndex
     */
    fun decryptChunk(encryptedChunk: ByteArray, sessionKey: String, expectedChunkIndex: Int? = null): ByteArray {
        val minLength = IV_LENGTH + AUTH_TAG_LENGTH
        if (encryptedChunk.size < minLength) {
            throw IllegalArgumentException("Trame chiffrée trop courte (${encryptedChunk.size} octets)")
        }

        val keyBytes = normalizeSessionKey(sessionKey)
        val iv = ByteArray(IV_LENGTH)
        System.arraycopy(encryptedChunk, 0, iv, 0, IV_LENGTH)

        // Contrôle anti-rejeu et anti-réordonnancement
        if (expectedChunkIndex != null && expectedChunkIndex >= 0) {
            val ivBuffer = ByteBuffer.wrap(iv)
            val embeddedIndex = ivBuffer.getInt(8)
            if (embeddedIndex != expectedChunkIndex) {
                throw SecurityException("Altération ou réordonnancement de bloc détecté (Index attendu: $expectedChunkIndex, Reçu dans IV: $embeddedIndex)")
            }
        }

        val cipherAndTagLength = encryptedChunk.size - IV_LENGTH
        val cipherAndTag = ByteArray(cipherAndTagLength)
        System.arraycopy(encryptedChunk, IV_LENGTH, cipherAndTag, 0, cipherAndTagLength)

        val keySpec = SecretKeySpec(keyBytes, "AES")
        val gcmSpec = GCMParameterSpec(GCM_TAG_BITS, iv)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)

        return cipher.doFinal(cipherAndTag)
    }
}
