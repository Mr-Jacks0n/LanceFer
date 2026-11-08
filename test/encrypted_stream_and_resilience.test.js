import { test } from 'node:test';
import assert from 'node:assert/strict';
import net from 'node:net';
import fs from 'node:fs/promises';
import path from 'node:path';
import os from 'node:os';
import {
  generateSessionKey,
  normalizeSessionKey,
  encryptChunk,
  decryptChunk,
} from '../src/core/crypto_stream.js';
import { TransferSender, TransferReceiver } from '../src/core/transfer_engine.js';
import { IntegrityManager } from '../src/core/integrity_manager.js';

test('CryptoStream: normalisation et génération de clé de session 256 bits', () => {
  const sessionKeyHex = generateSessionKey();
  assert.equal(sessionKeyHex.length, 64, 'La clé hexadécimale doit faire 64 caractères');
  
  const keyBuf1 = normalizeSessionKey(sessionKeyHex);
  assert.equal(keyBuf1.length, 32, 'Le buffer normalisé doit faire 32 octets (256 bits)');

  const keyBuf2 = normalizeSessionKey('mot_de_passe_simple_ou_pin');
  assert.equal(keyBuf2.length, 32, 'La dérivation SHA-256 doit produire 32 octets');
});

test('CryptoStream: Chiffrement et déchiffrement AES-256-GCM d’un chunk', () => {
  const sessionKey = generateSessionKey();
  const originalData = Buffer.from('Bonjour LanceDrop! Ceci est un transfert haute sécurité AES-256-GCM.');
  
  const encrypted = encryptChunk(originalData, sessionKey, 1);
  assert.notDeepEqual(encrypted, originalData, 'Le chunk chiffré doit différer du texte clair');
  assert.ok(encrypted.length > originalData.length + 27, 'Taille chiffrée = IV(12) + Data + Tag(16)');

  const decrypted = decryptChunk(encrypted, sessionKey);
  assert.deepEqual(decrypted, originalData, 'Le payload déchiffré doit correspondre exactement au texte initial');
});

test('CryptoStream: Anti-tampering — Altération d’un octet détectée et rejetée (Bad Tag)', () => {
  const sessionKey = generateSessionKey();
  const originalData = Buffer.from('Donnée confidentielle non altérable');
  const encrypted = encryptChunk(originalData, sessionKey, 0);

  // Falsification d'un octet dans le payload
  const tampered = Buffer.from(encrypted);
  tampered[tampered.length - 1] ^= 0xFF; // Inverser le dernier bit du tag

  assert.throws(() => {
    decryptChunk(tampered, sessionKey);
  }, /Unsupported state|unable to authenticate|auth/i, 'Toute altération doit être rejetée par GCM');
});

test('TransferEngine: Transfert complet de bout en bout chiffré en AES-256-GCM', async () => {
  const tmpDir = await fs.mkdtemp(path.join(os.tmpdir(), 'lancedrop-enc-test-'));
  const srcFile = path.join(tmpDir, 'secret_document.bin');
  const destDir = path.join(tmpDir, 'received');
  await fs.mkdir(destDir, { recursive: true });

  // Génération d'un fichier source de 250 Ko
  const testData = Buffer.alloc(250 * 1024);
  for (let i = 0; i < testData.length; i++) {
    testData[i] = (i * 31) % 256;
  }
  await fs.writeFile(srcFile, testData);
  const expectedSha256 = await IntegrityManager.computeFileHash(srcFile);

  const sessionKey = generateSessionKey();

  // Démarrage du serveur TCP récepteur avec la sessionKey
  let serverReceiver = null;
  const server = net.createServer((socket) => {
    serverReceiver = new TransferReceiver(socket, destDir, { sessionKey });
    serverReceiver.start().catch((err) => {
      // Ignorer l'erreur attendue si le test ferme le socket
    });
  });

  await new Promise(res => server.listen(0, '127.0.0.1', res));
  const port = server.address().port;

  // Connexion du client émetteur avec la sessionKey
  const clientSocket = net.connect({ host: '127.0.0.1', port });
  await new Promise(res => clientSocket.once('connect', res));

  const sender = new TransferSender(clientSocket);
  const result = await sender.sendFile(srcFile, { sessionKey });

  assert.equal(result.sha256, expectedSha256);
  assert.equal(result.bytesSent, testData.length);

  const receivedFile = path.join(destDir, 'secret_document.bin');
  const receivedData = await fs.readFile(receivedFile);
  assert.deepEqual(receivedData, testData, 'Le fichier reçu déchiffré doit être rigoureusement identique au fichier source');

  clientSocket.end();
  server.close();
  await fs.rm(tmpDir, { recursive: true, force: true });
});

test('TransferEngine: Pré-contrôle d’espace disque disponible (INSUFFICIENT_STORAGE)', async () => {
  const tmpDir = await fs.mkdtemp(path.join(os.tmpdir(), 'lancedrop-disk-test-'));
  const srcFile = path.join(tmpDir, 'huge_file.bin');
  const destDir = path.join(tmpDir, 'received');
  await fs.mkdir(destDir, { recursive: true });

  await fs.writeFile(srcFile, Buffer.from('Petit fichier test'));

  // Serveur avec statfs mocké pour simuler 10 octets libres
  let serverReceiver = null;
  const server = net.createServer((socket) => {
    serverReceiver = new TransferReceiver(socket, destDir);
    serverReceiver.start().catch(() => {});
  });

  await new Promise(res => server.listen(0, '127.0.0.1', res));
  const port = server.address().port;

  // Mock temporaire de fs.statfs
  const origStatfs = fs.statfs;
  fs.statfs = async () => ({
    bavail: 1,
    bsize: 5, // 5 octets disponibles alors que le fichier en fait plus
  });

  const clientSocket = net.connect({ host: '127.0.0.1', port });
  await new Promise(res => clientSocket.once('connect', res));

  const sender = new TransferSender(clientSocket);

  try {
    await assert.rejects(async () => {
      await sender.sendFile(srcFile);
    }, /Espace disque insuffisant/i);
  } finally {
    fs.statfs = origStatfs; // Restaurer statfs original
    clientSocket.end();
    server.close();
    await fs.rm(tmpDir, { recursive: true, force: true });
  }
});
