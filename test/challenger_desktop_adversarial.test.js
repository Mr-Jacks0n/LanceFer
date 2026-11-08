import test from 'node:test';
import assert from 'node:assert/strict';
import os from 'node:os';
import { DiscoveryEngine } from '../src/discovery/discovery_engine.js';

// Extracted isLocalAddress logic directly mirroring main.cjs
function getAllHostIps() {
  const ipSet = new Set(['127.0.0.1', '::1', '0.0.0.0', '::', 'localhost']);
  try {
    const interfaces = os.networkInterfaces();
    for (const name of Object.keys(interfaces)) {
      for (const iface of interfaces[name]) {
        if (iface.address) {
          const clean = iface.address.replace(/^::ffff:/i, '').trim().toLowerCase();
          ipSet.add(clean);
          ipSet.add(iface.address.toLowerCase());
        }
      }
    }
  } catch (_) {}
  return ipSet;
}

function isLocalAddressMainCjs(addr) {
  if (!addr) return false;
  const cachedHostIps = getAllHostIps();
  const clean = addr.replace(/^::ffff:/i, '').trim().toLowerCase();
  if (clean === '127.0.0.1' || clean === '::1' || clean.startsWith('127.') || clean === '0.0.0.0' || clean === '::') {
    return true;
  }
  return cachedHostIps.has(clean) || cachedHostIps.has(addr.toLowerCase());
}

test('Adversarial 1: DiscoveryEngine with ownIps configured vs forged device IDs with host IPs', async () => {
  const hostIps = ['192.168.1.100', '10.0.4.15'];
  const devHost = new DiscoveryEngine({
    deviceId: 'real-host-device-01',
    deviceName: 'Real-Host-PC',
    platform: 'windows',
    ownIps: hostIps,
  });

  let events = [];
  devHost.on('peer_found', (p) => events.push({ type: 'peer_found', p }));
  devHost.on('peer_discovered', (p) => events.push({ type: 'peer_discovered', p }));

  // Attack 1A: Packet with forged / spoofed deviceId but originating from local host IP 192.168.1.100
  devHost.handleAnnouncement(
    {
      deviceId: 'forged-attacker-id-666',
      deviceName: 'Evil-Attacker',
      platform: 'android',
      transferPort: 42420,
    },
    { address: '192.168.1.100', port: 42424 }
  );

  assert.equal(events.length, 0, 'DiscoveryEngine must NOT emit peer_found/peer_discovered when address is in ownIps');
  assert.equal(devHost.getPeers().length, 0, 'No peer should be stored in peers map');

  // Attack 1B: Packet with forged deviceId and IPv4-mapped local host IP ::ffff:10.0.4.15
  devHost.handleAnnouncement(
    {
      deviceId: 'forged-attacker-id-777',
      deviceName: 'Evil-Attacker-V6',
      platform: 'linux',
      transferPort: 42420,
    },
    { address: '::ffff:10.0.4.15', port: 42424 }
  );

  assert.equal(events.length, 0, 'DiscoveryEngine must NOT emit peer_found/peer_discovered for ::ffff: mapped host IP');
  assert.equal(devHost.getPeers().length, 0);
});

test('Adversarial 2: DiscoveryEngine rejection of packets with local host device ID and forged external IP', async () => {
  const devHost = new DiscoveryEngine({
    deviceId: 'real-host-device-01',
    deviceName: 'Real-Host-PC',
    platform: 'windows',
    ownIps: ['192.168.1.100'],
  });

  let events = [];
  devHost.on('peer_found', (p) => events.push({ type: 'peer_found', p }));
  devHost.on('peer_discovered', (p) => events.push({ type: 'peer_discovered', p }));

  // Attack 2: Packet arrives from external IP (192.168.1.222) claiming to have host's deviceId
  devHost.handleAnnouncement(
    {
      deviceId: 'real-host-device-01',
      deviceName: 'Spoofed-Host-Name',
      platform: 'windows',
      transferPort: 42420,
    },
    { address: '192.168.1.222', port: 42424 }
  );

  assert.equal(events.length, 0, 'DiscoveryEngine must reject packet with matching deviceId regardless of source IP');
  assert.equal(devHost.getPeers().length, 0);
});

test('Adversarial 3: DiscoveryEngine behavior on default initialization vs loopback (127.0.0.1, ::1, 127.0.0.2, 0.0.0.0)', async () => {
  // Instance initialized with default interfaces (no ownIps explicitly provided)
  const defaultDev = new DiscoveryEngine({
    deviceId: 'real-host-device-default',
    deviceName: 'Default-PC',
    platform: 'windows',
  });

  // Empirical test: Does DiscoveryEngine default ownIps include loopback?
  assert.equal(defaultDev.isOwnAddress('127.0.0.1'), false);
  assert.equal(defaultDev.isOwnAddress('::1'), false);
  assert.equal(defaultDev.isOwnAddress('::ffff:127.0.0.1'), false);
  assert.equal(defaultDev.isOwnAddress('127.0.0.2'), false);
  assert.equal(defaultDev.isOwnAddress('0.0.0.0'), false);

  let events = [];
  defaultDev.on('peer_found', (p) => events.push({ type: 'peer_found', p }));
  defaultDev.on('peer_discovered', (p) => events.push({ type: 'peer_discovered', p }));

  // Because 127.0.0.2 is not in default ownIps, DiscoveryEngine alone admits it:
  defaultDev.handleAnnouncement(
    {
      deviceId: 'loopback-rogue-2',
      deviceName: 'Rogue-Loopback',
      platform: 'windows',
    },
    { address: '127.0.0.2', port: 42424 }
  );

  // DiscoveryEngine emitted peer_discovered/peer_found because ownIps lacked loopback by default:
  assert.equal(events.length, 2, 'DiscoveryEngine without ownAddresses emits on 127.0.0.2');
  assert.equal(defaultDev.getPeers().length, 1);
});

test('Adversarial 4: main.cjs isLocalAddress robustness against all loopbacks and subnets', () => {
  assert.equal(isLocalAddressMainCjs('127.0.0.1'), true, 'main.cjs isLocalAddress rejects 127.0.0.1');
  assert.equal(isLocalAddressMainCjs('127.0.0.2'), true, 'main.cjs isLocalAddress rejects 127.0.0.2');
  assert.equal(isLocalAddressMainCjs('127.255.255.254'), true, 'main.cjs isLocalAddress rejects 127.x.x.x');
  assert.equal(isLocalAddressMainCjs('::1'), true, 'main.cjs isLocalAddress rejects ::1');
  assert.equal(isLocalAddressMainCjs('::ffff:127.0.0.1'), true, 'main.cjs isLocalAddress rejects ::ffff:127.0.0.1');
  assert.equal(isLocalAddressMainCjs('::ffff:127.0.0.2'), true, 'main.cjs isLocalAddress rejects ::ffff:127.0.0.2');
  assert.equal(isLocalAddressMainCjs('0.0.0.0'), true, 'main.cjs isLocalAddress rejects 0.0.0.0');
  assert.equal(isLocalAddressMainCjs('::'), true, 'main.cjs isLocalAddress rejects ::');
  assert.equal(isLocalAddressMainCjs('localhost'), true, 'main.cjs isLocalAddress rejects localhost');

  // Physical host IPs
  const interfaces = os.networkInterfaces();
  for (const name of Object.keys(interfaces)) {
    for (const iface of interfaces[name]) {
      if (iface.address) {
        assert.equal(isLocalAddressMainCjs(iface.address), true, `main.cjs rejects physical/virtual host IP: ${iface.address}`);
        assert.equal(isLocalAddressMainCjs(`::ffff:${iface.address}`), true, `main.cjs rejects ::ffff:${iface.address}`);
      }
    }
  }

  // Legitimate external remote IP should NOT be blocked
  assert.equal(isLocalAddressMainCjs('192.168.254.254'), false, 'Remote LAN IP is not local');
  assert.equal(isLocalAddressMainCjs('10.200.100.50'), false, 'Remote LAN IP is not local');
});

test('Adversarial 5: main.cjs 5-layer filtering audit against all adversarial evasion payloads', () => {
  const myHostname = os.hostname();
  const myDeviceId = `win_${myHostname}`;
  const myDeviceName = myHostname;

  function evaluateMainCjsFilter(data, rinfo) {
    if (!rinfo || !rinfo.address || isLocalAddressMainCjs(rinfo.address)) {
      return { accepted: false, reason: 'REJECTED_LOCAL_IP' };
    }
    if (!data) return { accepted: false, reason: 'NO_DATA' };
    if (data.deviceId === myDeviceId) {
      return { accepted: false, reason: 'REJECTED_SELF_DEVICE_ID' };
    }
    if (data.deviceName === myDeviceName && data.platform === 'windows') {
      return { accepted: false, reason: 'REJECTED_SELF_DEVICE_NAME' };
    }
    return { accepted: true };
  }

  // 1. Forged device ID with local host IP (physical IP)
  const physicalIps = Array.from(getAllHostIps()).filter(ip => ip !== '127.0.0.1' && ip !== '0.0.0.0' && ip !== '::1');
  if (physicalIps.length > 0) {
    const testIp = physicalIps[0];
    const res1 = evaluateMainCjsFilter({ deviceId: 'forged_foreign_id', deviceName: 'Foreign', platform: 'android' }, { address: testIp });
    assert.equal(res1.accepted, false);
    assert.equal(res1.reason, 'REJECTED_LOCAL_IP');
  }

  // 2. Forged remote IP with local host's device ID
  const res2 = evaluateMainCjsFilter({ deviceId: myDeviceId, deviceName: 'Foreign', platform: 'android' }, { address: '192.168.88.99' });
  assert.equal(res2.accepted, false);
  assert.equal(res2.reason, 'REJECTED_SELF_DEVICE_ID');

  // 3. Forged remote IP with local device name on windows platform
  const res3 = evaluateMainCjsFilter({ deviceId: 'other_dev', deviceName: myDeviceName, platform: 'windows' }, { address: '192.168.88.99' });
  assert.equal(res3.accepted, false);
  assert.equal(res3.reason, 'REJECTED_SELF_DEVICE_NAME');

  // 4. Loopback IPv6 ::1
  const res4 = evaluateMainCjsFilter({ deviceId: 'other_dev', deviceName: 'Foreign', platform: 'android' }, { address: '::1' });
  assert.equal(res4.accepted, false);
  assert.equal(res4.reason, 'REJECTED_LOCAL_IP');

  // 5. IPv4-mapped ::ffff:127.0.0.1
  const res5 = evaluateMainCjsFilter({ deviceId: 'other_dev', deviceName: 'Foreign', platform: 'android' }, { address: '::ffff:127.0.0.1' });
  assert.equal(res5.accepted, false);
  assert.equal(res5.reason, 'REJECTED_LOCAL_IP');

  // 6. Subnets like 127.0.0.2
  const res6 = evaluateMainCjsFilter({ deviceId: 'other_dev', deviceName: 'Foreign', platform: 'android' }, { address: '127.0.0.2' });
  assert.equal(res6.accepted, false);
  assert.equal(res6.reason, 'REJECTED_LOCAL_IP');

  // 7. Subnet 0.0.0.0
  const res7 = evaluateMainCjsFilter({ deviceId: 'other_dev', deviceName: 'Foreign', platform: 'android' }, { address: '0.0.0.0' });
  assert.equal(res7.accepted, false);
  assert.equal(res7.reason, 'REJECTED_LOCAL_IP');

  // 8. Legitimate foreign peer on foreign LAN IP
  const res8 = evaluateMainCjsFilter({ deviceId: 'foreign_dev_999', deviceName: 'Galaxy-S24', platform: 'android' }, { address: '192.168.1.77' });
  assert.equal(res8.accepted, true);
});

test('Adversarial 6: UI Peer filtering logic isolation test', () => {
  const localDeviceInfo = {
    deviceId: 'win_HostMachine',
    deviceName: 'HostMachine',
    platform: 'windows',
    primaryIp: '192.168.1.12',
    activeIps: ['192.168.1.12', '10.0.4.15']
  };

  function filterPeersForRadar(rawList, localInfo) {
    return rawList.filter(d => {
      if (!d) return false;
      if (localInfo) {
        if (d.id === localInfo.deviceId) return false;
        if (localInfo.activeIps && localInfo.activeIps.includes(d.ip)) return false;
        if (d.ip === localInfo.primaryIp) return false;
      }
      if (d.ip === '127.0.0.1' || d.ip === '::1' || String(d.ip).startsWith('127.')) return false;
      return true;
    });
  }

  const peerCandidates = [
    { id: 'win_HostMachine', ip: '192.168.1.200', name: 'Self DeviceId Spoofed' },
    { id: 'dev_ext_1', ip: '192.168.1.12', name: 'Self Primary IP Spoofed' },
    { id: 'dev_ext_2', ip: '10.0.4.15', name: 'Self Secondary IP Spoofed' },
    { id: 'dev_ext_3', ip: '127.0.0.1', name: 'Loopback 127.0.0.1' },
    { id: 'dev_ext_4', ip: '127.0.0.2', name: 'Loopback 127.0.0.2' },
    { id: 'dev_ext_5', ip: '::1', name: 'IPv6 Loopback' },
    { id: 'real_phone', ip: '192.168.1.55', name: 'Galaxy S24' },
    { id: 'real_laptop', ip: '192.168.1.88', name: 'MacBook Pro' }
  ];

  const filtered = filterPeersForRadar(peerCandidates, localDeviceInfo);

  assert.equal(filtered.length, 2, 'Only the 2 external legitimate devices must survive filtering');
  assert.deepEqual(filtered.map(p => p.id), ['real_phone', 'real_laptop']);
});
