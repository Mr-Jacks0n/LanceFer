import { test } from 'node:test';
import assert from 'node:assert/strict';

function getSubnetBroadcast(ip, netmask) {
  try {
    const ipParts = ip.split('.').map(Number);
    const maskParts = (netmask || '255.255.255.0').split('.').map(Number);
    if (ipParts.length !== 4 || maskParts.length !== 4) return '255.255.255.255';
    const broadcastParts = ipParts.map((part, i) => (part | (~maskParts[i] & 255)));
    return broadcastParts.join('.');
  } catch (_) {
    return '255.255.255.255';
  }
}

test('Subnet Broadcast: Calcul exact de l’adresse de diffusion pour divers masques réseau', () => {
  // Masque /24 classique (255.255.255.0)
  assert.equal(getSubnetBroadcast('192.168.1.100', '255.255.255.0'), '192.168.1.255');
  
  // Masque Hotspot Android (192.168.43.1 / 255.255.255.0)
  assert.equal(getSubnetBroadcast('192.168.43.25', '255.255.255.0'), '192.168.43.255');
  
  // Masque /16 (255.255.0.0)
  assert.equal(getSubnetBroadcast('172.16.5.10', '255.255.0.0'), '172.16.255.255');
  
  // Masque /8 (255.0.0.0)
  assert.equal(getSubnetBroadcast('10.50.2.1', '255.0.0.0'), '10.255.255.255');

  // Repli sécurisé en cas d'entrée invalide
  assert.equal(getSubnetBroadcast('invalid', null), '255.255.255.255');
});

test('Polling Optimizer: Throttling anti-flood des événements de découverte de pairs', () => {
  let emitCount = 0;
  let lastEmit = 0;

  function simulateBeaconArrival(isNewOrChanged, now) {
    if (isNewOrChanged || (now - lastEmit >= 1000)) {
      emitCount++;
      lastEmit = now;
      return true;
    }
    return false;
  }

  // 1. Premier beacon (nouveau pair) -> émission immédiate
  let t = 1000;
  assert.equal(simulateBeaconArrival(true, t), true);
  assert.equal(emitCount, 1);

  // 2. Beacons répétés sans changement à t + 100ms, t + 200ms, t + 500ms -> ignorés
  assert.equal(simulateBeaconArrival(false, t + 100), false);
  assert.equal(simulateBeaconArrival(false, t + 200), false);
  assert.equal(simulateBeaconArrival(false, t + 500), false);
  assert.equal(emitCount, 1, 'Les répétitions de beacons sans changement doivent être filtrées');

  // 3. Après 1000 ms écoulées -> émission de synchronisation autorisée
  assert.equal(simulateBeaconArrival(false, t + 1050), true);
  assert.equal(emitCount, 2);

  // 4. Nouveau changement -> émission immédiate sans attendre 1000ms
  assert.equal(simulateBeaconArrival(true, t + 1100), true);
  assert.equal(emitCount, 3);
});

test('Polling Optimizer: Élagage TTL des pairs inactifs et purge après 30s', () => {
  const devices = [
    { id: 'dev1', name: 'PC Bureau', lastSeen: 5000, status: 'online' },
    { id: 'dev2', name: 'Smartphone', lastSeen: 25000, status: 'online' },
    { id: 'dev3', name: 'Tablette Active', lastSeen: 38000, status: 'online' }
  ];

  const now = 40000;
  const selectedTargetId = 'dev1';

  // Simuler le cycle du TTL Garbage Collector
  let changed = false;
  for (let i = devices.length - 1; i >= 0; i--) {
    const dev = devices[i];
    const elapsed = now - dev.lastSeen;
    if (dev.status === 'online' && elapsed > 10000) {
      dev.status = 'offline';
      changed = true;
    }
    if (elapsed > 30000 && dev.id !== selectedTargetId) {
      devices.splice(i, 1);
      changed = true;
    }
  }

  assert.equal(changed, true);
  // dev3 (elapsed = 2000 ms) reste online
  assert.equal(devices.find(d => d.id === 'dev3').status, 'online');
  // dev2 (elapsed = 10000 ms) reste présent mais passe offline
  assert.equal(devices.find(d => d.id === 'dev2').status, 'offline');
  // dev1 (elapsed = 30000 ms) est la cible sélectionnée active, donc conservée en offline
  assert.equal(devices.find(d => d.id === 'dev1').status, 'offline');
});
