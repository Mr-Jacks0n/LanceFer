import test from 'node:test';
import assert from 'node:assert/strict';
import net from 'node:net';
import fs from 'node:fs/promises';
import path from 'node:path';
import { TransferSender, TransferReceiver } from '../src/core/transfer_engine.js';
import { ResumeManager } from '../src/core/resume_manager.js';
import { IntegrityManager } from '../src/core/integrity_manager.js';

const TMP_BASE = path.resolve('tmp/resume_e2e');
const SRC_DIR = path.join(TMP_BASE, 'src');
const DST_DIR = path.join(TMP_BASE, 'dst');

test('ResumeEngine: Simulation de coupure réseau brutale et reprise sans perte', async () => {
  await fs.mkdir(SRC_DIR, { recursive: true });
  await fs.mkdir(DST_DIR, { recursive: true });

  const testFileName = 'video_interrompue.mp4';
  const srcFilePath = path.join(SRC_DIR, testFileName);

  // Fichier de 3 Mo (6 blocs de 512 Ko)
  const fileSize = 3 * 1024 * 1024;
  const testData = Buffer.alloc(fileSize);
  for (let i = 0; i < fileSize; i++) {
    testData[i] = (i * 17) % 256;
  }
  await fs.writeFile(srcFilePath, testData);
  const expectedSha256 = await IntegrityManager.computeFileHash(srcFilePath);

  const transferId = 'tr-interrupted-001';

  // 1. Simuler un premier transfert partiel où les blocs 0 et 1 sont déjà reçus (par exemple 33% du fichier)
  const partialProposal = {
    transfer_id: transferId,
    file_id: 'f-001',
    relative_path: testFileName,
    size: fileSize,
    chunk_size: 512 * 1024,
    total_chunks: 6,
    sha256: expectedSha256,
  };

  const targetFilePath = path.join(DST_DIR, testFileName);
  // Pré-créer le fichier avec les 2 premiers blocs
  const partialHandle = await fs.open(targetFilePath, 'w+');
  await partialHandle.truncate(fileSize);
  await partialHandle.write(testData.subarray(0, 1024 * 1024), 0, 1024 * 1024, 0);
  await partialHandle.close();

  // Créer le manifeste partiel indiquant que les chunks 0 et 1 sont déjà validés
  await ResumeManager.createManifest(DST_DIR, partialProposal, targetFilePath);
  await ResumeManager.markChunkReceived(DST_DIR, transferId, 0);
  await ResumeManager.markChunkReceived(DST_DIR, transferId, 1);

  // 2. Démarrer le serveur TCP récepteur pour la REPRISE
  const server = net.createServer();
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const port = server.address().port;

  let receiverPromise;
  server.on('connection', socket => {
    const receiver = new TransferReceiver(socket, DST_DIR);
    receiverPromise = receiver.start();
  });

  // 3. Connecter le client émetteur
  const clientSocket = net.connect({ host: '127.0.0.1', port });
  await new Promise(resolve => clientSocket.on('connect', resolve));

  const sender = new TransferSender(clientSocket);

  // 4. L'émetteur envoie avec le même transferId
  const sendResult = await sender.sendFile(srcFilePath, {
    transferId,
    chunkSize: 512 * 1024,
  });

  const receiveResult = await receiverPromise;

  // 5. Vérifier que seuls les 4 blocs restants ont été renvoyés (2 Mo au lieu de 3 Mo)
  assert.equal(sendResult.bytesSent, 4 * 512 * 1024); // Exactement 2 Mo
  assert.equal(receiveResult.sha256, expectedSha256);

  // Vérifier l'intégrité finale du fichier réassemblé
  const finalFile = await fs.readFile(targetFilePath);
  assert.equal(finalFile.length, fileSize);
  assert.deepEqual(finalFile, testData);

  // Vérifier que le manifeste de reprise temporaire a bien été nettoyé après le succès
  const manifestLeft = await ResumeManager.loadManifest(DST_DIR, transferId);
  assert.equal(manifestLeft, null);

  clientSocket.end();
  await new Promise(resolve => server.close(resolve));
  await fs.rm(TMP_BASE, { recursive: true, force: true });
});
