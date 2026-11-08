/**
 * LanceDrop Core — CryptoStreamManager
 * Fournit le chiffrement authentifié (AEAD) AES-256-GCM de chaque chunk FDSP
 * Format binaire standardisé compatible 100% Java / Android :
 * [IV: 12 octets] + [Ciphertext: N octets] + [AuthTag: 16 octets]
 */
import crypto from 'crypto';

export const IV_LENGTH = 12;      // 96-bit IV standard pour AES-GCM
export const AUTH_TAG_LENGTH = 16; // 128-bit Authentication Tag

/**
 * Normalise une clé de session en Buffer de 32 octets (AES-256).
 * @param {Buffer|string} key 
 * @returns {Buffer}
 */
export function normalizeSessionKey(key) {
  if (Buffer.isBuffer(key)) {
    if (key.length === 32) return key;
    return crypto.createHash('sha256').update(key).digest();
  }
  if (typeof key === 'string') {
    const trimmed = key.trim();
    if (trimmed.length === 64 && /^[0-9a-fA-F]+$/.test(trimmed)) {
      return Buffer.from(trimmed, 'hex');
    }
    return crypto.createHash('sha256').update(trimmed, 'utf8').digest();
  }
  throw new Error('Type de clé de session non supporté');
}

/**
 * Génère une clé de session éphémère cryptographiquement forte de 256 bits.
 * @returns {string} Clé au format hexadécimal (64 caractères)
 */
export function generateSessionKey() {
  return crypto.randomBytes(32).toString('hex');
}

/**
 * Chiffre un chunk de données avec AES-256-GCM.
 * Structure produite : [IV: 12 octets] + [Ciphertext: N octets] + [AuthTag: 16 octets]
 * @param {Buffer} plainChunk Le buffer brut du chunk
 * @param {Buffer|string} sessionKey La clé de session 256-bit
 * @param {number} [chunkIndex] Index du chunk optionnel (utilisé pour l'IV)
 * @returns {Buffer} Le buffer chiffré et authentifié
 */
export function encryptChunk(plainChunk, sessionKey, chunkIndex = 0) {
  const key = normalizeSessionKey(sessionKey);
  
  // Génération de l'IV (12 octets) : 8 octets aléatoires + 4 octets chunkIndex
  const iv = crypto.randomBytes(IV_LENGTH);
  if (typeof chunkIndex === 'number' && chunkIndex >= 0) {
    iv.writeUInt32BE(chunkIndex >>> 0, 8);
  }

  const cipher = crypto.createCipheriv('aes-256-gcm', key, iv);
  const ciphertext = Buffer.concat([
    cipher.update(plainChunk),
    cipher.final()
  ]);
  const authTag = cipher.getAuthTag();

  return Buffer.concat([iv, ciphertext, authTag]);
}

/**
 * Déchiffre et authentifie un chunk reçu.
 * Structure attendue : [IV: 12 octets] + [Ciphertext: N octets] + [AuthTag: 16 octets]
 * @param {Buffer} encryptedChunk Le buffer chiffré reçu
 * @param {Buffer|string} sessionKey La clé de session 256-bit
 * @param {number|null} [expectedChunkIndex] Index attendu du chunk (vérification anti-rejeu)
 * @returns {Buffer} Le buffer brut d'origine
 * @throws {Error} Si le tag d'authentification est invalide, payload corrompu ou index réordonné
 */
export function decryptChunk(encryptedChunk, sessionKey, expectedChunkIndex = null) {
  const key = normalizeSessionKey(sessionKey);
  const minLength = IV_LENGTH + AUTH_TAG_LENGTH;

  if (!Buffer.isBuffer(encryptedChunk) || encryptedChunk.length < minLength) {
    throw new Error(`Trame chiffrée trop courte (${encryptedChunk?.length || 0} octets, min ${minLength})`);
  }

  const iv = encryptedChunk.subarray(0, IV_LENGTH);

  // Vérification cryptographique anti-rejeu et anti-réordonnancement de chunks
  if (typeof expectedChunkIndex === 'number' && expectedChunkIndex >= 0) {
    const embeddedChunkIndex = iv.readUInt32BE(8);
    if (embeddedChunkIndex !== expectedChunkIndex) {
      throw new Error(`Altération ou réordonnancement de bloc détecté (Index attendu: ${expectedChunkIndex}, Reçu dans IV: ${embeddedChunkIndex})`);
    }
  }

  const ciphertext = encryptedChunk.subarray(IV_LENGTH, encryptedChunk.length - AUTH_TAG_LENGTH);
  const authTag = encryptedChunk.subarray(encryptedChunk.length - AUTH_TAG_LENGTH);

  const decipher = crypto.createDecipheriv('aes-256-gcm', key, iv);
  decipher.setAuthTag(authTag);

  return Buffer.concat([
    decipher.update(ciphertext),
    decipher.final()
  ]);
}
