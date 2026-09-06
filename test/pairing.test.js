import test from 'node:test';
import assert from 'node:assert/strict';
import net from 'node:net';
import fs from 'node:fs/promises';
import path from 'node:path';
import { PairingEngine } from '../src/security/pairing_engine.js';
import { TrustStore } from '../src/security/trust_store.js';
import { FrameParser } from '../src/protocol/framing.js';
import { MESSAGE_TYPES } from '../src/protocol/constants.js';

const TMP_DIR = path.resolve('tmp/test_pairing');

test('TrustStore: cycle de vie complet (ajout, vérification, révocation, suppression)', async () => {
  await fs.mkdir(TMP_DIR, { recursive: true });
  const store = new TrustStore(TMP_DIR, 'test_trusted.json');

  assert.equal(await store.isTrusted('dev-001'), false);

  await store.addTrustedDevice({
    deviceId: 'dev-001',
    deviceName: 'Pixel-8',
    publicKeyFingerprint: '749 182',
    platform: 'android',
  });

  assert.equal(await store.isTrusted('dev-001'), true);
  assert.equal(await store.isTrusted('dev-001', '749 182'), true);
  assert.equal(await store.isTrusted('dev-001', '000 000'), false);

  // Recharger depuis le disque
  const reloadedStore = new TrustStore(TMP_DIR, 'test_trusted.json');
  assert.equal(await reloadedStore.isTrusted('dev-001'), true);

  // Révocation
  await store.revokeDevice('dev-001');
  assert.equal(await store.isTrusted('dev-001'), false);

  // Suppression
  await store.removeDevice('dev-001');
  assert.equal(store.getDevices().length, 0);

  await fs.rm(TMP_DIR, { recursive: true, force: true });
});

test('PairingEngine: dérivation SAS PIN déterministe et canonique', () => {
  const secret1 = 'abcdef0123456789abcdef0123456789';
  const secret2 = '9876543210fedcba9876543210fedcba';

  const pinA = PairingEngine.deriveSasPin(secret1, secret2);
  const pinB = PairingEngine.deriveSasPin(secret2, secret1);

  assert.equal(pinA, pinB);
  assert.match(pinA, /^\d{3} \d{3}$/);
});

test('PairingEngine: Handshake complet via socket TCP avec confirmation de PIN', async () => {
  await fs.mkdir(TMP_DIR, { recursive: true });

  const storeA = new TrustStore(TMP_DIR, 'trust_a.json');
  const storeB = new TrustStore(TMP_DIR, 'trust_b.json');

  const engineA = new PairingEngine({
    deviceId: 'dev-pc-001',
    deviceName: 'PC-Jackson',
    platform: 'windows',
  }, storeA);

  const engineB = new PairingEngine({
    deviceId: 'dev-phone-002',
    deviceName: 'Galaxy-S24',
    platform: 'android',
  }, storeB);

  // Créer un serveur récepteur (Phone)
  const server = net.createServer();
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const port = server.address().port;

  let responderPin = null;
  server.on('connection', socket => {
    const parser = new FrameParser();
    socket.on('data', chunk => parser.push(chunk));

    parser.on('frame', async ({ type, payload }) => {
      if (type === MESSAGE_TYPES.PAIR_REQUEST) {
        const req = JSON.parse(payload.toString('utf8'));
        await engineB.handlePairRequest(socket, req, async (peer, pin) => {
          responderPin = pin;
          return true; // Utilisateur B valide le PIN
        });
      }
    });
  });

  // Client initiateur (PC)
  const clientSocket = net.connect({ host: '127.0.0.1', port });
  await new Promise(resolve => clientSocket.on('connect', resolve));
  const clientParser = new FrameParser();
  clientSocket.on('data', chunk => clientParser.push(chunk));

  let initiatorPin = null;
  const result = await engineA.initiatePairing(clientSocket, clientParser, async pin => {
    initiatorPin = pin;
    return true; // Utilisateur A valide le PIN
  });

  // Vérifications
  assert.equal(result.success, true);
  assert.equal(result.deviceId, 'dev-phone-002');
  assert.equal(initiatorPin, responderPin); // Les deux PINs doivent être identiques !
  assert.equal(await storeA.isTrusted('dev-phone-002'), true);
  assert.equal(await storeB.isTrusted('dev-pc-001'), true);

  clientSocket.end();
  await new Promise(resolve => server.close(resolve));
  await fs.rm(TMP_DIR, { recursive: true, force: true });
});

test('PairingEngine: Rejet si l\'utilisateur refuse la demande', async () => {
  await fs.mkdir(TMP_DIR, { recursive: true });

  const storeA = new TrustStore(TMP_DIR, 'trust_a_rej.json');
  const storeB = new TrustStore(TMP_DIR, 'trust_b_rej.json');

  const engineA = new PairingEngine({ deviceId: 'dev-pc-001', deviceName: 'PC-Jackson' }, storeA);
  const engineB = new PairingEngine({ deviceId: 'dev-phone-002', deviceName: 'Galaxy-S24' }, storeB);

  const server = net.createServer();
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const port = server.address().port;

  server.on('connection', socket => {
    const parser = new FrameParser();
    socket.on('data', chunk => parser.push(chunk));
    parser.on('frame', async ({ type, payload }) => {
      if (type === MESSAGE_TYPES.PAIR_REQUEST) {
        const req = JSON.parse(payload.toString('utf8'));
        await engineB.handlePairRequest(socket, req, async () => {
          return false; // Utilisateur B REFUSE l'appairage !
        });
      }
    });
  });

  const clientSocket = net.connect({ host: '127.0.0.1', port });
  await new Promise(resolve => clientSocket.on('connect', resolve));
  const clientParser = new FrameParser();
  clientSocket.on('data', chunk => clientParser.push(chunk));

  const result = await engineA.initiatePairing(clientSocket, clientParser, async () => true);

  assert.equal(result.success, false);
  assert.match(result.reason, /Refusé/);
  assert.equal(await storeA.isTrusted('dev-phone-002'), false);
  assert.equal(await storeB.isTrusted('dev-pc-001'), false);

  clientSocket.end();
  await new Promise(resolve => server.close(resolve));
  await fs.rm(TMP_DIR, { recursive: true, force: true });
});
