import fs from 'node:fs/promises';
import path from 'node:path';

/**
 * Gestionnaire persistant de l'historique et des métriques de transfert
 */
export class HistoryStore {
  /**
   * Charge la liste des transferts depuis le fichier JSON
   * @param {string} filePath
   * @returns {Promise<Array<object>>}
   */
  static async loadHistory(filePath) {
    try {
      const data = await fs.readFile(filePath, 'utf8');
      const parsed = JSON.parse(data);
      return Array.isArray(parsed) ? parsed : [];
    } catch (err) {
      if (err.code === 'ENOENT') {
        return [];
      }
      console.warn('[HistoryStore] Erreur lecture historique:', err.message);
      return [];
    }
  }

  /**
   * Sauvegarde la liste complète des transferts
   * @param {string} filePath
   * @param {Array<object>} history
   */
  static async saveHistory(filePath, history) {
    try {
      await fs.mkdir(path.dirname(filePath), { recursive: true });
      const tempPath = `${filePath}.tmp.${Date.now()}`;
      await fs.writeFile(tempPath, JSON.stringify(history, null, 2), 'utf8');
      await fs.rename(tempPath, filePath);
    } catch (err) {
      console.error('[HistoryStore] Erreur sauvegarde historique:', err);
      throw err;
    }
  }

  /**
   * Ajoute une nouvelle entrée d'historique avec métriques
   * @param {string} filePath
   * @param {object} entry
   * @returns {Promise<object>}
   */
  static async addHistoryEntry(filePath, entry) {
    const history = await this.loadHistory(filePath);
    const newEntry = {
      id: entry.id || `tr-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`,
      fileName: entry.fileName || 'inconnu',
      fileSize: entry.fileSize || entry.bytesSent || entry.bytesReceived || 0,
      peerName: entry.peerName || 'Inconnu',
      timestamp: entry.timestamp || Date.now(),
      isIncoming: Boolean(entry.isIncoming),
      sha256Checksum: entry.sha256Checksum || entry.sha256 || null,
      filePath: entry.filePath || null,
      durationMs: entry.durationMs || 0,
      averageSpeedBytesPerSec: entry.averageSpeedBytesPerSec || (entry.durationMs > 0 ? ((entry.fileSize || 0) / (entry.durationMs / 1000)) : 0),
      peakSpeedBytesPerSec: entry.peakSpeedBytesPerSec || entry.averageSpeedBytesPerSec || 0,
      status: entry.status || 'COMPLETED',
      isDirectory: Boolean(entry.isDirectory),
    };

    // Mettre à jour si ID existant ou ajouter en tête
    const existingIndex = history.findIndex(h => h.id === newEntry.id);
    if (existingIndex >= 0) {
      history[existingIndex] = { ...history[existingIndex], ...newEntry };
    } else {
      history.unshift(newEntry);
    }

    // Limiter la taille maximale de l'historique (500 éléments max)
    if (history.length > 500) {
      history.length = 500;
    }

    await this.saveHistory(filePath, history);
    return newEntry;
  }

  /**
   * Met à jour une entrée existante
   * @param {string} filePath
   * @param {string} id
   * @param {object} updates
   * @returns {Promise<object|null>}
   */
  static async updateHistoryEntry(filePath, id, updates) {
    const history = await this.loadHistory(filePath);
    const index = history.findIndex(h => h.id === id);
    if (index >= 0) {
      history[index] = { ...history[index], ...updates };
      await this.saveHistory(filePath, history);
      return history[index];
    }
    return null;
  }

  /**
   * Récupère les transferts interrompus pour la reprise après crash
   * @param {string} filePath
   * @returns {Promise<Array<object>>}
   */
  static async getInterruptedTransfers(filePath) {
    const history = await this.loadHistory(filePath);
    return history.filter(h => h.status === 'INTERRUPTED');
  }

  /**
   * Vide l'historique complet
   * @param {string} filePath
   */
  static async clearHistory(filePath) {
    await this.saveHistory(filePath, []);
  }
}
