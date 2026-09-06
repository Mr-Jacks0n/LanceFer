import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import { ChunkManager } from '../src/core/chunk_manager.js';

const TMP_DIR = path.resolve('tmp/test_chunk');

test('ChunkManager: calcul correct du partitionnement', () => {
  // Fichier de 2.5 Mo avec blocs de 1 Mo -> 3 blocs
  const p1 = ChunkManager.getPartitionInfo(2500000, 1000000);
  assert.equal(p1.totalChunks, 3);
  assert.equal(p1.chunkSize, 1000000);
  assert.equal(p1.lastChunkSize, 500000);

  // Fichier exact multiple
  const p2 = ChunkManager.getPartitionInfo(2000000, 1000000);
  assert.equal(p2.totalChunks, 2);
  assert.equal(p2.lastChunkSize, 1000000);
});

test('ChunkManager: encodage et décodage du payload binaire CHUNK_DATA', () => {
  const dummyData = Buffer.from('ceci-est-un-bloc-binaire-de-test', 'utf8');
  const encoded = ChunkManager.encodeChunkPayload(1, 42, 42000000, dummyData);

  const decoded = ChunkManager.decodeChunkPayload(encoded);
  assert.equal(decoded.fileIndex, 1);
  assert.equal(decoded.chunkIndex, 42);
  assert.equal(decoded.offset, 42000000n);
  assert.deepEqual(decoded.data, dummyData);
});

test('ChunkManager: encodage et décodage de CHUNK_ACK', () => {
  const encoded = ChunkManager.encodeAckPayload(2, 99, 1048576);
  const decoded = ChunkManager.decodeAckPayload(encoded);

  assert.equal(decoded.fileIndex, 2);
  assert.equal(decoded.chunkIndex, 99);
  assert.equal(decoded.bytesWritten, 1048576);
});

test('ChunkManager: lecture et écriture asynchrone par blocs', async () => {
  await fs.mkdir(TMP_DIR, { recursive: true });
  const testFile = path.join(TMP_DIR, 'source.bin');
  const targetFile = path.join(TMP_DIR, 'dest.bin');

  // Créer un fichier source de 500 Ko avec un contenu prévisible
  const testContent = Buffer.alloc(500 * 1024);
  for (let i = 0; i < testContent.length; i++) {
    testContent[i] = i % 256;
  }
  await fs.writeFile(testFile, testContent);

  const srcHandle = await fs.open(testFile, 'r');
  const dstHandle = await fs.open(targetFile, 'w+');

  const chunkSize = 128 * 1024; // 128 Ko
  const partition = ChunkManager.getPartitionInfo(testContent.length, chunkSize);

  for (let i = 0; i < partition.totalChunks; i++) {
    const chunk = await ChunkManager.readChunk(srcHandle, i, chunkSize, testContent.length);
    await ChunkManager.writeChunk(dstHandle, i, chunkSize, chunk.buffer);
  }

  await srcHandle.close();
  await dstHandle.close();

  // Vérifier que le fichier de destination est identique
  const writtenContent = await fs.readFile(targetFile);
  assert.equal(writtenContent.length, testContent.length);
  assert.deepEqual(writtenContent, testContent);

  // Nettoyage
  await fs.rm(TMP_DIR, { recursive: true, force: true });
});
