import { EventEmitter } from 'node:events';
import fs from 'node:fs/promises';
import path from 'node:path';
import { MESSAGE_TYPES, CHUNK_CONFIG } from '../protocol/constants.js';
import { encodeFrame, FrameParser } from '../protocol/framing.js';
import { ChunkManager } from './chunk_manager.js';
import { IntegrityManager } from './integrity_manager.js';
import { ResumeManager } from './resume_manager.js';
import { BackpressureController } from './backpressure_controller.js';
import { sanitizeAndResolvePath } from '../security/sanitizer.js';
import { encryptChunk, decryptChunk } from './crypto_stream.js';

/**
 * Session Émettrice FastDrop
 */
export class TransferSender extends EventEmitter {
  /**
   * @param {import('node:net').Socket|import('node:stream').Duplex} socket
   */
  constructor(socket) {
    super();
    this.socket = socket;
    this.parser = new FrameParser();
    this.socket.on('data', chunk => this.parser.push(chunk));
    this.socket.on('error', err => this.emit('error', err));
    this.backpressure = new BackpressureController(CHUNK_CONFIG.MAX_WINDOW_SIZE);
  }

  /**
   * Scanne récursivement un répertoire et extrait tous les fichiers avec chemin relatif
   * @param {string} dirPath
   * @param {string} [baseDir]
   * @returns {Promise<Array<{ absolutePath: string, relativePath: string, size: number }>>}
   */
  static async scanDirectory(dirPath, baseDir = path.dirname(dirPath)) {
    const results = [];
    const entries = await fs.readdir(dirPath, { withFileTypes: true });

    for (const entry of entries) {
      const fullPath = path.join(dirPath, entry.name);
      if (entry.isDirectory()) {
        const subResults = await TransferSender.scanDirectory(fullPath, baseDir);
        results.push(...subResults);
      } else if (entry.isFile()) {
        const stat = await fs.stat(fullPath);
        const rel = path.relative(baseDir, fullPath).replace(/\\/g, '/');
        results.push({
          absolutePath: fullPath,
          relativePath: rel,
          size: stat.size,
        });
      }
    }
    return results;
  }

  /**
   * Envoie un répertoire complet sans compression préalable en préservant l'arborescence
   * @param {string} dirPath
   * @param {object} [options]
   * @returns {Promise<{ transferId: string, durationMs: number, filesCount: number, bytesSent: number, results: Array<object> }>}
   */
  async sendDirectory(dirPath, options = {}) {
    const startTime = Date.now();
    const files = await TransferSender.scanDirectory(dirPath, options.baseDir);
    const results = [];
    let totalBytesSent = 0;
    const transferId = options.transferId || `tr-dir-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`;

    this.emit('status', { stage: 'DIRECTORY_SCANNED', message: `${files.length} fichiers trouvés pour le streaming de dossier.` });

    for (let i = 0; i < files.length; i++) {
      const file = files[i];
      this.emit('status', {
        stage: 'SENDING_FILE',
        fileIndex: i + 1,
        totalFiles: files.length,
        relativePath: file.relativePath,
      });

      const fileResult = await this.sendFile(file.absolutePath, {
        ...options,
        relativePath: file.relativePath,
        transferId: `${transferId}-f${i}`,
      });

      results.push(fileResult);
      if (!fileResult.skipped) {
        totalBytesSent += fileResult.bytesSent || 0;
      }
    }

    return {
      transferId,
      durationMs: Date.now() - startTime,
      filesCount: files.length,
      bytesSent: totalBytesSent,
      results,
    };
  }

  /**
   * Envoie un chemin (fichier ou dossier)
   * @param {string} targetPath
   * @param {object} [options]
   */
  async sendPath(targetPath, options = {}) {
    const stat = await fs.stat(targetPath);
    if (stat.isDirectory()) {
      return this.sendDirectory(targetPath, options);
    }
    return this.sendFile(targetPath, options);
  }

  /**
   * Envoie un fichier complet vers le récepteur
   * @param {string} sourceFilePath - Chemin absolu du fichier source
   * @param {object} [options]
   * @param {string} [options.transferId] - ID de transfert unique (optionnel)
   * @param {string} [options.relativePath] - Chemin relatif préservé (pour streaming de dossier)
   * @param {number} [options.chunkSize] - Taille de bloc (défaut 1 Mo)
   * @returns {Promise<{ transferId: string, durationMs: number, bytesSent: number, sha256: string, skipped?: boolean }>}
   */
  async sendFile(sourceFilePath, options = {}) {
    const startTime = Date.now();
    const stat = await fs.stat(sourceFilePath);
    const totalSize = stat.size;
    const chunkSize = options.chunkSize || CHUNK_CONFIG.DEFAULT_CHUNK_SIZE;
    const transferId = options.transferId || `tr-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`;
    const relativePath = options.relativePath || sourceFilePath.split(/[/\\]/).pop();

    this.emit('status', { stage: 'COMPUTING_SHA256', message: 'Calcul du hash SHA-256 initial...' });
    const sha256 = await IntegrityManager.computeFileHash(sourceFilePath);

    const sessionKey = options.sessionKey || this.sessionKey || null;
    const partition = ChunkManager.getPartitionInfo(totalSize, chunkSize);
    const proposal = {
      transfer_id: transferId,
      file_id: 'f-001',
      relative_path: relativePath,
      size: totalSize,
      chunk_size: chunkSize,
      total_chunks: partition.totalChunks,
      sha256,
      mtime: Math.floor(stat.mtimeMs),
      encrypted: Boolean(sessionKey),
    };

    // 1. Envoyer TRANSFER_PROPOSAL et attendre décision
    const decision = await new Promise((resolve, reject) => {
      const onFrame = ({ type, payload }) => {
        if (type === MESSAGE_TYPES.TRANSFER_DECISION) {
          this.parser.off('frame', onFrame);
          try {
            resolve(JSON.parse(payload.toString('utf8')));
          } catch (e) {
            reject(e);
          }
        } else if (type === MESSAGE_TYPES.TRANSFER_RESUME_REQ) {
          this.parser.off('frame', onFrame);
          try {
            resolve({ resume: true, ...JSON.parse(payload.toString('utf8')) });
          } catch (e) {
            reject(e);
          }
        } else if (type === MESSAGE_TYPES.ERROR_ABORT) {
          this.parser.off('frame', onFrame);
          reject(new Error(`Transfert refusé par le récepteur: ${payload.toString('utf8')}`));
        }
      };

      this.parser.on('frame', onFrame);
      this.socket.write(encodeFrame(MESSAGE_TYPES.TRANSFER_PROPOSAL, proposal));
    });

    if (decision.accepted === false) {
      if (decision.reason === 'FILE_EXISTS_SKIPPED') {
        this.emit('status', { stage: 'SKIPPED', message: 'Fichier ignoré car déjà existant chez le récepteur' });
        return {
          transferId,
          durationMs: 0,
          bytesSent: 0,
          sha256,
          skipped: true,
        };
      }
      if (decision.reason === 'INSUFFICIENT_STORAGE') {
        throw new Error(`Transfert refusé : Espace disque insuffisant sur l'appareil récepteur (Requis: ${decision.required} octets, Disponible: ${decision.available} octets)`);
      }
      throw new Error(`Transfert rejeté : ${decision.reason || 'Raison non spécifiée'}`);
    }

    // Déterminer la liste des chunks à envoyer (tous ou seulement les manquants si reprise)
    let chunksToSend = [];
    if (decision.resume && Array.isArray(decision.missing_chunks)) {
      chunksToSend = decision.missing_chunks;
      this.emit('status', { stage: 'RESUMING', message: `Reprise de transfert : ${chunksToSend.length} blocs restants.` });
    } else {
      for (let i = 0; i < partition.totalChunks; i++) {
        chunksToSend.push(i);
      }
    }

    const fileHandle = await fs.open(sourceFilePath, 'r');
    let totalBytesSent = 0;

    // Gestionnaire des acquittements CHUNK_ACK
    const ackPromises = new Map();
    const onAckFrame = ({ type, payload }) => {
      if (type === MESSAGE_TYPES.CHUNK_ACK) {
        const { chunkIndex, bytesWritten } = ChunkManager.decodeAckPayload(payload);
        this.backpressure.recordAck(chunkIndex);
        totalBytesSent += bytesWritten;
        const progress = {
          chunkIndex,
          totalChunks: partition.totalChunks,
          bytesSent: totalBytesSent,
          totalBytes: totalSize,
          percent: totalSize === 0 ? 100 : Math.min(100, Math.round((totalBytesSent / totalSize) * 100)),
        };
        this.emit('progress', progress);

        if (ackPromises.has(chunkIndex)) {
          ackPromises.get(chunkIndex)();
          ackPromises.delete(chunkIndex);
        }
      }
    };
    this.parser.on('frame', onAckFrame);

    try {
      for (const chunkIndex of chunksToSend) {
        await this.backpressure.waitForDrain();

        const { buffer, offset, bytesRead } = await ChunkManager.readChunk(
          fileHandle,
          chunkIndex,
          chunkSize,
          totalSize
        );

        const dataBuffer = sessionKey ? encryptChunk(buffer, sessionKey, chunkIndex) : buffer;
        const chunkPayload = ChunkManager.encodeChunkPayload(0, chunkIndex, offset, dataBuffer);
        this.backpressure.recordSent(chunkIndex);

        const ackPromise = new Promise(resolve => {
          ackPromises.set(chunkIndex, resolve);
        });

        this.socket.write(encodeFrame(MESSAGE_TYPES.CHUNK_DATA, chunkPayload));
        await ackPromise; // Synchronisation par bloc ou via fenêtre
      }
    } finally {
      await fileHandle.close();
      this.parser.off('frame', onAckFrame);
    }

    // 3. Envoyer TRANSFER_COMPLETE et attendre CHECKSUM_VERIFIED
    await new Promise((resolve, reject) => {
      const onVerifiedFrame = ({ type, payload }) => {
        if (type === MESSAGE_TYPES.CHECKSUM_VERIFIED) {
          this.parser.off('frame', onVerifiedFrame);
          resolve(JSON.parse(payload.toString('utf8')));
        } else if (type === MESSAGE_TYPES.ERROR_ABORT) {
          this.parser.off('frame', onVerifiedFrame);
          reject(new Error(`Échec de vérification finale : ${payload.toString('utf8')}`));
        }
      };

      this.parser.on('frame', onVerifiedFrame);
      this.socket.write(encodeFrame(MESSAGE_TYPES.TRANSFER_COMPLETE, { transfer_id: transferId, file_id: 'f-001' }));
    });

    const durationMs = Date.now() - startTime;
    return {
      transferId,
      durationMs,
      bytesSent: totalBytesSent,
      sha256,
    };
  }
}

/**
 * Session Réceptrice FastDrop
 */
export class TransferReceiver extends EventEmitter {
  /**
   * @param {import('node:net').Socket|import('node:stream').Duplex} socket
   * @param {string} destinationDirectory - Répertoire sécurisé de destination
   * @param {object} [options]
   * @param {'OVERWRITE'|'RENAME'|'SKIP'} [options.conflictResolution='OVERWRITE']
   * @param {Function} [options.onConflict]
   */
  constructor(socket, destinationDirectory, options = {}) {
    super();
    this.socket = socket;
    this.destinationDir = destinationDirectory;
    this.options = options;
    this.conflictResolution = options.conflictResolution || 'OVERWRITE';
    this.onConflict = options.onConflict || null;
    this.sessionKey = options.sessionKey || null;
    this.parser = new FrameParser();
    this.socket.on('data', chunk => this.parser.push(chunk));
    this.socket.on('error', err => this.emit('error', err));
  }

  /**
   * Génère un chemin unique en cas de doublon ("Conserver les deux")
   * @param {string} targetPath
   * @returns {Promise<string>}
   */
  static async resolveUniquePath(targetPath) {
    try {
      await fs.stat(targetPath);
    } catch (err) {
      if (err.code === 'ENOENT') return targetPath;
      throw err;
    }
    const dir = path.dirname(targetPath);
    const ext = path.extname(targetPath);
    const name = path.basename(targetPath, ext);
    let counter = 1;
    while (true) {
      const candidate = path.join(dir, `${name} (${counter})${ext}`);
      try {
        await fs.stat(candidate);
        counter++;
      } catch (err) {
        if (err.code === 'ENOENT') return candidate;
        throw err;
      }
    }
  }

  /**
   * Démarre la réception d'un transfert
   * @returns {Promise<{ transferId: string, filePath: string, size: number, sha256: string }>}
   */
  async start() {
    return new Promise((resolve, reject) => {
      let fileHandle = null;
      let manifest = null;
      let targetFilePath = null;
      let proposal = null;
      let bytesReceived = 0;

      const cleanup = async () => {
        if (fileHandle) {
          await fileHandle.close().catch(() => {});
          fileHandle = null;
        }
      };

      // Purge automatique en tâche de fond des transferts partiels orphelins (> 24h)
      ResumeManager.cleanupExpiredManifests(this.destinationDir).catch(() => {});

      this.parser.on('frame', async ({ type, payload }) => {
        try {
          if (type === MESSAGE_TYPES.CLIPBOARD_SHARE) {
            const clipText = payload.toString('utf8');
            this.emit('clipboard_received', clipText);
            resolve({ clipboard: true, text: clipText });
            return;
          }

          if (type === MESSAGE_TYPES.TRANSFER_PROPOSAL) {
            proposal = JSON.parse(payload.toString('utf8'));

            // 1. Validation de sécurité anti-Path Traversal et création récursive des répertoires parents
            targetFilePath = sanitizeAndResolvePath(proposal.relative_path, this.destinationDir);
            await fs.mkdir(path.dirname(targetFilePath), { recursive: true });

            // 2. Vérification de reprise existante
            const existingManifest = await ResumeManager.loadManifest(this.destinationDir, proposal.transfer_id);
            if (existingManifest && !ResumeManager.isComplete(existingManifest)) {
              manifest = existingManifest;
              const missingChunks = ResumeManager.getMissingChunks(manifest);
              fileHandle = await fs.open(targetFilePath, 'r+'); // Ouvrir fichier existant
              this.socket.write(encodeFrame(MESSAGE_TYPES.TRANSFER_RESUME_REQ, {
                accepted: true,
                transfer_id: proposal.transfer_id,
                missing_chunks: missingChunks,
              }));
              return;
            }

            // 2b. Détection et résolution des conflits de doublons
            let fileExists = false;
            try {
              const st = await fs.stat(targetFilePath);
              fileExists = st.isFile();
            } catch (e) {
              if (e.code !== 'ENOENT') throw e;
            }

            if (fileExists) {
              let resolution = this.conflictResolution;
              if (typeof this.onConflict === 'function') {
                resolution = await this.onConflict({
                  fileName: proposal.relative_path,
                  targetFilePath,
                  size: proposal.size,
                });
              }

              if (resolution === 'SKIP') {
                this.socket.write(encodeFrame(MESSAGE_TYPES.TRANSFER_DECISION, {
                  accepted: false,
                  reason: 'FILE_EXISTS_SKIPPED',
                  transfer_id: proposal.transfer_id,
                }));
                const skipInfo = { transferId: proposal.transfer_id, fileName: proposal.relative_path, skipped: true };
                this.emit('transfer_skipped', skipInfo);
                resolve(skipInfo);
                return;
              } else if (resolution === 'RENAME') {
                targetFilePath = await TransferReceiver.resolveUniquePath(targetFilePath);
              }
            }

            // 2c. Vérification préalable de l'espace disque disponible
            if (typeof fs.statfs === 'function' && proposal.size > 0) {
              try {
                const stats = await fs.statfs(this.destinationDir);
                const availableBytes = stats.bavail * stats.bsize;
                if (availableBytes < proposal.size) {
                  this.socket.write(encodeFrame(MESSAGE_TYPES.TRANSFER_DECISION, {
                    accepted: false,
                    reason: 'INSUFFICIENT_STORAGE',
                    transfer_id: proposal.transfer_id,
                    available: availableBytes,
                    required: proposal.size,
                  }));
                  const rejectInfo = {
                    transferId: proposal.transfer_id,
                    fileName: proposal.relative_path,
                    rejected: true,
                    reason: 'INSUFFICIENT_STORAGE',
                    available: availableBytes,
                    required: proposal.size,
                  };
                  this.emit('transfer_rejected', rejectInfo);
                  resolve(rejectInfo);
                  return;
                }
              } catch (statErr) {
                // Si statfs échoue pour une raison de chemin non monté, on continue
              }
            }

            // 3. Nouveau transfert : allocation initiale
            manifest = await ResumeManager.createManifest(this.destinationDir, proposal, targetFilePath);
            fileHandle = await fs.open(targetFilePath, 'w+');
            if (proposal.size > 0) {
              await fileHandle.truncate(proposal.size); // Pré-allocation de la taille du fichier
            }

            this.socket.write(encodeFrame(MESSAGE_TYPES.TRANSFER_DECISION, {
              accepted: true,
              transfer_id: proposal.transfer_id,
            }));
            this.emit('transfer_started', { transferId: proposal.transfer_id, fileName: proposal.relative_path, filePath: targetFilePath });

          } else if (type === MESSAGE_TYPES.CHUNK_DATA) {
            if (!fileHandle) {
              throw new Error('CHUNK_DATA reçu sans proposition préalable');
            }

            const { fileIndex, chunkIndex, data } = ChunkManager.decodeChunkPayload(payload);
            let rawData = data;
            if (proposal?.encrypted || this.sessionKey) {
              const key = this.sessionKey || this.options.sessionKey;
              if (!key) {
                throw new Error('Chunk chiffré reçu mais aucune clé de session configurée');
              }
              // Validation stricte anti-rejeu et anti-réordonnancement
              rawData = decryptChunk(data, key, chunkIndex);
            }
            const bytesWritten = await ChunkManager.writeChunk(fileHandle, chunkIndex, proposal.chunk_size, rawData);

            // Mettre à jour le manifeste
            await ResumeManager.markChunkReceived(this.destinationDir, proposal.transfer_id, chunkIndex);
            bytesReceived += bytesWritten;

            // Renvoyer l'acquittement CHUNK_ACK
            const ack = ChunkManager.encodeAckPayload(fileIndex, chunkIndex, bytesWritten);
            this.socket.write(encodeFrame(MESSAGE_TYPES.CHUNK_ACK, ack));

            this.emit('progress', {
              chunkIndex,
              totalChunks: proposal.total_chunks,
              bytesReceived,
              totalBytes: proposal.size,
              percent: proposal.size === 0 ? 100 : Math.min(100, Math.round((bytesReceived / proposal.size) * 100)),
            });

          } else if (type === MESSAGE_TYPES.TRANSFER_COMPLETE) {
            await cleanup();

            this.emit('status', { stage: 'VERIFYING_SHA256', message: 'Vérification SHA-256 finale en cours...' });
            const isMatch = await IntegrityManager.verifyFile(targetFilePath, proposal.sha256);

            if (!isMatch) {
              this.socket.write(encodeFrame(MESSAGE_TYPES.ERROR_ABORT, { error: 'Checksum mismatch' }));
              throw new Error(`Échec d'intégrité SHA-256 sur ${targetFilePath}`);
            }

            // Restauration de l'horodatage de modification original (mtime)
            if (typeof proposal?.mtime === 'number' && proposal.mtime > 0) {
              try {
                const timeSec = proposal.mtime / 1000;
                await fs.utimes(targetFilePath, timeSec, timeSec);
              } catch {}
            }

            // Validation réussie, suppression du manifeste de reprise
            await ResumeManager.cleanupManifest(this.destinationDir, proposal.transfer_id);
            this.socket.write(encodeFrame(MESSAGE_TYPES.CHECKSUM_VERIFIED, {
              verified: true,
              transfer_id: proposal.transfer_id,
              sha256: proposal.sha256,
            }));

            const result = {
              transferId: proposal.transfer_id,
              filePath: targetFilePath,
              size: proposal.size,
              sha256: proposal.sha256,
            };
            this.emit('transfer_completed', result);
            resolve(result);

            // Réinitialiser les pointeurs pour un éventuel prochain fichier
            fileHandle = null;
            manifest = null;
            targetFilePath = null;
            proposal = null;
            bytesReceived = 0;
          }
        } catch (err) {
          await cleanup();
          this.socket.write(encodeFrame(MESSAGE_TYPES.ERROR_ABORT, { error: err.message }));
          this.emit('error', err);
          reject(err);
        }
      });
    });
  }

  /**
   * Reçoit tous les fichiers d'une session de streaming (ex: streaming de dossier) jusqu'à la fin de la connexion
   * @returns {Promise<Array<object>>}
   */
  async receiveAll() {
    const receivedFiles = [];
    return new Promise((resolve, reject) => {
      this.on('transfer_completed', (res) => {
        receivedFiles.push(res);
      });
      this.on('transfer_skipped', (res) => {
        receivedFiles.push(res);
      });
      this.on('error', reject);

      const onEnd = () => {
        this.emit('session_completed', receivedFiles);
        resolve(receivedFiles);
      };

      this.socket.once('end', onEnd);
      this.socket.once('close', onEnd);

      this.start().catch(reject);
    });
  }
}

