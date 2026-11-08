import test from 'node:test';
import assert from 'node:assert/strict';
import net from 'node:net';
import fs from 'node:fs/promises';
import path from 'node:path';
import { TransferSender, TransferReceiver } from '../src/core/transfer_engine.js';
import { IntegrityManager } from '../src/core/integrity_manager.js';
import { HistoryStore } from '../src/core/history_store.js';

const TEST_BASE = path.resolve('tmp/recursive_history_test');
const SENDER_DIR = path.join(TEST_BASE, 'sender');
const RECEIVER_DIR = path.join(TEST_BASE, 'receiver');

test('Recursive Folder Streaming: Préservation intégrale de l\'arborescence sans compression', async () => {
  await fs.rm(TEST_BASE, { recursive: true, force: true });
  await fs.mkdir(SENDER_DIR, { recursive: true });
  await fs.mkdir(RECEIVER_DIR, { recursive: true });

  // Créer une structure de dossiers imbriqués
  const rootFolder = path.join(SENDER_DIR, 'ProjetAlpha');
  const subFolder1 = path.join(rootFolder, 'docs');
  const subFolder2 = path.join(rootFolder, 'assets', 'images');
  await fs.mkdir(subFolder1, { recursive: true });
  await fs.mkdir(subFolder2, { recursive: true });

  const file1 = path.join(rootFolder, 'README.md');
  const file2 = path.join(subFolder1, 'spec.txt');
  const file3 = path.join(subFolder2, 'logo.bin');
  const file4 = path.join(subFolder1, 'empty.txt');

  await fs.writeFile(file1, 'Documentation de ProjetAlpha');
  await fs.writeFile(file2, 'Spécifications techniques du transfert récursif');
  const binData = Buffer.alloc(1024 * 512, 0x33); // 512 Ko
  await fs.writeFile(file3, binData);
  await fs.writeFile(file4, ''); // Fichier vide de 0 octet

  const hash1 = await IntegrityManager.computeFileHash(file1);
  const hash2 = await IntegrityManager.computeFileHash(file2);
  const hash3 = await IntegrityManager.computeFileHash(file3);
  const hash4 = await IntegrityManager.computeFileHash(file4);

  // Serveur récepteur
  const server = net.createServer();
  await new Promise(res => server.listen(0, '127.0.0.1', res));
  const port = server.address().port;

  let receiverAllPromise;
  server.on('connection', socket => {
    const receiver = new TransferReceiver(socket, RECEIVER_DIR);
    receiverAllPromise = receiver.receiveAll();
  });

  // Client émetteur
  const clientSocket = net.connect({ host: '127.0.0.1', port });
  await new Promise(res => clientSocket.once('connect', res));

  const sender = new TransferSender(clientSocket);
  const sendStats = await sender.sendDirectory(rootFolder);
  clientSocket.end();

  assert.equal(sendStats.filesCount, 4);
  assert.equal(sendStats.results.length, 4);

  const receivedFiles = await receiverAllPromise;
  assert.equal(receivedFiles.length, 4);

  // Vérifier l'existence et l'intégrité de tous les fichiers dans RECEIVER_DIR
  const recFile1 = path.join(RECEIVER_DIR, 'ProjetAlpha', 'README.md');
  const recFile2 = path.join(RECEIVER_DIR, 'ProjetAlpha', 'docs', 'spec.txt');
  const recFile3 = path.join(RECEIVER_DIR, 'ProjetAlpha', 'assets', 'images', 'logo.bin');
  const recFile4 = path.join(RECEIVER_DIR, 'ProjetAlpha', 'docs', 'empty.txt');

  assert.equal(await fs.readFile(recFile1, 'utf8'), 'Documentation de ProjetAlpha');
  assert.equal(await fs.readFile(recFile2, 'utf8'), 'Spécifications techniques du transfert récursif');
  assert.deepEqual(await fs.readFile(recFile3), binData);
  assert.equal(await fs.readFile(recFile4, 'utf8'), '');

  assert.equal(await IntegrityManager.computeFileHash(recFile1), hash1);
  assert.equal(await IntegrityManager.computeFileHash(recFile2), hash2);
  assert.equal(await IntegrityManager.computeFileHash(recFile3), hash3);
  assert.equal(await IntegrityManager.computeFileHash(recFile4), hash4);

  if (server.closeAllConnections) server.closeAllConnections();
  await new Promise(res => server.close(res));
  await fs.rm(TEST_BASE, { recursive: true, force: true });
});

test('Duplicate Resolution: RENAME, OVERWRITE et SKIP', async () => {
  const DUP_BASE = path.resolve('tmp/duplicate_test');
  const DUP_SRC = path.join(DUP_BASE, 'src');
  const DUP_DST = path.join(DUP_BASE, 'dst');
  await fs.rm(DUP_BASE, { recursive: true, force: true });
  await fs.mkdir(DUP_SRC, { recursive: true });
  await fs.mkdir(DUP_DST, { recursive: true });

  const testFile = path.join(DUP_SRC, 'rapport.txt');
  await fs.writeFile(testFile, 'Version 2 (Nouvelle)');

  // Cas 1 : RENAME ("Conserver les deux")
  const existingDst = path.join(DUP_DST, 'rapport.txt');
  await fs.writeFile(existingDst, 'Version 1 (Initiale)');

  const server1 = net.createServer();
  await new Promise(res => server1.listen(0, '127.0.0.1', res));
  const port1 = server1.address().port;

  let receiverPromise1;
  server1.on('connection', socket => {
    socket.on('end', () => socket.destroy());
    const receiver = new TransferReceiver(socket, DUP_DST, { conflictResolution: 'RENAME' });
    receiverPromise1 = receiver.start();
  });

  const client1 = net.connect({ host: '127.0.0.1', port: port1 });
  await new Promise(res => client1.once('connect', res));

  const sender1 = new TransferSender(client1);
  await sender1.sendFile(testFile);
  const result1 = await receiverPromise1;

  assert.ok(result1.filePath.includes('rapport (1).txt'));
  assert.equal(await fs.readFile(existingDst, 'utf8'), 'Version 1 (Initiale)');
  assert.equal(await fs.readFile(result1.filePath, 'utf8'), 'Version 2 (Nouvelle)');

  client1.destroy();
  if (server1.closeAllConnections) server1.closeAllConnections();
  await new Promise(res => server1.close(res));

  // Cas 2 : SKIP ("Ignorer")
  const server2 = net.createServer();
  await new Promise(res => server2.listen(0, '127.0.0.1', res));
  const port2 = server2.address().port;

  let skipReceiverPromise;
  server2.on('connection', socket => {
    socket.on('end', () => socket.destroy());
    const receiver = new TransferReceiver(socket, DUP_DST, { conflictResolution: 'SKIP' });
    skipReceiverPromise = receiver.start();
  });

  const clientSkip = net.connect({ host: '127.0.0.1', port: port2 });
  await new Promise(res => clientSkip.once('connect', res));

  const senderSkip = new TransferSender(clientSkip);
  const skipResult = await senderSkip.sendFile(testFile);
  assert.equal(skipResult.skipped, true);
  await skipReceiverPromise;

  clientSkip.destroy();
  if (server2.closeAllConnections) server2.closeAllConnections();
  await new Promise(res => server2.close(res));

  // Cas 3 : OVERWRITE ("Écraser")
  const server3 = net.createServer();
  await new Promise(res => server3.listen(0, '127.0.0.1', res));
  const port3 = server3.address().port;

  let overwritePromise;
  server3.on('connection', socket => {
    socket.on('end', () => socket.destroy());
    const receiver = new TransferReceiver(socket, DUP_DST, { conflictResolution: 'OVERWRITE' });
    overwritePromise = receiver.start();
  });

  const clientOverwrite = net.connect({ host: '127.0.0.1', port: port3 });
  await new Promise(res => clientOverwrite.once('connect', res));

  const senderOverwrite = new TransferSender(clientOverwrite);
  await senderOverwrite.sendFile(testFile);
  await overwritePromise;

  assert.equal(await fs.readFile(existingDst, 'utf8'), 'Version 2 (Nouvelle)');

  clientOverwrite.destroy();
  if (server3.closeAllConnections) server3.closeAllConnections();
  await new Promise(res => server3.close(res));

  // Cas 4 : onConflict interactif (Callback prompt)
  const server4 = net.createServer();
  await new Promise(res => server4.listen(0, '127.0.0.1', res));
  const port4 = server4.address().port;

  let interactivePromise;
  let onConflictCalled = false;
  server4.on('connection', socket => {
    socket.on('end', () => socket.destroy());
    const receiver = new TransferReceiver(socket, DUP_DST, {
      conflictResolution: 'PROMPT',
      onConflict: async (conflictInfo) => {
        onConflictCalled = true;
        assert.equal(conflictInfo.fileName, 'rapport.txt');
        return 'RENAME';
      }
    });
    interactivePromise = receiver.start();
  });

  const client4 = net.connect({ host: '127.0.0.1', port: port4 });
  await new Promise(res => client4.once('connect', res));

  const sender4 = new TransferSender(client4);
  await sender4.sendFile(testFile);
  const result4 = await interactivePromise;

  assert.equal(onConflictCalled, true);
  assert.ok(result4.filePath.includes('rapport (2).txt'));

  client4.destroy();
  if (server4.closeAllConnections) server4.closeAllConnections();
  await new Promise(res => server4.close(res));

  await fs.rm(DUP_BASE, { recursive: true, force: true });
});

test('HistoryStore: Persistance, métriques et détection des transferts interrompus', async () => {
  const HIST_DIR = path.resolve('tmp/history_test');
  const histFile = path.join(HIST_DIR, 'transfers_history.json');
  await fs.rm(HIST_DIR, { recursive: true, force: true });
  await fs.mkdir(HIST_DIR, { recursive: true });

  // 1. Initialement vide
  let history = await HistoryStore.loadHistory(histFile);
  assert.deepEqual(history, []);

  // 2. Ajouter un transfert réussi avec métriques
  const entry1 = await HistoryStore.addHistoryEntry(histFile, {
    fileName: 'video.mp4',
    fileSize: 10485760, // 10 Mo
    durationMs: 200,
    peerName: 'Pixel 8',
    status: 'COMPLETED',
    isDirectory: false,
    sha256: 'deadbeef1234',
  });

  assert.equal(entry1.fileName, 'video.mp4');
  assert.equal(entry1.averageSpeedBytesPerSec, 52428800); // 10 Mo / 0.2s = 50 Mo/s

  // 3. Ajouter un transfert interrompu
  const entry2 = await HistoryStore.addHistoryEntry(histFile, {
    fileName: 'backup.zip',
    fileSize: 50000000,
    durationMs: 500,
    peerName: 'Laptop-PC',
    status: 'INTERRUPTED',
    isDirectory: false,
  });

  // 4. Détection des transferts interrompus pour le bandeau de crash
  const interrupted = await HistoryStore.getInterruptedTransfers(histFile);
  assert.equal(interrupted.length, 1);
  assert.equal(interrupted[0].fileName, 'backup.zip');

  // 5. Mise à jour de statut (reprise réussie)
  await HistoryStore.updateHistoryEntry(histFile, entry2.id, { status: 'COMPLETED' });
  const interruptedAfter = await HistoryStore.getInterruptedTransfers(histFile);
  assert.equal(interruptedAfter.length, 0);

  // 6. Nettoyage
  await HistoryStore.clearHistory(histFile);
  assert.deepEqual(await HistoryStore.loadHistory(histFile), []);

  await fs.rm(HIST_DIR, { recursive: true, force: true });
});
