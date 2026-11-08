import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import os from 'node:os';
import net from 'node:net';
import {
  generateSessionKey,
  encryptChunk,
  decryptChunk,
} from '../src/core/crypto_stream.js';
import { ResumeManager } from '../src/core/resume_manager.js';
import { DiscoveryEngine } from '../src/discovery/discovery_engine.js';
import { TransferSender, TransferReceiver } from '../src/core/transfer_engine.js';

test('Shadow Board #1: Anti-replay & anti-reordering vérifié par decryptChunk', () => {
  const sessionKey = generateSessionKey();
  const plainText = Buffer.from('Bloc de données confidentielles');
  
  // Chiffrement du chunk #5
  const encryptedChunk5 = encryptChunk(plainText, sessionKey, 5);

  // 1. Déchiffrement avec l'index attendu correct (5) => Succès
  const decrypted = decryptChunk(encryptedChunk5, sessionKey, 5);
  assert.deepEqual(decrypted, plainText);

  // 2. Déchiffrement avec un index attendu erroné (ex: 4 ou rejeu) => Doit lever une exception de sécurité
  assert.throws(() => {
    decryptChunk(encryptedChunk5, sessionKey, 4);
  }, /Altération ou réordonnancement de bloc détecté/i);

  // 3. Déchiffrement sans spécifier expectedChunkIndex (tolérant si non spécifié) => Succès
  const decryptedUnchecked = decryptChunk(encryptedChunk5, sessionKey);
  assert.deepEqual(decryptedUnchecked, plainText);
});

test('Shadow Board #2: Garbage Collector automatique des manifests expirés (.fastdrop_part)', async () => {
  const tmpDir = await fs.mkdtemp(path.join(os.tmpdir(), 'lancedrop-gc-test-'));

  const expiredPart = path.join(tmpDir, 'abandoned_transfer.iso.fastdrop_part');
  const freshPart = path.join(tmpDir, 'active_transfer.iso.fastdrop_part');
  const expiredManifest = path.join(tmpDir, '.fastdrop_resume_expired.json');
  const freshManifest = path.join(tmpDir, '.fastdrop_resume_fresh.json');

  await fs.writeFile(expiredPart, Buffer.from('dummy partial data 1'));
  await fs.writeFile(freshPart, Buffer.from('dummy partial data 2'));
  await fs.writeFile(expiredManifest, JSON.stringify({ transfer_id: 'expired', destination_path: expiredPart }));
  await fs.writeFile(freshManifest, JSON.stringify({ transfer_id: 'fresh', destination_path: freshPart }));

  // Définir la date de modification du manifeste expiré à il y a 48 heures
  const twoDaysAgo = (Date.now() - 48 * 3600 * 1000) / 1000;
  await fs.utimes(expiredManifest, twoDaysAgo, twoDaysAgo);

  // Exécuter le Garbage Collector avec seuil d'expiration de 24h
  const deletedCount = await ResumeManager.cleanupExpiredManifests(tmpDir, 24 * 3600 * 1000);
  assert.equal(deletedCount, 1, 'Exactement 1 fichier expiré doit être purgé');

  // Vérifier qu'expiredPart et expiredManifest n'existent plus
  let expiredExists = false;
  try {
    await fs.access(expiredPart);
    expiredExists = true;
  } catch (_) {
    expiredExists = false;
  }
  assert.equal(expiredExists, false, 'Le fichier temporaire abandonné doit avoir été supprimé');

  // Vérifier que freshPart existe toujours
  let freshExists = false;
  try {
    await fs.access(freshPart);
    freshExists = true;
  } catch (_) {
    freshExists = false;
  }
  assert.equal(freshExists, true, 'Le transfert récent doit être préservé');

  await fs.rm(tmpDir, { recursive: true, force: true });
});

test('Shadow Board #3: DiscoveryEngine borne la table des pairs (LRU max 50) et assainit le deviceName', () => {
  const engine = new DiscoveryEngine({
    deviceId: 'test-node-local',
    deviceName: 'Super PC',
    platform: 'windows',
    transferPort: 42420,
  });

  // Simuler l'arrivée de 70 annonces de pairs distincts (tentative d'inondation mémoire / DoS)
  for (let i = 1; i <= 70; i++) {
    const fakePeer = {
      deviceId: `device-attacker-${i}`,
      deviceName: `Smartphone \u0000\u001F Malveillant #${i} ${'X'.repeat(100)}`,
      platform: 'android',
      transferPort: 42420,
      version: 1,
    };
    engine.handleAnnouncement(fakePeer, { address: `192.168.1.${i % 250 + 1}`, port: 42424 });
  }

  const peers = engine.getPeers();
  assert.ok(peers.length <= 50, `Le nombre de pairs stockés (${peers.length}) ne doit jamais dépasser 50`);

  // Vérification de l'assainissement du deviceName (pas de caractères de contrôle, longueur max 64)
  for (const p of peers) {
    assert.ok(p.deviceName.length <= 64, 'La longueur du nom doit être plafonnée à 64 caractères');
    assert.ok(!/[\x00-\x1F]/.test(p.deviceName), 'Les caractères de contrôle ASCII doivent être éradiqués');
  }
});

test('Shadow Board #4: Préservation de l’horodatage de modification (mtime)', async () => {
  const tmpDir = await fs.mkdtemp(path.join(os.tmpdir(), 'lancedrop-mtime-test-'));
  const srcFile = path.join(tmpDir, 'document_avec_mtime.txt');
  const destDir = path.join(tmpDir, 'received');
  await fs.mkdir(destDir, { recursive: true });

  await fs.writeFile(srcFile, 'Contenu pour test d’intégrité temporelle');
  
  // Date de modification connue dans le passé (ex: 1er Janvier 2024 12:00:00 UTC)
  const targetMtimeSec = 1704110400;
  await fs.utimes(srcFile, targetMtimeSec, targetMtimeSec);

  const server = net.createServer(async (socket) => {
    const receiver = new TransferReceiver(socket, destDir, { conflictResolution: 'OVERWRITE' });
    await receiver.start();
  });

  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  const port = server.address().port;

  const client = net.connect({ host: '127.0.0.1', port });
  await new Promise((resolve) => client.once('connect', resolve));

  const sender = new TransferSender(client);
  await sender.sendPath(srcFile);
  client.end();

  await new Promise((resolve) => server.close(resolve));

  const destFile = path.join(destDir, 'document_avec_mtime.txt');
  const destStat = await fs.stat(destFile);

  // Le mtime reçu doit correspondre à 1 seconde près
  const diffSec = Math.abs(Math.floor(destStat.mtimeMs / 1000) - targetMtimeSec);
  assert.ok(diffSec <= 2, `mtime original préservé (écart: ${diffSec}s)`);

  await fs.rm(tmpDir, { recursive: true, force: true });
});
