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

test('DiscoveryEngine: Rejet strict des annonces provenant des propres adresses IP de l’hôte (peer_found non émis)', async () => {
  const hostIps = ['192.168.1.100', '10.0.0.5'];
  const devHost = new DiscoveryEngine({
    deviceId: 'dev-host-001',
    deviceName: 'Host-PC',
    platform: 'windows',
    ownIps: hostIps,
  });

  assert.equal(devHost.isOwnAddress('192.168.1.100'), true);
  assert.equal(devHost.isOwnAddress('::ffff:192.168.1.100'), true);
  assert.equal(devHost.isOwnAddress('10.0.0.5'), true);
  assert.equal(devHost.isOwnAddress('192.168.1.200'), false);

  let peerFoundEmitted = false;
  let peerDiscoveredEmitted = false;

  devHost.on('peer_found', () => {
    peerFoundEmitted = true;
  });
  devHost.on('peer_discovered', () => {
    peerDiscoveredEmitted = true;
  });

  // 1. Annonce simulant un rebond UDP provenant de la propre IP de l'hôte (même avec un deviceId distinct)
  devHost.handleAnnouncement(
    {
      deviceId: 'dev-ghost-self',
      deviceName: 'Phantom-Self',
      platform: 'windows',
      transferPort: 42420,
    },
    { address: '192.168.1.100', port: 42424 }
  );

  // Annonce avec préfixe IPv4-mapped ::ffff:
  devHost.handleAnnouncement(
    {
      deviceId: 'dev-ghost-v6',
      deviceName: 'Phantom-V6',
      platform: 'windows',
      transferPort: 42420,
    },
    { address: '::ffff:10.0.0.5', port: 42424 }
  );

  assert.equal(peerFoundEmitted, false, 'peer_found ne doit pas être émis pour une IP propre à l’hôte');
  assert.equal(peerDiscoveredEmitted, false, 'peer_discovered ne doit pas être émis pour une IP propre à l’hôte');
  assert.equal(devHost.getPeers().length, 0, 'La liste des pairs doit rester vide');

  // 2. Annonce légitime provenant d'un pair externe distant (IP différente)
  devHost.handleAnnouncement(
    {
      deviceId: 'dev-remote-real',
      deviceName: 'Android-Pixel',
      platform: 'android',
      transferPort: 42420,
    },
    { address: '192.168.1.200', port: 42424 }
  );

  assert.equal(peerFoundEmitted, true, 'peer_found doit être émis pour un pair distant légitime');
  assert.equal(peerDiscoveredEmitted, true, 'peer_discovered doit être émis pour un pair distant légitime');
  assert.equal(devHost.getPeers().length, 1, 'Le pair distant légitime doit être ajouté');
  assert.equal(devHost.getPeers()[0].deviceId, 'dev-remote-real');
});

test('DiscoveryEngine: Rejet strict des annonces portant le propre deviceId de l’appareil', async () => {
  const devLocal = new DiscoveryEngine({
    deviceId: 'dev-self-999',
    deviceName: 'Mon-PC',
    platform: 'windows',
  });

  let selfPeerFound = false;
  let selfPeerDiscovered = false;

  devLocal.on('peer_found', () => {
    selfPeerFound = true;
  });
  devLocal.on('peer_discovered', () => {
    selfPeerDiscovered = true;
  });

  // Annonce contenant le même deviceId que l'appareil local (même avec une IP distante différente)
  devLocal.handleAnnouncement(
    {
      deviceId: 'dev-self-999',
      deviceName: 'Mon-PC',
      platform: 'windows',
      transferPort: 42420,
    },
    { address: '192.168.1.188', port: 42424 }
  );

  assert.equal(selfPeerFound, false, 'peer_found ne doit pas être émis pour le propre deviceId');
  assert.equal(selfPeerDiscovered, false, 'peer_discovered ne doit pas être émis pour le propre deviceId');
  assert.equal(devLocal.getPeers().length, 0, 'Aucun pair avec le propre deviceId ne doit être enregistré');
});

