import { EventEmitter } from 'node:events';
import fs from 'node:fs/promises';
import { MESSAGE_TYPES, CHUNK_CONFIG } from '../protocol/constants.js';
import { encodeFrame, FrameParser } from '../protocol/framing.js';
import { ChunkManager } from './chunk_manager.js';
import { IntegrityManager } from './integrity_manager.js';
import { ResumeManager } from './resume_manager.js';
import { BackpressureController } from './backpressure_controller.js';
import { sanitizeAndResolvePath } from '../security/sanitizer.js';

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
   * Envoie un fichier complet vers le récepteur
   * @param {string} sourceFilePath - Chemin absolu du fichier source
   * @param {object} [options]
   * @param {string} [options.transferId] - ID de transfert unique (optionnel)
   * @param {number} [options.chunkSize] - Taille de bloc (défaut 1 Mo)
   * @returns {Promise<{ transferId: string, durationMs: number, bytesSent: number, sha256: string }>}
   */
  async sendFile(sourceFilePath, options = {}) {
    const startTime = Date.now();
    const stat = await fs.stat(sourceFilePath);
    const totalSize = stat.size;
    const chunkSize = options.chunkSize || CHUNK_CONFIG.DEFAULT_CHUNK_SIZE;
    const transferId = options.transferId || `tr-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`;

    this.emit('status', { stage: 'COMPUTING_SHA256', message: 'Calcul du hash SHA-256 initial...' });
    const sha256 = await IntegrityManager.computeFileHash(sourceFilePath);

    const partition = ChunkManager.getPartitionInfo(totalSize, chunkSize);
    const proposal = {
      transfer_id: transferId,
      file_id: 'f-001',
      relative_path: sourceFilePath.split(/[/\\]/).pop(),
      size: totalSize,
      chunk_size: chunkSize,
      total_chunks: partition.totalChunks,
      sha256,
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

        const chunkPayload = ChunkManager.encodeChunkPayload(0, chunkIndex, offset, buffer);
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
   */
  constructor(socket, destinationDirectory) {
    super();
    this.socket = socket;
    this.destinationDir = destinationDirectory;
    this.parser = new FrameParser();
    this.socket.on('data', chunk => this.parser.push(chunk));
    this.socket.on('error', err => this.emit('error', err));
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

      this.parser.on('frame', async ({ type, payload }) => {
        try {
          if (type === MESSAGE_TYPES.TRANSFER_PROPOSAL) {
            proposal = JSON.parse(payload.toString('utf8'));

            // 1. Validation de sécurité anti-Path Traversal
            targetFilePath = sanitizeAndResolvePath(proposal.relative_path, this.destinationDir);
            await fs.mkdir(this.destinationDir, { recursive: true });

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
            this.emit('transfer_started', { transferId: proposal.transfer_id, fileName: proposal.relative_path });

          } else if (type === MESSAGE_TYPES.CHUNK_DATA) {
            if (!fileHandle) {
              throw new Error('CHUNK_DATA reçu sans proposition préalable');
            }

            const { fileIndex, chunkIndex, data } = ChunkManager.decodeChunkPayload(payload);
            const bytesWritten = await ChunkManager.writeChunk(fileHandle, chunkIndex, proposal.chunk_size, data);

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
          }
        } catch (err) {
          await cleanup();
          this.socket.write(encodeFrame(MESSAGE_TYPES.ERROR_ABORT, { error: err.message }));
          reject(err);
        }
      });
    });
  }
}
