import fs from 'node:fs/promises';
import path from 'node:path';

/**
 * Magasin sécurisé des appareils approuvés (TrustStore)
 * Conforme aux règles d'audit de docs/architecture/security_model.md
 */
export class TrustStore {
  /**
   * @param {string} storageDirectory - Répertoire où persister le magasin
   * @param {string} [fileName] - Nom du fichier (défaut: .fastdrop_trusted_devices.json)
   */
  constructor(storageDirectory, fileName = '.fastdrop_trusted_devices.json') {
    this.storagePath = path.join(storageDirectory, fileName);
    this.devices = new Map(); // deviceId -> TrustedDeviceInfo
    this.loaded = false;
  }

  /**
   * Charge la liste des appareils approuvés depuis le disque
   * @returns {Promise<void>}
   */
  async load() {
    try {
      const data = await fs.readFile(this.storagePath, 'utf8');
      const list = JSON.parse(data);
      this.devices.clear();
      if (Array.isArray(list)) {
        for (const item of list) {
          if (item.deviceId) {
            this.devices.set(item.deviceId, item);
          }
        }
      }
    } catch {
      // Fichier absent ou vide, démarrer avec une liste vide
      this.devices.clear();
    }
    this.loaded = true;
  }

  /**
   * Sauvegarde l'état du magasin sur disque
   * @returns {Promise<void>}
   */
  async save() {
    const list = Array.from(this.devices.values());
    await fs.mkdir(path.dirname(this.storagePath), { recursive: true });
    await fs.writeFile(this.storagePath, JSON.stringify(list, null, 2), 'utf8');
  }

  /**
   * Enregistre un nouvel appareil approuvé après validation mutuelle
   * @param {object} deviceInfo
   * @param {string} deviceInfo.deviceId
   * @param {string} deviceInfo.deviceName
   * @param {string} deviceInfo.publicKeyFingerprint
   * @param {string} [deviceInfo.platform]
   * @returns {Promise<void>}
   */
  async addTrustedDevice(deviceInfo) {
    if (!this.loaded) await this.load();

    const record = {
      deviceId: deviceInfo.deviceId,
      deviceName: deviceInfo.deviceName,
      publicKeyFingerprint: deviceInfo.publicKeyFingerprint,
      platform: deviceInfo.platform || 'unknown',
      pairedAt: new Date().toISOString(),
      status: 'trusted',
    };

    this.devices.set(deviceInfo.deviceId, record);
    await this.save();
  }

  /**
   * Vérifie si un appareil est approuvé et non révoqué
   * @param {string} deviceId
   * @param {string} [publicKeyFingerprint] - Empreinte de clé à comparer si fournie
   * @returns {Promise<boolean>}
   */
  async isTrusted(deviceId, publicKeyFingerprint) {
    if (!this.loaded) await this.load();

    const record = this.devices.get(deviceId);
    if (!record || record.status !== 'trusted') {
      return false;
    }

    if (publicKeyFingerprint) {
      return record.publicKeyFingerprint.toLowerCase() === publicKeyFingerprint.toLowerCase();
    }

    return true;
  }

  /**
   * Révoque l'autorisation d'un appareil (sans supprimer son historique)
   * @param {string} deviceId
   * @returns {Promise<boolean>}
   */
  async revokeDevice(deviceId) {
    if (!this.loaded) await this.load();
    const record = this.devices.get(deviceId);
    if (record) {
      record.status = 'revoked';
      record.revokedAt = new Date().toISOString();
      await this.save();
      return true;
    }
    return false;
  }

  /**
   * Supprime définitivement un appareil du magasin
   * @param {string} deviceId
   * @returns {Promise<boolean>}
   */
  async removeDevice(deviceId) {
    if (!this.loaded) await this.load();
    const deleted = this.devices.delete(deviceId);
    if (deleted) {
      await this.save();
    }
    return deleted;
  }

  /**
   * Renvoie la liste complète des appareils connus
   * @returns {Array<object>}
   */
  getDevices() {
    return Array.from(this.devices.values());
  }

  /**
   * Réinitialise le magasin
   * @returns {Promise<void>}
   */
  async clear() {
    this.devices.clear();
    try {
      await fs.unlink(this.storagePath);
    } catch {
      // Ignorer
    }
  }
}
