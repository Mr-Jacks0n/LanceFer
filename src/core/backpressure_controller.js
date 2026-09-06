import { EventEmitter } from 'node:events';
import { CHUNK_CONFIG } from '../protocol/constants.js';

export class BackpressureController extends EventEmitter {
  constructor(maxInFlight = CHUNK_CONFIG.MAX_WINDOW_SIZE) {
    super();
    this.maxInFlight = maxInFlight;
    this.inFlightChunks = new Set();
  }

  /**
   * Indique si un nouveau chunk peut être envoyé
   * @returns {boolean}
   */
  canSend() {
    return this.inFlightChunks.size < this.maxInFlight;
  }

  /**
   * Enregistre l'envoi d'un chunk
   * @param {number} chunkIndex
   */
  recordSent(chunkIndex) {
    this.inFlightChunks.add(chunkIndex);
  }

  /**
   * Enregistre la confirmation (ACK) d'un chunk reçu et écrit
   * @param {number} chunkIndex
   */
  recordAck(chunkIndex) {
    this.inFlightChunks.delete(chunkIndex);
    if (this.canSend()) {
      this.emit('drain');
    }
  }

  /**
   * Attend de manière asynchrone qu'une place se libère dans la fenêtre de transmission
   * @returns {Promise<void>}
   */
  async waitForDrain() {
    if (this.canSend()) return;
    return new Promise(resolve => {
      this.once('drain', resolve);
    });
  }

  /**
   * Nombre actuel de blocs en cours de transit sans acquittement
   */
  get inFlightCount() {
    return this.inFlightChunks.size;
  }

  /**
   * Réinitialise le contrôleur
   */
  reset() {
    this.inFlightChunks.clear();
  }
}
