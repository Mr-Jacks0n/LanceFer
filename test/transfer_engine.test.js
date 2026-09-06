import test from 'node:test';
import assert from 'node:assert/strict';
import net from 'node:net';
import fs from 'node:fs/promises';
import path from 'node:path';
import { TransferSender, TransferReceiver } from '../src/core/transfer_engine.js';
import { IntegrityManager } from '../src/core/integrity_manager.js';

const TMP_BASE = path.resolve('tmp/e2e_transfer');
const SRC_DIR = path.join(TMP_BASE, 'sender_files');
const DST_DIR = path.join(TMP_BASE, 'receiver_downloads');

test('TransferEngine: Transfert E2E via Socket TCP local avec validation SHA-256', async () => {
  await fs.mkdir(SRC_DIR, { recursive: true });
  await fs.mkdir(DST_DIR, { recursive: true });

  const testFileName = 'document_test.bin';
  const srcFilePath = path.join(SRC_DIR, testFileName);

  // Générer un fichier source de 2.5 Mo (5 blocs de 512 Ko)
  const fileSize = 2.5 * 1024 * 1024; // 2 621 440 octets
  const testData = Buffer.alloc(fileSize);
  for (let i = 0; i < fileSize; i++) {
    testData[i] = (i * 31) % 256;
  }
  await fs.writeFile(srcFilePath, testData);

  const originalSha256 = await IntegrityManager.computeFileHash(srcFilePath);

  // 1. Démarrer le serveur TCP récepteur
  const server = net.createServer();
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const port = server.address().port;

  let receiverPromise;
  server.on('connection', socket => {
    const receiver = new TransferReceiver(socket, DST_DIR);
    receiverPromise = receiver.start();
  });

  // 2. Connecter le client émetteur
  const clientSocket = net.connect({ host: '127.0.0.1', port });
  await new Promise(resolve => clientSocket.on('connect', resolve));

  const sender = new TransferSender(clientSocket);
  const progressEvents = [];
  sender.on('progress', p => progressEvents.push(p.percent));

  // 3. Exécuter l'envoi avec des blocs de 512 Ko
  const sendResult = await sender.sendFile(srcFilePath, { chunkSize: 512 * 1024 });
  const receiveResult = await receiverPromise;

  // 4. Vérifications strictes (GO CONDITION)
  assert.equal(sendResult.sha256, originalSha256);
  assert.equal(receiveResult.sha256, originalSha256);
  assert.equal(receiveResult.size, fileSize);

  const receivedData = await fs.readFile(receiveResult.filePath);
  assert.equal(receivedData.length, fileSize);
  assert.deepEqual(receivedData, testData);

  // Vérifier que la progression a bien atteint 100%
  assert.ok(progressEvents.includes(100));

  // Fermeture des sockets
  clientSocket.end();
  await new Promise(resolve => server.close(resolve));

  // Nettoyage
  await fs.rm(TMP_BASE, { recursive: true, force: true });
});
