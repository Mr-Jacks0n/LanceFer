import crypto from 'node:crypto';
import fs from 'node:fs';

export class IntegrityManager {
  /**
   * Calcule l'empreinte SHA-256 d'un fichier en streaming (zéro explosion mémoire)
   * @param {string} filePath - Chemin absolu du fichier
   * @returns {Promise<string>} - Hachage hexadécimal SHA-256
   */
  static async computeFileHash(filePath) {
    return new Promise((resolve, reject) => {
      const hash = crypto.createHash('sha256');
      const stream = fs.createReadStream(filePath, { highWaterMark: 1024 * 1024 });

      stream.on('data', chunk => {
        hash.update(chunk);
      });

      stream.on('end', () => {
        resolve(hash.digest('hex'));
      });

      stream.on('error', err => {
        reject(err);
      });
    });
  }

  /**
   * Calcule le SHA-256 d'un Buffer en mémoire
   * @param {Buffer} buffer
   * @returns {string}
   */
  static computeBufferHash(buffer) {
    return crypto.createHash('sha256').update(buffer).digest('hex');
  }

  /**
   * Vérifie qu'un fichier correspond au hash attendu
   * @param {string} filePath
   * @param {string} expectedHash
   * @returns {Promise<boolean>}
   */
  static async verifyFile(filePath, expectedHash) {
    const actualHash = await this.computeFileHash(filePath);
    return actualHash.toLowerCase() === expectedHash.toLowerCase();
  }
}
