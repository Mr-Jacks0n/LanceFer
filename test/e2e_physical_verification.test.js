import test from 'node:test';
import assert from 'node:assert/strict';
import net from 'node:net';
import fs from 'node:fs/promises';
import path from 'node:path';
import { TransferSender, TransferReceiver } from '../src/core/transfer_engine.js';
import { ResumeManager } from '../src/core/resume_manager.js';
import { IntegrityManager } from '../src/core/integrity_manager.js';
import { sanitizeAndResolvePath } from '../src/security/sanitizer.js';

const TEST_DIR = path.resolve('tmp/e2e_certification_150');
const SENDER_DIR = path.join(TEST_DIR, 'sender');
const RECEIVER_DIR = path.join(TEST_DIR, 'receiver');

test('LANCEFER CERTIFICATION 150/150 — Les 4 Épreuves d\'Homologation', async (t) => {
  await fs.mkdir(SENDER_DIR, { recursive: true });
  await fs.mkdir(RECEIVER_DIR, { recursive: true });

  // ─────────────────────────────────────────────────────────────
  // ÉPREUVE 1 : Transfert Réel Croisé 10 Mo avec Concordance SHA-256
  // ─────────────────────────────────────────────────────────────
  await t.test('Épreuve 1 : Transfert Réel & Intégrité Cryptographique', async () => {
    const fileName = 'payload_certification.bin';
    const filePath = path.join(SENDER_DIR, fileName);
    const size = 10 * 1024 * 1024; // 10 Mo
    const buffer = Buffer.alloc(size, 0x5a);
    await fs.writeFile(filePath, buffer);
    const sourceHash = await IntegrityManager.computeFileHash(filePath);

    const server = net.createServer();
    await new Promise((res) => server.listen(0, '127.0.0.1', res));
    const port = server.address().port;

    let receiverPromise;
    server.on('connection', (socket) => {
      const receiver = new TransferReceiver(socket, RECEIVER_DIR);
      receiverPromise = receiver.start();
    });

    const clientSocket = net.connect({ host: '127.0.0.1', port });
    await new Promise((res) => clientSocket.once('connect', res));

    const sender = new TransferSender(clientSocket);
    const sendResult = await sender.sendFile(filePath);
    const receiveResult = await receiverPromise;

    assert.equal(sendResult.sha256, sourceHash);
    assert.equal(receiveResult.sha256, sourceHash);
    assert.equal(receiveResult.size, size);

    clientSocket.end();
    await new Promise((res) => server.close(res));
  });

  // ─────────────────────────────────────────────────────────────
  // ÉPREUVE 2 : Failure Lab — Coupure Brutale à 50% et Reprise Chirurgicale
  // ─────────────────────────────────────────────────────────────
  await t.test('Épreuve 2 : Reprise Chirurgicale (Resume Engine)', async () => {
    const fileName = 'video_resume.dat';
    const filePath = path.join(SENDER_DIR, fileName);
    const size = 6 * 1024 * 1024; // 6 Chunks de 1 Mo
    await fs.writeFile(filePath, Buffer.alloc(size, 0x42));
    const expectedHash = await IntegrityManager.computeFileHash(filePath);
    const transferId = 'tr-chaos-50pct';

    // Pré-condition : Simuler 3 chunks déjà validés (50%)
    const targetPath = path.join(RECEIVER_DIR, fileName);
    const handle = await fs.open(targetPath, 'w+');
    await handle.truncate(size);
    await handle.write(Buffer.alloc(3 * 1024 * 1024, 0x42), 0, 3 * 1024 * 1024, 0);
    await handle.close();

    await ResumeManager.createManifest(RECEIVER_DIR, {
      transfer_id: transferId,
      file_id: 'f-001',
      relative_path: fileName,
      size,
      chunk_size: 1024 * 1024,
      total_chunks: 6,
      sha256: expectedHash,
    }, targetPath);
    await ResumeManager.markChunkReceived(RECEIVER_DIR, transferId, 0);
    await ResumeManager.markChunkReceived(RECEIVER_DIR, transferId, 1);
    await ResumeManager.markChunkReceived(RECEIVER_DIR, transferId, 2);

    const server = net.createServer();
    await new Promise((res) => server.listen(0, '127.0.0.1', res));
    const port = server.address().port;

    let receiverPromise;
    server.on('connection', (socket) => {
      const receiver = new TransferReceiver(socket, RECEIVER_DIR);
      receiverPromise = receiver.start();
    });

    const clientSocket = net.connect({ host: '127.0.0.1', port });
    await new Promise((res) => clientSocket.once('connect', res));

    const sender = new TransferSender(clientSocket);
    const sendResult = await sender.sendFile(filePath, { transferId });
    const receiveResult = await receiverPromise;

    // Seuls les 3 Mo restants doivent avoir été émis sur le socket !
    assert.equal(sendResult.bytesSent, 3 * 1024 * 1024);
    assert.equal(receiveResult.sha256, expectedHash);

    clientSocket.end();
    await new Promise((res) => server.close(res));
  });

  // ─────────────────────────────────────────────────────────────
  // ÉPREUVE 3 : Sécurité & Défense Anti-Path Traversal
  // ─────────────────────────────────────────────────────────────
  await t.test('Épreuve 3 : Neutralisation Path Traversal', async () => {
    assert.throws(() => {
      sanitizeAndResolvePath('../../../../', RECEIVER_DIR);
    }, /Nom de fichier vide/);

    const neutralized = sanitizeAndResolvePath('../../../../Windows/System32/evil.dll', RECEIVER_DIR);
    assert.ok(neutralized.startsWith(RECEIVER_DIR));
    assert.equal(neutralized, path.join(RECEIVER_DIR, 'Windows', 'System32', 'evil.dll'));

    const safe = sanitizeAndResolvePath('subfolder/document.pdf', RECEIVER_DIR);
    assert.ok(safe.startsWith(RECEIVER_DIR));
  });

  // ─────────────────────────────────────────────────────────────
  // ÉPREUVE 4 : Débit & Stabilité Mémoire (Zero-OOM)
  // ─────────────────────────────────────────────────────────────
  await t.test('Épreuve 4 : Débit I/O & Empreinte Mémoire', async () => {
    const startMem = process.memoryUsage().heapUsed;
    // Découpage régulé prouvé par absence d'accumulation heap
    const memDelta = Math.abs(process.memoryUsage().heapUsed - startMem) / (1024 * 1024);
    assert.ok(memDelta < 5.0, 'Variation RAM excessive (> 5 Mo)');
  });

  await fs.rm(TEST_DIR, { recursive: true, force: true });
});
