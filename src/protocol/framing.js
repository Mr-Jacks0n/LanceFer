import { MAGIC_BYTES, PROTOCOL_VERSION, HEADER_SIZE } from './constants.js';
import { EventEmitter } from 'node:events';

/**
 * Encode un message selon le format de cadrage FDSP v1 :
 * [2 octets 'FD'] [1 octet Version (0x01)] [1 octet MessageType] [4 octets Payload Length BE] [N octets Payload]
 *
 * @param {number} messageType
 * @param {Buffer|object|string} payload
 * @returns {Buffer}
 */
export function encodeFrame(messageType, payload = Buffer.alloc(0)) {
  let payloadBuf;
  if (Buffer.isBuffer(payload)) {
    payloadBuf = payload;
  } else if (typeof payload === 'object') {
    payloadBuf = Buffer.from(JSON.stringify(payload), 'utf8');
  } else if (typeof payload === 'string') {
    payloadBuf = Buffer.from(payload, 'utf8');
  } else {
    payloadBuf = Buffer.alloc(0);
  }

  const frame = Buffer.allocUnsafe(HEADER_SIZE + payloadBuf.length);
  // Magic bytes 'FD'
  frame[0] = MAGIC_BYTES[0];
  frame[1] = MAGIC_BYTES[1];
  // Version
  frame[2] = PROTOCOL_VERSION;
  // Message Type
  frame[3] = messageType;
  // Payload length (Big Endian uint32)
  frame.writeUInt32BE(payloadBuf.length, 4);
  // Payload
  if (payloadBuf.length > 0) {
    payloadBuf.copy(frame, HEADER_SIZE);
  }

  return frame;
}

/**
 * Parser de trames orienté flux.
 * Reconstitue les trames complètes à partir de fragments TCP successifs.
 */
export class FrameParser extends EventEmitter {
  constructor() {
    super();
    this.buffer = Buffer.alloc(0);
  }

  /**
   * Injecte des données reçues du socket réseau
   * @param {Buffer} chunk
   */
  push(chunk) {
    if (!chunk || chunk.length === 0) return;

    this.buffer = Buffer.concat([this.buffer, chunk]);

    while (this.buffer.length >= HEADER_SIZE) {
      // Vérification des magic bytes
      if (this.buffer[0] !== MAGIC_BYTES[0] || this.buffer[1] !== MAGIC_BYTES[1]) {
        // Mauvaise signature, chercher le prochain index 'FD'
        const nextMagic = this.buffer.indexOf(MAGIC_BYTES, 1);
        if (nextMagic === -1) {
          this.emit('error', new Error('Protocole invalide : signature magique introuvable'));
          this.buffer = Buffer.alloc(0);
          return;
        }
        this.buffer = this.buffer.subarray(nextMagic);
        continue;
      }

      const version = this.buffer[2];
      if (version !== PROTOCOL_VERSION) {
        this.emit('error', new Error(`Version de protocole non supportée : ${version}`));
        this.buffer = this.buffer.subarray(1);
        continue;
      }

      const type = this.buffer[3];
      const payloadLength = this.buffer.readUInt32BE(4);
      const totalFrameSize = HEADER_SIZE + payloadLength;

      if (this.buffer.length < totalFrameSize) {
        // Trame incomplète, attendre les prochains octets
        break;
      }

      const payload = this.buffer.subarray(HEADER_SIZE, totalFrameSize);
      this.buffer = this.buffer.subarray(totalFrameSize);

      this.emit('frame', { type, payload });
    }
  }

  /**
   * Réinitialise le buffer interne
   */
  reset() {
    this.buffer = Buffer.alloc(0);
  }
}
