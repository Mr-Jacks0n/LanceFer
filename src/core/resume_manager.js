import fs from 'node:fs/promises';
import path from 'node:path';

export class ResumeManager {
  /**
   * Obtient le chemin du fichier manifeste local
   * @param {string} destinationDir
   * @param {string} transferId
   * @returns {string}
   */
  static getManifestPath(destinationDir, transferId) {
    return path.join(destinationDir, `.fastdrop_resume_${transferId}.json`);
  }

  /**
   * Initialise et persiste un nouveau manifeste de reprise
   * @param {string} destinationDir
   * @param {object} transferProposal
   * @param {string} destinationFilePath
   * @returns {Promise<object>}
   */
  static async createManifest(destinationDir, transferProposal, destinationFilePath) {
    const manifestPath = this.getManifestPath(destinationDir, transferProposal.transfer_id);

    const manifest = {
      transfer_id: transferProposal.transfer_id,
      file_id: transferProposal.file_id || 'f-001',
      file_name: transferProposal.relative_path,
      destination_path: destinationFilePath,
      total_size: transferProposal.size,
      chunk_size: transferProposal.chunk_size,
      total_chunks: transferProposal.total_chunks,
      expected_sha256: transferProposal.sha256,
      received_chunks: [], // Tableau d'index des chunks validés
      created_at: new Date().toISOString(),
      updated_at: new Date().toISOString(),
    };

    await fs.writeFile(manifestPath, JSON.stringify(manifest, null, 2), 'utf8');
    return manifest;
  }

  /**
   * Charge un manifeste existant pour reprise
   * @param {string} destinationDir
   * @param {string} transferId
   * @returns {Promise<object|null>}
   */
  static async loadManifest(destinationDir, transferId) {
    const manifestPath = this.getManifestPath(destinationDir, transferId);
    try {
      const content = await fs.readFile(manifestPath, 'utf8');
      return JSON.parse(content);
    } catch {
      return null;
    }
  }

  /**
   * Enregistre un chunk comme reçu et persiste le manifeste
   * @param {string} destinationDir
   * @param {string} transferId
   * @param {number} chunkIndex
   * @returns {Promise<void>}
   */
  static async markChunkReceived(destinationDir, transferId, chunkIndex) {
    const manifest = await this.loadManifest(destinationDir, transferId);
    if (!manifest) return;

    if (!manifest.received_chunks.includes(chunkIndex)) {
      manifest.received_chunks.push(chunkIndex);
      manifest.updated_at = new Date().toISOString();
      const manifestPath = this.getManifestPath(destinationDir, transferId);
      await fs.writeFile(manifestPath, JSON.stringify(manifest, null, 2), 'utf8');
    }
  }

  /**
   * Calcule la liste des chunks encore manquants pour finaliser le transfert
   * @param {object} manifest
   * @returns {number[]} - Liste des index de chunks à retransmettre
   */
  static getMissingChunks(manifest) {
    const receivedSet = new Set(manifest.received_chunks);
    const missing = [];
    for (let i = 0; i < manifest.total_chunks; i++) {
      if (!receivedSet.has(i)) {
        missing.push(i);
      }
    }
    return missing;
  }

  /**
   * Vérifie si tous les chunks ont été reçus
   * @param {object} manifest
   * @returns {boolean}
   */
  static isComplete(manifest) {
    return manifest.received_chunks.length >= manifest.total_chunks;
  }

  /**
   * Supprime le fichier manifeste une fois le transfert 100% validé
   * @param {string} destinationDir
   * @param {string} transferId
   * @returns {Promise<void>}
   */
  static async cleanupManifest(destinationDir, transferId) {
    const manifestPath = this.getManifestPath(destinationDir, transferId);
    try {
      await fs.unlink(manifestPath);
    } catch {
      // Ignorer si le fichier n'existe plus
    }
  }
}
