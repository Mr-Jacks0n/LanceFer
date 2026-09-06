/**
 * FastDrop Protocol (FDSP v1) — Constantes et Définitions
 * Conforme aux spécifications de docs/architecture/fastdrop_protocol_v1.md
 */

export const MAGIC_BYTES = Buffer.from([0x46, 0x44]); // ASCII 'FD'
export const PROTOCOL_VERSION = 0x01;

export const MESSAGE_TYPES = {
  HELLO_REQUEST: 0x01,
  HELLO_RESPONSE: 0x02,
  PAIR_REQUEST: 0x03,
  PAIR_RESPONSE: 0x04,

  TRANSFER_PROPOSAL: 0x10,
  TRANSFER_DECISION: 0x11,
  TRANSFER_RESUME_REQ: 0x12,

  CHUNK_DATA: 0x20,
  CHUNK_ACK: 0x21,

  TRANSFER_COMPLETE: 0x30,
  CHECKSUM_VERIFIED: 0x31,

  TRANSFER_PAUSE: 0x40,
  TRANSFER_CANCEL: 0x41,

  ERROR_ABORT: 0xff,
};

export const REVERSE_MESSAGE_TYPES = Object.fromEntries(
  Object.entries(MESSAGE_TYPES).map(([k, v]) => [v, k])
);

// Configuration des blocs de données
export const CHUNK_CONFIG = {
  DEFAULT_CHUNK_SIZE: 1024 * 1024,      // 1 Mo
  MIN_CHUNK_SIZE: 64 * 1024,            // 64 Ko
  MAX_CHUNK_SIZE: 4 * 1024 * 1024,      // 4 Mo
  MAX_WINDOW_SIZE: 8,                   // 8 chunks en vol max (8 Mo max en RAM)
};

export const HEADER_SIZE = 8; // 2 (magic) + 1 (ver) + 1 (type) + 4 (length)
