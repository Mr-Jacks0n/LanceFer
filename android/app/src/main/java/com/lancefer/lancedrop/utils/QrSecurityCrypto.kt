package com.lancefer.lancedrop.utils

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * LanceDrop Android — Chiffrement Cryptographique pour Codes QR Sécurisés
 * Implémente le chiffrement AES-256-CBC identique à la version Desktop
 * afin d'interdire la lecture des IP/Ports/PIN par des scanners tiers ou caméras standard.
 */
object QrSecurityCrypto {

    private const val SECRET_SALT = "LanceDrop::Protocol::SecureTransport::QR::2026"
    private const val PREFIX = "LANCEDROP:SEC:v1:"

    private val masterKey: ByteArray by lazy {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.digest(SECRET_SALT.toByteArray(Charsets.UTF_8))
    }

    /**
     * Chiffre une chaîne JSON en payload QR opaque LANCEDROP:SEC:v1:...
     */
    fun encryptQrPayload(plainText: String): String {
        return try {
            val iv = ByteArray(16)
            SecureRandom().nextBytes(iv)

            val keySpec = SecretKeySpec(masterKey, "AES")
            val ivSpec = IvParameterSpec(iv)
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)

            val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(iv.size + cipherText.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(cipherText, 0, combined, iv.size, cipherText.size)

            val base64 = Base64.encodeToString(combined, Base64.NO_WRAP)
            PREFIX + base64
        } catch (e: Exception) {
            e.printStackTrace()
            plainText
        }
    }

    /**
     * Déchiffre un payload QR sécurisé vers le JSON d'origine.
     */
    fun decryptQrPayload(encryptedPayload: String?): String? {
        if (encryptedPayload.isNullOrBlank()) return null

        val trimmed = encryptedPayload.trim()
        // Rétrocompatibilité si QR non chiffré
        if (trimmed.startsWith("{")) {
            return trimmed
        }

        if (!trimmed.startsWith(PREFIX)) {
            return null
        }

        return try {
            val base64Data = trimmed.substring(PREFIX.length)
            val combined = Base64.decode(base64Data, Base64.NO_WRAP)
            if (combined.size < 17) return null

            val iv = ByteArray(16)
            val cipherText = ByteArray(combined.size - 16)
            System.arraycopy(combined, 0, iv, 0, 16)
            System.arraycopy(combined, 16, cipherText, 0, cipherText.size)

            val keySpec = SecretKeySpec(masterKey, "AES")
            val ivSpec = IvParameterSpec(iv)
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)

            val decryptedBytes = cipher.doFinal(cipherText)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
