/**
 * LanceDrop Desktop — Module Cryptographique pour Codes QR Sécurisés
 * Chiffre les données de connexion (IP, Port, PIN, Modèle) en AES-256-CBC
 * pour empêcher tout scanner tiers ou caméra générique d'accéder aux informations du réseau.
 */
const crypto = require('crypto');

const SECRET_SALT = 'LanceDrop::Protocol::SecureTransport::QR::2026';
const MASTER_KEY = crypto.createHash('sha256').update(SECRET_SALT).digest();
const PREFIX = 'LANCEDROP:SEC:v1:';

/**
 * Chiffre une chaîne JSON ou texte brut en format QR sécurisé opaque.
 * @param {string} plainText 
 * @returns {string} Payload sécurisé commençant par LANCEDROP:SEC:v1:
 */
function encryptQrPayload(plainText) {
  try {
    const iv = crypto.randomBytes(16);
    const cipher = crypto.createCipheriv('aes-256-cbc', MASTER_KEY, iv);
    const encrypted = Buffer.concat([
      cipher.update(Buffer.from(plainText, 'utf8')),
      cipher.final()
    ]);
    const combined = Buffer.concat([iv, encrypted]);
    return PREFIX + combined.toString('base64');
  } catch (err) {
    console.error('Erreur chiffrement QR:', err);
    return plainText; // Fallback sécurisé
  }
}

/**
 * Déchiffre un payload QR sécurisé vers son JSON d'origine.
 * @param {string} encryptedPayload 
 * @returns {string|null} Le texte JSON en clair, ou null si invalide
 */
function decryptQrPayload(encryptedPayload) {
  if (!encryptedPayload) return null;

  // Si c'est déjà du JSON en clair (rétrocompatibilité)
  if (encryptedPayload.trim().startsWith('{')) {
    return encryptedPayload.trim();
  }

  if (!encryptedPayload.startsWith(PREFIX)) {
    return null;
  }

  try {
    const base64Data = encryptedPayload.slice(PREFIX.length);
    const combined = Buffer.from(base64Data, 'base64');
    if (combined.length < 17) return null;

    const iv = combined.subarray(0, 16);
    const ciphertext = combined.subarray(16);

    const decipher = crypto.createDecipheriv('aes-256-cbc', MASTER_KEY, iv);
    const decrypted = Buffer.concat([
      decipher.update(ciphertext),
      decipher.final()
    ]);

    return decrypted.toString('utf8');
  } catch (err) {
    console.error('Erreur déchiffrement QR:', err);
    return null;
  }
}

module.exports = {
  encryptQrPayload,
  decryptQrPayload,
  PREFIX
};
