import test from 'node:test';
import assert from 'node:assert/strict';
import { DiscoveryEngine } from '../src/discovery/discovery_engine.js';

test('DiscoveryEngine: Découverte bidirectionnelle entre 2 instances locales', async () => {
  const portA = 42490;
  const portB = 42491;

  const devA = new DiscoveryEngine({
    deviceId: 'dev-pc-001',
    deviceName: 'PC-Jackson',
    platform: 'windows',
    transferPort: 42420,
  });

  const devB = new DiscoveryEngine({
    deviceId: 'dev-phone-002',
    deviceName: 'Galaxy-S24',
    platform: 'android',
    transferPort: 42421,
  });

  let devADiscoveredB = false;
  let devBDiscoveredA = false;

  devA.on('peer_discovered', peer => {
    if (peer.deviceId === 'dev-phone-002') {
      devADiscoveredB = true;
      assert.equal(peer.deviceName, 'Galaxy-S24');
      assert.equal(peer.platform, 'android');
    }
  });

  devB.on('peer_discovered', peer => {
    if (peer.deviceId === 'dev-pc-001') {
      devBDiscoveredA = true;
      assert.equal(peer.deviceName, 'PC-Jackson');
      assert.equal(peer.platform, 'windows');
    }
  });

  await devA.start({ port: portA, targetPort: portB, broadcastAddress: '127.0.0.1', beaconIntervalMs: 100 });
  await devB.start({ port: portB, targetPort: portA, broadcastAddress: '127.0.0.1', beaconIntervalMs: 100 });

  // Attendre la découverte mutuelle (avec timeout)
  await new Promise((resolve, reject) => {
    const timeout = setTimeout(() => {
      clearInterval(check);
      reject(new Error(`Timeout: A->B=${devADiscoveredB}, B->A=${devBDiscoveredA}`));
    }, 2000);

    const check = setInterval(() => {
      if (devADiscoveredB && devBDiscoveredA) {
        clearTimeout(timeout);
        clearInterval(check);
        resolve();
      }
    }, 30);
  });

  assert.ok(devADiscoveredB);
  assert.ok(devBDiscoveredA);

  const peersOfA = devA.getPeers();
  assert.equal(peersOfA.length, 1);
  assert.equal(peersOfA[0].deviceId, 'dev-phone-002');

  await devA.stop();
  await devB.stop();
});

test('DiscoveryEngine: Expiration TTL et détection de disparition de pair', async () => {
  const portA = 42492;
  const portB = 42493;

  const devA = new DiscoveryEngine({
    deviceId: 'dev-pc-001',
    deviceName: 'PC-Jackson',
    platform: 'windows',
  });

  const devB = new DiscoveryEngine({
    deviceId: 'dev-phone-002',
    deviceName: 'Galaxy-S24',
    platform: 'android',
  });

  let peerLostDetected = false;
  devA.on('peer_lost', peer => {
    if (peer.deviceId === 'dev-phone-002') {
      peerLostDetected = true;
    }
  });

  // TTL très court de 250ms pour le test
  await devA.start({ port: portA, targetPort: portB, broadcastAddress: '127.0.0.1', beaconIntervalMs: 50, peerTtlMs: 250 });
  await devB.start({ port: portB, targetPort: portA, broadcastAddress: '127.0.0.1', beaconIntervalMs: 50 });

  // Attendre la découverte
  await new Promise((resolve, reject) => {
    const timeout = setTimeout(() => reject(new Error('Timeout découverte B')), 2000);
    const check = setInterval(() => {
      if (devA.getPeers().length >= 1) {
        clearTimeout(timeout);
        clearInterval(check);
        resolve();
      }
    }, 20);
  });

  assert.equal(devA.getPeers().length, 1);

  // Arrêter B brutalement (simulation coupure)
  await devB.stop();

  // Attendre l'expiration du TTL
  await new Promise((resolve, reject) => {
    const timeout = setTimeout(() => reject(new Error('Timeout TTL expiration')), 2000);
    const check = setInterval(() => {
      if (peerLostDetected) {
        clearTimeout(timeout);
        clearInterval(check);
        resolve();
      }
    }, 30);
  });

  assert.ok(peerLostDetected);
  assert.equal(devA.getPeers().length, 0);

  await devA.stop();
});

