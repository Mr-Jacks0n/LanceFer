import net from 'node:net';
import fs from 'node:fs/promises';
import path from 'node:path';
import { TransferSender, TransferReceiver } from '../src/core/transfer_engine.js';
import { IntegrityManager } from '../src/core/integrity_manager.js';

const BENCH_DIR = path.resolve('tmp/benchmark');
const SRC_DIR = path.join(BENCH_DIR, 'source');
const DST_DIR = path.join(BENCH_DIR, 'dest');

async function runBenchmarkForSize(sizeMb) {
  console.log(`\n========================================`);
  console.log(`BENCHMARK FASTDROP TRANSFER CORE : ${sizeMb} MB`);
  console.log(`========================================`);

  await fs.mkdir(SRC_DIR, { recursive: true });
  await fs.mkdir(DST_DIR, { recursive: true });

  const fileName = `bench_${sizeMb}mb.dat`;
  const filePath = path.join(SRC_DIR, fileName);
  const totalBytes = sizeMb * 1024 * 1024;

  console.log(`Génération du fichier de test (${sizeMb} Mo)...`);
  const chunkBuffer = Buffer.alloc(1024 * 1024);
  for (let i = 0; i < chunkBuffer.length; i++) {
    chunkBuffer[i] = (i * 13) % 256;
  }

  const handle = await fs.open(filePath, 'w');
  for (let i = 0; i < sizeMb; i++) {
    await handle.write(chunkBuffer, 0, chunkBuffer.length);
  }
  await handle.close();

  const originalSha256 = await IntegrityManager.computeFileHash(filePath);

  // Serveur récepteur TCP
  const server = net.createServer();
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const port = server.address().port;

  let receiverPromise;
  server.on('connection', socket => {
    const receiver = new TransferReceiver(socket, DST_DIR);
    receiverPromise = receiver.start();
  });

  // Client émetteur
  const clientSocket = net.connect({ host: '127.0.0.1', port });
  await new Promise(resolve => clientSocket.on('connect', resolve));

  const sender = new TransferSender(clientSocket);

  const startMem = process.memoryUsage().heapUsed;
  const startTime = Date.now();

  const sendResult = await sender.sendFile(filePath, { chunkSize: 1024 * 1024 });
  const receiveResult = await receiverPromise;

  const durationSec = (Date.now() - startTime) / 1000;
  const speedMbPerSec = (sizeMb / durationSec).toFixed(2);
  const endMem = process.memoryUsage().heapUsed;
  const ramDeltaMb = ((endMem - startMem) / (1024 * 1024)).toFixed(2);

  clientSocket.end();
  await new Promise(resolve => server.close(resolve));

  const isHashValid = receiveResult.sha256 === originalSha256;

  console.log(`Résultats :`);
  console.log(`- Durée totale : ${durationSec.toFixed(3)} s`);
  console.log(`- Débit effectif : ${speedMbPerSec} Mo/s`);
  console.log(`- Variation RAM : ${ramDeltaMb} Mo`);
  console.log(`- Intégrité SHA-256 : ${isHashValid ? 'VALIDE (100% IDENTIQUE)' : 'ECHEC'}`);

  return {
    sizeMb,
    durationSec,
    speedMbPerSec: parseFloat(speedMbPerSec),
    ramDeltaMb: parseFloat(ramDeltaMb),
    sha256Valid: isHashValid,
  };
}

async function main() {
  try {
    const metrics = [];
    metrics.push(await runBenchmarkForSize(10));
    metrics.push(await runBenchmarkForSize(50));

    // Sauvegarder dans .lancefer/metrics.json
    const metricsPath = path.resolve('.lancefer/metrics.json');
    await fs.writeFile(metricsPath, JSON.stringify({
      timestamp: new Date().toISOString(),
      system: 'Windows x64 / Node.js',
      benchmarks: metrics,
    }, null, 2));

    console.log(`\nMétriques enregistrées avec succès dans .lancefer/metrics.json`);
    await fs.rm(BENCH_DIR, { recursive: true, force: true });
  } catch (err) {
    console.error('Erreur benchmark :', err);
    process.exit(1);
  }
}

main();
