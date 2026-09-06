import fs from 'node:fs/promises';
import { CHUNK_CONFIG } from '../protocol/constants.js';

export class ChunkManager {
  /**
   * Calcule les métadonnées de partitionnement d'un fichier
   * @param {number} fileSize - Taille totale en octets
   * @param {number} chunkSize - Taille d'un bloc (par défaut 1 Mo)
   * @returns {{ totalChunks: number, chunkSize: number, lastChunkSize: number }}
   */
  static getPartitionInfo(fileSize, chunkSize = CHUNK_CONFIG.DEFAULT_CHUNK_SIZE) {
    if (fileSize <= 0) {
      return { totalChunks: 1, chunkSize: 0, lastChunkSize: 0 };
    }
    const totalChunks = Math.ceil(fileSize / chunkSize);
    const lastChunkSize = fileSize % chunkSize === 0 ? chunkSize : fileSize % chunkSize;
    return { totalChunks, chunkSize, lastChunkSize };
  }

  /**
   * Lit un bloc précis d'un fichier à partir de son index
   * @param {fs.FileHandle} fileHandle
   * @param {number} chunkIndex
   * @param {number} chunkSize
   * @param {number} totalFileSize
   * @returns {Promise<{ buffer: Buffer, offset: number, bytesRead: number }>}
   */
  static async readChunk(fileHandle, chunkIndex, chunkSize, totalFileSize) {
    const offset = chunkIndex * chunkSize;
    const remaining = totalFileSize - offset;
    const sizeToRead = Math.min(chunkSize, Math.max(0, remaining));

    const buffer = Buffer.allocUnsafe(sizeToRead);
    const { bytesRead } = await fileHandle.read(buffer, 0, sizeToRead, offset);

    return {
      buffer: buffer.subarray(0, bytesRead),
      offset,
      bytesRead,
    };
  }

  /**
   * Écrit un bloc de données directement à l'offset cible dans le fichier de destination
   * @param {fs.FileHandle} fileHandle
   * @param {number} chunkIndex
   * @param {number} chunkSize
   * @param {Buffer} data
   * @returns {Promise<number>} - Nombre d'octets écrits
   */
  static async writeChunk(fileHandle, chunkIndex, chunkSize, data) {
    const offset = chunkIndex * chunkSize;
    const { bytesWritten } = await fileHandle.write(data, 0, data.length, offset);
    return bytesWritten;
  }

  /**
   * Encode le payload binaire d'un message CHUNK_DATA
   * Format :
   * [FileIndex: uint16 (2o)] [Reserved: uint16 (2o)]
   * [ChunkIndex: uint32 BE (4o)]
   * [Offset: BigUInt64 BE (8o)]
   * [Data: N octets]
   *
   * @param {number} fileIndex
   * @param {number} chunkIndex
   * @param {number|BigInt} offset
   * @param {Buffer} dataBuffer
   * @returns {Buffer}
   */
  static encodeChunkPayload(fileIndex, chunkIndex, offset, dataBuffer) {
    const header = Buffer.allocUnsafe(16);
    header.writeUInt16BE(fileIndex, 0);
    header.writeUInt16BE(0, 2); // Reserved
    header.writeUInt32BE(chunkIndex, 4);
    header.writeBigUInt64BE(BigInt(offset), 8);

    return Buffer.concat([header, dataBuffer]);
  }

  /**
   * Décode le payload binaire d'un message CHUNK_DATA
   * @param {Buffer} payloadBuffer
   * @returns {{ fileIndex: number, chunkIndex: number, offset: BigInt, data: Buffer }}
   */
  static decodeChunkPayload(payloadBuffer) {
    if (payloadBuffer.length < 16) {
      throw new Error('Payload CHUNK_DATA trop court (< 16 octets)');
    }

    const fileIndex = payloadBuffer.readUInt16BE(0);
    const chunkIndex = payloadBuffer.readUInt32BE(4);
    const offset = payloadBuffer.readBigUInt64BE(8);
    const data = payloadBuffer.subarray(16);

    return { fileIndex, chunkIndex, offset, data };
  }

  /**
   * Encode le payload d'un acquittement CHUNK_ACK (16 octets fixes)
   * @param {number} fileIndex
   * @param {number} chunkIndex
   * @param {number} bytesWritten
   * @returns {Buffer}
   */
  static encodeAckPayload(fileIndex, chunkIndex, bytesWritten) {
    const ack = Buffer.alloc(16);
    ack.writeUInt16BE(fileIndex, 0);
    ack.writeUInt16BE(0, 2);
    ack.writeUInt32BE(chunkIndex, 4);
    ack.writeUInt32BE(bytesWritten, 8);
    return ack;
  }

  /**
   * Décode le payload d'un CHUNK_ACK
   * @param {Buffer} ackBuffer
   * @returns {{ fileIndex: number, chunkIndex: number, bytesWritten: number }}
   */
  static decodeAckPayload(ackBuffer) {
    if (ackBuffer.length < 12) {
      throw new Error('Payload CHUNK_ACK invalide (< 12 octets)');
    }
    const fileIndex = ackBuffer.readUInt16BE(0);
    const chunkIndex = ackBuffer.readUInt32BE(4);
    const bytesWritten = ackBuffer.readUInt32BE(8);
    return { fileIndex, chunkIndex, bytesWritten };
  }
}
