import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import { ResumeManager } from '../src/core/resume_manager.js';

const TMP_DIR = path.resolve('tmp/test_resume');

test('ResumeManager: cycle de vie du manifeste de reprise', async () => {
  await fs.mkdir(TMP_DIR, { recursive: true });

  const transferProposal = {
    transfer_id: 'tr-test-123',
    file_id: 'f-001',
    relative_path: 'video.mp4',
    size: 4000000,
    chunk_size: 1000000,
    total_chunks: 4,
    sha256: 'abc123sha256',
  };

  const targetPath = path.join(TMP_DIR, 'video.mp4');

  // 1. Création
  const manifest = await ResumeManager.createManifest(TMP_DIR, transferProposal, targetPath);
  assert.equal(manifest.transfer_id, 'tr-test-123');
  assert.equal(manifest.total_chunks, 4);
  assert.deepEqual(manifest.received_chunks, []);

  // 2. Chunks reçus
  await ResumeManager.markChunkReceived(TMP_DIR, 'tr-test-123', 0);
  await ResumeManager.markChunkReceived(TMP_DIR, 'tr-test-123', 1);

  // 3. Rechargement et vérification des manquants
  const loaded = await ResumeManager.loadManifest(TMP_DIR, 'tr-test-123');
  assert.deepEqual(loaded.received_chunks, [0, 1]);

  const missing = ResumeManager.getMissingChunks(loaded);
  assert.deepEqual(missing, [2, 3]);
  assert.equal(ResumeManager.isComplete(loaded), false);

  // 4. Complétion
  await ResumeManager.markChunkReceived(TMP_DIR, 'tr-test-123', 2);
  await ResumeManager.markChunkReceived(TMP_DIR, 'tr-test-123', 3);

  const completed = await ResumeManager.loadManifest(TMP_DIR, 'tr-test-123');
  assert.equal(ResumeManager.isComplete(completed), true);

  // 5. Nettoyage
  await ResumeManager.cleanupManifest(TMP_DIR, 'tr-test-123');
  const cleaned = await ResumeManager.loadManifest(TMP_DIR, 'tr-test-123');
  assert.equal(cleaned, null);

  await fs.rm(TMP_DIR, { recursive: true, force: true });
});
