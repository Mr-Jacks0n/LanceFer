import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';

test('Windows Parity: Filtrage des adaptateurs réseau virtuels (WSL, VirtualBox, Hyper-V, VPN)', () => {
  const VIRTUAL_ADAPTER_PATTERNS = [
    /vethernet/i,
    /wsl/i,
    /virtualbox/i,
    /vbox/i,
    /vmnet/i,
    /vmware/i,
    /tailscale/i,
    /zerotier/i,
    /tap/i,
    /tun/i,
    /loopback/i,
    /bluetooth/i,
    /isatap/i,
    /teredo/i,
    /hyper-v/i
  ];

  const simulatedInterfaces = {
    'vEthernet (WSL)': [
      { address: '172.28.160.1', family: 'IPv4', internal: false, netmask: '255.255.240.0' }
    ],
    'VirtualBox Host-Only Ethernet Adapter': [
      { address: '192.168.56.1', family: 'IPv4', internal: false, netmask: '255.255.255.0' }
    ],
    'Bluetooth Network Connection': [
      { address: '169.254.12.34', family: 'IPv4', internal: false, netmask: '255.255.0.0' }
    ],
    'Wi-Fi': [
      { address: '192.168.1.45', family: 'IPv4', internal: false, netmask: '255.255.255.0' }
    ]
  };

  function filterAndPrioritize(interfaces) {
    const validList = [];
    for (const name of Object.keys(interfaces)) {
      const isVirtual = VIRTUAL_ADAPTER_PATTERNS.some(pat => pat.test(name));
      for (const iface of interfaces[name]) {
        if (iface.family === 'IPv4' && !iface.internal) {
          if (iface.address.startsWith('169.254.') || iface.address.startsWith('127.')) continue;

          let priority = 10;
          const lowerName = name.toLowerCase();
          if (lowerName.includes('wi-fi') || lowerName.includes('wireless') || lowerName.includes('wlan')) {
            priority = 1;
          } else if (lowerName.includes('ethernet') && !isVirtual) {
            priority = 2;
          } else if (iface.address.startsWith('192.168.') && !isVirtual) {
            priority = 3;
          } else if (isVirtual) {
            priority = 20;
          }

          validList.push({
            name,
            address: iface.address,
            isVirtual,
            priority
          });
        }
      }
    }
    validList.sort((a, b) => a.priority - b.priority);
    return validList;
  }

  const result = filterAndPrioritize(simulatedInterfaces);
  assert.equal(result.length, 3, 'Doit exclure APIPA (169.254)');
  assert.equal(result[0].name, 'Wi-Fi', 'La carte physique Wi-Fi doit être la première priorité');
  assert.equal(result[0].address, '192.168.1.45', 'L’adresse IP locale sélectionnée doit être celle du Wi-Fi');
  assert.equal(result[0].isVirtual, false, 'Wi-Fi ne doit pas être marqué comme virtuel');

  const virtuals = result.filter(r => r.isVirtual);
  assert.equal(virtuals.length, 2, 'WSL et VirtualBox doivent être identifiés comme virtuels');
});

test('Windows Parity: Résolution et catégorisation des types de fichiers multimédia', () => {
  function getCategory(fileName) {
    const ext = (fileName.split('.').pop() || '').toLowerCase();
    const imageExts = ['jpg', 'jpeg', 'png', 'gif', 'webp', 'svg'];
    const audioExts = ['mp3', 'wav', 'ogg', 'aac', 'flac'];
    const videoExts = ['mp4', 'mkv', 'avi'];
    const codeExts = ['js', 'json', 'py', 'kt', 'txt', 'md'];

    if (imageExts.includes(ext)) return 'IMAGE';
    if (audioExts.includes(ext)) return 'AUDIO';
    if (videoExts.includes(ext)) return 'VIDEO';
    if (codeExts.includes(ext)) return 'CODE';
    return 'DOCUMENT';
  }

  assert.equal(getCategory('vacances.jpg'), 'IMAGE');
  assert.equal(getCategory('musique.mp3'), 'AUDIO');
  assert.equal(getCategory('film.mp4'), 'VIDEO');
  assert.equal(getCategory('config.json'), 'CODE');
  assert.equal(getCategory('rapport.pdf'), 'DOCUMENT');
});

test('Windows Parity: Throttling IPC Anti-Lag (Régulation 150 ms)', () => {
  const emitted = [];
  let lastTime = 0;
  const THROTTLE_MS = 150;

  function throttleProgress(prog, currentTime) {
    const isEdge = prog.percent === 0 || prog.percent >= 100;
    if (isEdge || currentTime - lastTime >= THROTTLE_MS) {
      lastTime = currentTime;
      emitted.push(prog.percent);
    }
  }

  throttleProgress({ percent: 0 }, 0);
  for (let i = 1; i < 999; i++) {
    const simulatedTime = Math.floor(i * 0.05);
    throttleProgress({ percent: (i / 1000) * 100 }, simulatedTime);
  }
  throttleProgress({ percent: 100 }, 55);

  assert.ok(emitted.length <= 3, `Le nombre d'émissions (${emitted.length}) doit être drastiquement régulé`);
  assert.equal(emitted[0], 0, 'La première émission (0%) doit être immédiate');
  assert.equal(emitted[emitted.length - 1], 100, 'La dernière émission (100%) doit être garantie');
});

test('Windows Parity: Persistance atomique de settings.json', () => {
  const tmpDir = path.join(os.tmpdir(), `lancedrop_test_${Date.now()}`);
  fs.mkdirSync(tmpDir, { recursive: true });
  const settingsFile = path.join(tmpDir, 'settings.json');

  const initialSettings = {
    theme: 'light',
    deviceName: 'PC-Test-Windows',
    isOnboarded: true
  };

  const tmpPath = settingsFile + '.tmp';
  fs.writeFileSync(tmpPath, JSON.stringify(initialSettings, null, 2), 'utf8');
  fs.renameSync(tmpPath, settingsFile);

  assert.ok(fs.existsSync(settingsFile));
  const readBack = JSON.parse(fs.readFileSync(settingsFile, 'utf8'));
  assert.equal(readBack.theme, 'light');
  assert.equal(readBack.deviceName, 'PC-Test-Windows');
  assert.equal(readBack.isOnboarded, true);

  fs.rmSync(tmpDir, { recursive: true, force: true });
});
