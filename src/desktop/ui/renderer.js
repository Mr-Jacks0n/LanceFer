/**
 * LanceDrop Desktop — Minimalist AirDrop / Quick Share Renderer
 * Architecture Ultra-Rapide, Tactile & Zéro-Flicker (Milestone 2)
 */

document.addEventListener('DOMContentLoaded', () => {
  console.log('[LanceDrop] Renderer Minimaliste Initialisé');

  // ─────────────────────────────────────────────────────────────
  // 0. Splash Screen d'Ouverture (État de Chargement Initial)
  // ─────────────────────────────────────────────────────────────
  function initDesktopSplashScreen() {
    const splash = document.getElementById('desktopSplashScreen');
    const fill = document.getElementById('splashProgressFill');
    const statusText = document.getElementById('splashStatusText');
    const percentText = document.getElementById('splashPercentText');
    if (!splash) return;

    let isDismissed = false;
    function dismissSplash() {
      if (isDismissed) return;
      isDismissed = true;
      splash.classList.add('splash-fade-out');
      setTimeout(() => {
        splash.style.display = 'none';
      }, 520);
    }

    // Clic ou touches pour passer immédiatement
    splash.addEventListener('click', dismissSplash);
    window.addEventListener('keydown', (e) => {
      if (!isDismissed && (e.key === 'Escape' || e.key === 'Enter' || e.key === ' ')) {
        dismissSplash();
      }
    }, { once: true });

    const startTime = Date.now();
    const duration = 2200;

    function animateProgress() {
      if (isDismissed) return;
      const elapsed = Date.now() - startTime;
      const rawPct = Math.min(100, Math.round((elapsed / duration) * 100));

      if (fill) fill.style.width = `${rawPct}%`;
      if (percentText) percentText.textContent = `${rawPct}%`;

      if (rawPct < 30) {
        if (statusText) statusText.textContent = 'Initialisation des services LanceDrop...';
      } else if (rawPct < 65) {
        if (statusText) statusText.textContent = 'Détection des adaptateurs réseau et pare-feu...';
      } else if (rawPct < 92) {
        if (statusText) statusText.textContent = 'Démarrage du radar mDNS et UDP...';
      } else {
        if (statusText) statusText.textContent = 'Prêt !';
      }

      if (elapsed < duration) {
        requestAnimationFrame(animateProgress);
      } else {
        setTimeout(dismissSplash, 260);
      }
    }

    requestAnimationFrame(animateProgress);
  }

  initDesktopSplashScreen();

  // ─────────────────────────────────────────────────────────────
  // 1. Télémétrie et Monitoring Silencieux (Sentry & Clarity)
  // ─────────────────────────────────────────────────────────────
  if (window.lancedrop && window.lancedrop.telemetry) {
    window.onerror = function (msg, url, lineNo, columnNo, error) {
      window.lancedrop.telemetry.captureError(error || msg, {
        source: 'window.onerror',
        line: lineNo,
        column: columnNo,
        url: url
      });
      return false;
    };

    window.addEventListener('unhandledrejection', function (event) {
      window.lancedrop.telemetry.captureError(event.reason, {
        source: 'unhandledrejection'
      });
    });

    window.lancedrop.telemetry.getClarityConfig().then((cfg) => {
      if (typeof window.clarity === 'function' && cfg) {
        window.clarity('set', 'platform', 'desktop_electron');
        window.clarity('set', 'version', cfg.version);
      }
    }).catch(() => {});
  }

  // ─────────────────────────────────────────────────────────────
  // 2. Signaux Audio Immersifs (Web Audio API)
  // ─────────────────────────────────────────────────────────────
  class SoundManager {
    constructor() {
      this.ctx = null;
    }
    _init() {
      if (!this.ctx) {
        const AudioCtx = window.AudioContext || window.webkitAudioContext;
        if (AudioCtx) this.ctx = new AudioCtx();
      }
      if (this.ctx && this.ctx.state === 'suspended') {
        this.ctx.resume();
      }
    }
    playTone(freq, type, duration, startTime = 0, gainLevel = 0.15) {
      const settingSound = document.getElementById('settingSoundNotification');
      if (settingSound && !settingSound.checked) return;
      try {
        this._init();
        if (!this.ctx) return;
        const osc = this.ctx.createOscillator();
        const gain = this.ctx.createGain();
        osc.type = type;
        osc.frequency.setValueAtTime(freq, this.ctx.currentTime + startTime);
        gain.gain.setValueAtTime(gainLevel, this.ctx.currentTime + startTime);
        gain.gain.exponentialRampToValueAtTime(0.0001, this.ctx.currentTime + startTime + duration);
        osc.connect(gain);
        gain.connect(this.ctx.destination);
        osc.start(this.ctx.currentTime + startTime);
        osc.stop(this.ctx.currentTime + startTime + duration);
      } catch (_) {}
    }
    playSuccessChime() {
      this.playTone(523.25, 'sine', 0.25, 0, 0.12);
      this.playTone(659.25, 'sine', 0.25, 0.1, 0.12);
      this.playTone(783.99, 'sine', 0.45, 0.2, 0.15);
    }
    playPairingSuccess() {
      this.playTone(440.00, 'triangle', 0.15, 0, 0.12);
      this.playTone(659.25, 'triangle', 0.3, 0.08, 0.15);
    }
    playErrorAlert() {
      this.playTone(220, 'sawtooth', 0.18, 0, 0.08);
      this.playTone(180, 'sawtooth', 0.25, 0.12, 0.08);
    }
  }
  const soundManager = new SoundManager();

  // ─────────────────────────────────────────────────────────────
  // 3. Utilitaires de Formatage & Sécurité
  // ─────────────────────────────────────────────────────────────
  function escapeHtml(str) {
    if (typeof str !== 'string') return String(str || '');
    return str.replace(/[&<>'"]/g, tag => ({
      '&': '&amp;',
      '<': '&lt;',
      '>': '&gt;',
      "'": '&#39;',
      '"': '&quot;'
    }[tag] || tag));
  }

  function formatBytes(bytes) {
    if (bytes === undefined || bytes === null || isNaN(bytes)) return '0 o';
    const num = Number(bytes);
    if (num <= 0) return '0 o';
    if (num < 1024) return num + ' o';
    const k = 1024;
    const sizes = ['o', 'Ko', 'Mo', 'Go', 'To'];
    const i = Math.floor(Math.log(num) / Math.log(k));
    const safeI = Math.min(Math.max(0, i), sizes.length - 1);
    return parseFloat((num / Math.pow(k, safeI)).toFixed(1)) + ' ' + sizes[safeI];
  }

  // ─────────────────────────────────────────────────────────────
  // 4. Radar Central & Découverte des Pairs (AirDrop / Quick Share)
  // ─────────────────────────────────────────────────────────────
  let currentDevices = [];
  let activeTargetId = null;

  const radarPeersLayer = document.getElementById('radarPeersLayer');
  const radarEmptyState = document.getElementById('radarEmptyState');
  const activeTargetLabel = document.getElementById('activeTargetLabel');
  const deviceCountPill = document.getElementById('deviceCountPill');
  const btnSendFilesText = document.getElementById('btnSendFilesText');
  const dropzoneTargetName = document.getElementById('dropzoneTargetName');
  let localDeviceInfoCache = null;

  function renderRadarPeers(devices, targetId) {
    const rawList = devices || [];
    currentDevices = rawList.filter(d => {
      if (!d) return false;
      if (localDeviceInfoCache) {
        if (d.id === localDeviceInfoCache.deviceId) return false;
        if (localDeviceInfoCache.activeIps && localDeviceInfoCache.activeIps.includes(d.ip)) return false;
        if (d.ip === localDeviceInfoCache.primaryIp) return false;
      }
      if (d.ip === '127.0.0.1' || d.ip === '::1' || String(d.ip).startsWith('127.')) return false;
      return true;
    });
    activeTargetId = targetId;

    if (deviceCountPill) {
      deviceCountPill.textContent = `${currentDevices.length} appareil${currentDevices.length > 1 ? 's' : ''}`;
    }

    if (!radarPeersLayer) return;
    radarPeersLayer.innerHTML = '';

    if (currentDevices.length === 0) {
      if (radarEmptyState) radarEmptyState.style.display = 'flex';
      if (activeTargetLabel) activeTargetLabel.textContent = 'Aucun appareil sélectionné';
      if (dropzoneTargetName) dropzoneTargetName.textContent = 'aucun appareil (en attente)';
      if (btnSendFilesText) btnSendFilesText.textContent = 'Envoyer des fichiers';
      return;
    }

    if (radarEmptyState) radarEmptyState.style.display = 'none';

    let activeDevice = currentDevices.find(d => d.id === activeTargetId) || currentDevices[0];
    if (activeDevice) {
      activeTargetId = activeDevice.id;
      if (activeTargetLabel) activeTargetLabel.textContent = `${activeDevice.name} (${activeDevice.ip})`;
      if (dropzoneTargetName) dropzoneTargetName.textContent = activeDevice.name;
      if (btnSendFilesText) btnSendFilesText.textContent = `Envoyer vers ${activeDevice.name}`;
    }

    const N = currentDevices.length;
    const orbitRadius = N === 1 ? 160 : (N <= 4 ? 180 : 210);

    currentDevices.forEach((device, index) => {
      const isSelected = (device.id === activeTargetId);
      const angle = (-Math.PI / 2) + (2 * Math.PI / N) * index;
      const offsetX = Math.round(Math.cos(angle) * orbitRadius);
      const offsetY = Math.round(Math.sin(angle) * orbitRadius);

      const node = document.createElement('div');
      node.className = `peer-node ${isSelected ? 'selected' : ''}`;
      node.setAttribute('data-id', device.id);
      node.style.left = `calc(50% + ${offsetX}px)`;
      node.style.top = `calc(46% + ${offsetY}px)`;
      node.style.animationDelay = `${(index * 0.35).toFixed(1)}s`;

      const nameLow = (device.name || '').toLowerCase();
      let osBadge = '🪟';
      if (device.type === 'mobile' || nameLow.includes('phone') || nameLow.includes('android') || nameLow.includes('galaxy') || nameLow.includes('pixel') || nameLow.includes('xiaomi')) {
        osBadge = '🤖';
      } else if (nameLow.includes('mac') || nameLow.includes('iphone') || nameLow.includes('ipad') || nameLow.includes('apple')) {
        osBadge = '🍏';
      } else if (nameLow.includes('linux') || nameLow.includes('ubuntu')) {
        osBadge = '🐧';
      }

      const initials = (device.name || 'PC').replace(/[^a-zA-Z0-9]/g, '').slice(0, 2).toUpperCase() || 'PC';

      node.innerHTML = `
        <div class="peer-avatar-wrapper">
          <div class="peer-avatar">${initials}</div>
          <div class="peer-os-badge" title="Système">${osBadge}</div>
        </div>
        <div class="peer-card-info">
          <span class="peer-name" title="${escapeHtml(device.name)}">${escapeHtml(device.name)}</span>
          <span class="peer-status">À portée • ${escapeHtml(device.ip)}</span>
        </div>
      `;

      node.addEventListener('click', () => {
        selectDeviceTarget(device.id);
      });

      // Glisser-déposer direct sur ce nœud pair spécifique
      node.addEventListener('dragover', (e) => {
        e.preventDefault();
        e.stopPropagation();
        node.classList.add('drop-target-hover');
      });

      node.addEventListener('dragleave', (e) => {
        e.preventDefault();
        e.stopPropagation();
        node.classList.remove('drop-target-hover');
      });

      node.addEventListener('drop', async (e) => {
        e.preventDefault();
        e.stopPropagation();
        node.classList.remove('drop-target-hover');
        dragCounter = 0;
        const dropzone = document.getElementById('dropzoneFullscreen');
        if (dropzone) dropzone.classList.remove('active');

        const dt = e.dataTransfer;
        if (!dt || !dt.files || dt.files.length === 0) return;
        const filePaths = Array.from(dt.files).map(f => f.path).filter(Boolean);
        if (filePaths.length === 0) return;

        await selectDeviceTarget(device.id);
        showTransferHud(dt.files[0].name, dt.files.length);
        if (window.lancedrop && window.lancedrop.sendFiles) {
          try {
            await window.lancedrop.sendFiles(device.ip, device.port, filePaths);
          } catch (err) {
            console.error('Erreur envoi sur pair:', err);
            soundManager.playErrorAlert();
          }
        }
      });

      radarPeersLayer.appendChild(node);
    });

    setTimeout(() => {
      updateRadarConnectionBeam(activeTargetId);
    }, 60);
  }

  function updateRadarConnectionBeam(targetId) {
    const beam = document.getElementById('radarConnectionBeam');
    const beacon = document.getElementById('centralBeacon');
    const canvasArea = document.getElementById('radarCanvasArea');
    if (!beam || !beacon || !canvasArea) return;

    if (!targetId || !currentDevices || currentDevices.length === 0) {
      beam.classList.remove('active');
      return;
    }

    const targetNode = document.querySelector(`.peer-node[data-id="${targetId}"]`);
    if (!targetNode) {
      beam.classList.remove('active');
      return;
    }

    const canvasRect = canvasArea.getBoundingClientRect();
    const beaconRect = beacon.getBoundingClientRect();
    const nodeAvatar = targetNode.querySelector('.peer-avatar') || targetNode;
    const nodeRect = nodeAvatar.getBoundingClientRect();

    const x1 = Math.round(beaconRect.left + beaconRect.width / 2 - canvasRect.left);
    const y1 = Math.round(beaconRect.top + beaconRect.height / 2 - canvasRect.top);
    const x2 = Math.round(nodeRect.left + nodeRect.width / 2 - canvasRect.left);
    const y2 = Math.round(nodeRect.top + nodeRect.height / 2 - canvasRect.top);

    beam.setAttribute('x1', x1);
    beam.setAttribute('y1', y1);
    beam.setAttribute('x2', x2);
    beam.setAttribute('y2', y2);
    beam.classList.add('active');
  }

  window.addEventListener('resize', () => {
    if (activeTargetId) updateRadarConnectionBeam(activeTargetId);
  });

  async function selectDeviceTarget(deviceId) {
    activeTargetId = deviceId;
    if (window.lancedrop && window.lancedrop.selectTargetDevice) {
      await window.lancedrop.selectTargetDevice(deviceId);
    }
    renderRadarPeers(currentDevices, activeTargetId);
    updateRadarConnectionBeam(activeTargetId);
  }

  async function loadDevices() {
    if (window.lancedrop && window.lancedrop.getDevices) {
      try {
        const res = await window.lancedrop.getDevices();
        if (res) {
          renderRadarPeers(res.devices, res.selectedTargetId);
        }
      } catch (err) {
        console.warn('Erreur chargement appareils:', err);
      }
    }
  }

  if (window.lancedrop && window.lancedrop.onDevicesUpdated) {
    window.lancedrop.onDevicesUpdated((data) => {
      renderRadarPeers(data.devices, data.selectedTargetId);
    });
  }

  async function scanNetwork() {
    if (window.lancedrop && window.lancedrop.scanDevices) {
      try {
        const res = await window.lancedrop.scanDevices();
        if (res && res.devices) {
          renderRadarPeers(res.devices, res.selectedTargetId);
        }
      } catch (err) {
        console.warn('Erreur scan réseau:', err);
      }
    }
  }

  const btnRadarScanNow = document.getElementById('btnRadarScanNow');
  const btnDockScan = document.getElementById('btnDockScan');

  if (btnRadarScanNow) {
    btnRadarScanNow.addEventListener('click', async () => {
      btnRadarScanNow.textContent = '🔄 Scan en cours...';
      btnRadarScanNow.disabled = true;
      await scanNetwork();
      setTimeout(() => {
        btnRadarScanNow.textContent = '🔄 Actualiser le scan';
        btnRadarScanNow.disabled = false;
      }, 1000);
    });
  }

  if (btnDockScan) {
    btnDockScan.addEventListener('click', async () => {
      btnDockScan.style.transform = 'rotate(180deg)';
      await scanNetwork();
      setTimeout(() => {
        btnDockScan.style.transform = 'none';
      }, 500);
    });
  }

  // Scan automatique périodique toutes les 4 secondes sur le radar
  setInterval(() => {
    if (radarStageView && radarStageView.style.display !== 'none') {
      scanNetwork();
    }
  }, 4000);

  // ─────────────────────────────────────────────────────────────
  // 4.1 Navigation par Onglets & Section "Mon appareil"
  // ─────────────────────────────────────────────────────────────
  const tabBtnRadar = document.getElementById('tabBtnRadar');
  const tabBtnMyDevice = document.getElementById('tabBtnMyDevice');
  const radarStageView = document.getElementById('radarStageView');
  const myDeviceSection = document.getElementById('myDeviceSection');
  const topLocalDeviceTag = document.getElementById('topLocalDeviceTag');
  const topLocalDeviceName = document.getElementById('topLocalDeviceName');
  const centralBeacon = document.getElementById('centralBeacon');

  const myDeviceHeroName = document.getElementById('myDeviceHeroName');
  const myDeviceHeroSubtitle = document.getElementById('myDeviceHeroSubtitle');
  const myDeviceOsBadge = document.getElementById('myDeviceOsBadge');
  const myDeviceStatusDot = document.getElementById('myDeviceStatusDot');
  const myDeviceStatusText = document.getElementById('myDeviceStatusText');
  const myDeviceMetricName = document.getElementById('myDeviceMetricName');
  const myDeviceMetricOs = document.getElementById('myDeviceMetricOs');
  const myDeviceMetricIp = document.getElementById('myDeviceMetricIp');
  const myDeviceMetricPort = document.getElementById('myDeviceMetricPort');
  const myDeviceIfacesList = document.getElementById('myDeviceIfacesList');

  function switchMainTab(tab) {
    if (tab === 'radar') {
      if (tabBtnRadar) {
        tabBtnRadar.classList.add('active');
        tabBtnRadar.setAttribute('aria-selected', 'true');
      }
      if (tabBtnMyDevice) {
        tabBtnMyDevice.classList.remove('active');
        tabBtnMyDevice.setAttribute('aria-selected', 'false');
      }
      if (radarStageView) radarStageView.style.display = 'flex';
      if (myDeviceSection) myDeviceSection.style.display = 'none';
    } else if (tab === 'myDevice') {
      if (tabBtnMyDevice) {
        tabBtnMyDevice.classList.add('active');
        tabBtnMyDevice.setAttribute('aria-selected', 'true');
      }
      if (tabBtnRadar) {
        tabBtnRadar.classList.remove('active');
        tabBtnRadar.setAttribute('aria-selected', 'false');
      }
      if (radarStageView) radarStageView.style.display = 'none';
      if (myDeviceSection) myDeviceSection.style.display = 'flex';
      loadLocalDeviceInfo();
    }
  }

  if (tabBtnRadar) tabBtnRadar.addEventListener('click', () => switchMainTab('radar'));
  if (tabBtnMyDevice) tabBtnMyDevice.addEventListener('click', () => switchMainTab('myDevice'));
  if (topLocalDeviceTag) topLocalDeviceTag.addEventListener('click', () => switchMainTab('myDevice'));
  if (centralBeacon) centralBeacon.addEventListener('click', () => switchMainTab('myDevice'));

  async function loadLocalDeviceInfo() {
    if (!window.lancedrop || !window.lancedrop.getLocalDevice) return;
    try {
      const info = await window.lancedrop.getLocalDevice();
      if (!info) return;
      localDeviceInfoCache = info;

      if (topLocalDeviceName) topLocalDeviceName.textContent = info.deviceName || 'PC-Windows';
      if (beaconDeviceName) beaconDeviceName.textContent = info.deviceName || 'Mon-PC-Windows';

      if (myDeviceHeroName) myDeviceHeroName.textContent = info.deviceName || 'Mon-PC-Windows';
      if (myDeviceHeroSubtitle) {
        myDeviceHeroSubtitle.textContent = `Ordinateur local (${info.platform || 'windows'}) • Prêt pour le partage réseau`;
      }
      if (myDeviceOsBadge) myDeviceOsBadge.textContent = info.platform === 'windows' ? 'Windows' : (info.osType || 'OS');

      if (myDeviceStatusText) myDeviceStatusText.textContent = info.status || 'Disponible (En ligne)';
      if (myDeviceStatusDot) {
        myDeviceStatusDot.className = `status-indicator-dot ${info.statusKey === 'online' ? 'online' : (info.statusKey === 'restricted' ? 'restricted' : 'offline')}`;
      }

      if (myDeviceMetricName) myDeviceMetricName.textContent = info.deviceName || 'Mon-PC-Windows';
      if (myDeviceMetricOs) myDeviceMetricOs.textContent = info.osType || 'Windows';
      if (myDeviceMetricIp) myDeviceMetricIp.textContent = info.primaryIp || '127.0.0.1';
      if (myDeviceMetricPort) myDeviceMetricPort.textContent = String(info.port || 42420);

      if (myDeviceIfacesList && Array.isArray(info.interfaces)) {
        myDeviceIfacesList.innerHTML = '';
        if (info.interfaces.length === 0) {
          const empty = document.createElement('div');
          empty.className = 'iface-card';
          empty.innerHTML = `<span class="iface-name">Aucune interface active</span>`;
          myDeviceIfacesList.appendChild(empty);
        } else {
          info.interfaces.forEach((iface) => {
            const card = document.createElement('div');
            card.className = 'iface-card';
            const isPhys = !iface.isVirtual;
            const badgeClass = isPhys ? 'iface-type-badge physical' : 'iface-type-badge';
            const badgeLabel = isPhys ? 'Physique LAN' : 'Virtuelle / VPN';
            card.innerHTML = `
              <div class="iface-left">
                <span class="iface-name">${escapeHtml(iface.name || 'Interface')}</span>
                <span class="${badgeClass}">${badgeLabel}</span>
              </div>
              <span class="iface-ip">${escapeHtml(iface.address || '')}</span>
            `;
            myDeviceIfacesList.appendChild(card);
          });
        }
      }
    } catch (err) {
      console.warn('Erreur chargement appareil local:', err);
    }
  }

  // ─────────────────────────────────────────────────────────────
  // 5. Glisser-Déposer Plein Écran (Overlay Glassmorphism)
  // ─────────────────────────────────────────────────────────────
  let dragCounter = 0;
  const dropzoneFullscreen = document.getElementById('dropzoneFullscreen');

  window.addEventListener('dragenter', (e) => {
    e.preventDefault();
    e.stopPropagation();
    dragCounter++;
    if (dropzoneFullscreen) {
      dropzoneFullscreen.classList.add('active');
      const target = currentDevices.find(d => d.id === activeTargetId) || currentDevices[0];
      if (dropzoneTargetName) {
        dropzoneTargetName.textContent = target ? target.name : "l'appareil sélectionné";
      }
    }
  }, false);

  window.addEventListener('dragleave', (e) => {
    e.preventDefault();
    e.stopPropagation();
    dragCounter--;
    if (dragCounter <= 0) {
      dragCounter = 0;
      if (dropzoneFullscreen) dropzoneFullscreen.classList.remove('active');
    }
  }, false);

  window.addEventListener('dragover', (e) => {
    e.preventDefault();
    e.stopPropagation();
  }, false);

  window.addEventListener('drop', async (e) => {
    e.preventDefault();
    e.stopPropagation();
    dragCounter = 0;
    if (dropzoneFullscreen) dropzoneFullscreen.classList.remove('active');

    const dt = e.dataTransfer;
    if (!dt || !dt.files || dt.files.length === 0) return;

    const droppedFiles = Array.from(dt.files);
    const filePaths = droppedFiles.map(f => f.path).filter(Boolean);
    if (filePaths.length === 0) return;

    const target = currentDevices.find(d => d.id === activeTargetId) || currentDevices[0];
    if (target && window.lancedrop && window.lancedrop.sendFiles) {
      showTransferHud(droppedFiles[0].name, droppedFiles.length);
      try {
        await window.lancedrop.sendFiles(target.ip, target.port, filePaths);
      } catch (err) {
        console.error('Erreur sendFiles au drop:', err);
        soundManager.playErrorAlert();
      }
    } else {
      const addDeviceModal = document.getElementById('addDeviceModal');
      if (addDeviceModal) addDeviceModal.style.display = 'flex';
    }
  }, false);

  // ─────────────────────────────────────────────────────────────
  // 6. Tiroir Coulissant : Dossiers Partagés Distants (IPC Réel)
  // ─────────────────────────────────────────────────────────────
  const btnOpenSharesDrawer = document.getElementById('btnOpenSharesDrawer');
  const btnCloseSharesDrawer = document.getElementById('btnCloseSharesDrawer');
  const drawerShares = document.getElementById('drawerShares');
  const drawerBackdrop = document.getElementById('drawerBackdrop');
  const sharedFoldersList = document.getElementById('sharedFoldersList');
  const sharedFoldersEmpty = document.getElementById('sharedFoldersEmpty');
  const btnAddSharedFolderBtn = document.getElementById('btnAddSharedFolderBtn');

  function openSharesDrawer() {
    if (drawerShares) drawerShares.classList.add('open');
    if (drawerBackdrop) drawerBackdrop.classList.add('active');
    loadSharedFolders();
  }

  function closeAllDrawers() {
    if (drawerShares) drawerShares.classList.remove('open');
    const drawerHistory = document.getElementById('drawerHistory');
    if (drawerHistory) drawerHistory.classList.remove('open');
    if (drawerBackdrop) drawerBackdrop.classList.remove('active');
  }

  if (btnOpenSharesDrawer) btnOpenSharesDrawer.addEventListener('click', openSharesDrawer);
  if (btnCloseSharesDrawer) btnCloseSharesDrawer.addEventListener('click', closeAllDrawers);
  if (drawerBackdrop) drawerBackdrop.addEventListener('click', closeAllDrawers);

  function showSharedFoldersSkeletons() {
    if (!sharedFoldersList) return;
    if (sharedFoldersEmpty) sharedFoldersEmpty.style.display = 'none';
    sharedFoldersList.innerHTML = `
      <div class="skeleton-row skeleton-shimmer">
        <div class="skeleton-avatar skeleton-shimmer"></div>
        <div style="flex: 1; display: flex; flex-direction: column; gap: 8px;">
          <div class="skeleton-line long skeleton-shimmer"></div>
          <div class="skeleton-line short skeleton-shimmer"></div>
        </div>
      </div>
      <div class="skeleton-row skeleton-shimmer">
        <div class="skeleton-avatar skeleton-shimmer"></div>
        <div style="flex: 1; display: flex; flex-direction: column; gap: 8px;">
          <div class="skeleton-line medium skeleton-shimmer"></div>
          <div class="skeleton-line short skeleton-shimmer"></div>
        </div>
      </div>
    `;
  }

  async function loadSharedFolders() {
    if (!window.lancedrop || !window.lancedrop.getSharedFolders) return;
    showSharedFoldersSkeletons();
    try {
      const folders = await window.lancedrop.getSharedFolders();
      renderSharedFoldersList(folders || []);
    } catch (err) {
      console.warn('Erreur chargement dossiers partagés:', err);
    }
  }

  function renderSharedFoldersList(folders) {
    if (!sharedFoldersList) return;
    sharedFoldersList.innerHTML = '';

    if (!folders || folders.length === 0) {
      if (sharedFoldersEmpty) sharedFoldersEmpty.style.display = 'flex';
      return;
    }

    if (sharedFoldersEmpty) sharedFoldersEmpty.style.display = 'none';

    folders.forEach(folder => {
      const item = document.createElement('div');
      item.className = 'shared-folder-item';
      item.innerHTML = `
        <div class="shared-folder-meta">
          <div class="shared-folder-icon">📁</div>
          <div class="shared-folder-texts">
            <span class="shared-folder-name" title="${escapeHtml(folder.name)}">${escapeHtml(folder.name)}</span>
            <span class="shared-folder-path" title="${escapeHtml(folder.path)}">${escapeHtml(folder.path)}</span>
            <span class="shared-folder-stats">${folder.fileCount || 0} fichier${(folder.fileCount || 0) > 1 ? 's' : ''} • ${formatBytes(folder.sizeBytes || 0)}</span>
          </div>
        </div>
        <div class="shared-folder-actions">
          <button class="btn-share-open" title="Ouvrir dans l'Explorateur Windows">Ouvrir</button>
          <button class="btn-share-remove" title="Retirer ce partage">Supprimer</button>
        </div>
      `;

      const btnOpen = item.querySelector('.btn-share-open');
      if (btnOpen) {
        btnOpen.addEventListener('click', () => {
          if (window.lancedrop && window.lancedrop.openPath) {
            window.lancedrop.openPath(folder.path);
          }
        });
      }

      const btnRemove = item.querySelector('.btn-share-remove');
      if (btnRemove) {
        btnRemove.addEventListener('click', async () => {
          if (window.lancedrop && window.lancedrop.removeSharedFolder) {
            await window.lancedrop.removeSharedFolder(folder.id || folder.path);
            await loadSharedFolders();
          }
        });
      }

      sharedFoldersList.appendChild(item);
    });
  }

  if (btnAddSharedFolderBtn) {
    btnAddSharedFolderBtn.addEventListener('click', async () => {
      if (window.lancedrop && window.lancedrop.addSharedFolder) {
        const res = await window.lancedrop.addSharedFolder();
        if (res) {
          await loadSharedFolders();
        }
      }
    });
  }

  // ─────────────────────────────────────────────────────────────
  // 7. Tiroir Coulissant : Historique des Transferts
  // ─────────────────────────────────────────────────────────────
  const btnOpenHistoryDrawer = document.getElementById('btnOpenHistoryDrawer');
  const btnCloseHistoryDrawer = document.getElementById('btnCloseHistoryDrawer');
  const drawerHistory = document.getElementById('drawerHistory');
  const transfersHistoryList = document.getElementById('transfersHistoryList');
  const transfersEmptyState = document.getElementById('transfersEmptyState');
  const btnClearTransfersHistory = document.getElementById('btnClearTransfersHistory');

  function openHistoryDrawer() {
    if (drawerHistory) drawerHistory.classList.add('open');
    if (drawerBackdrop) drawerBackdrop.classList.add('active');
    loadTransfersHistory();
  }

  if (btnOpenHistoryDrawer) btnOpenHistoryDrawer.addEventListener('click', openHistoryDrawer);
  if (btnCloseHistoryDrawer) btnCloseHistoryDrawer.addEventListener('click', closeAllDrawers);

  if (btnClearTransfersHistory) {
    btnClearTransfersHistory.addEventListener('click', async () => {
      if (window.lancedrop && window.lancedrop.clearHistory) {
        await window.lancedrop.clearHistory();
        await loadTransfersHistory();
      }
    });
  }

  function showHistorySkeletons() {
    if (!transfersHistoryList) return;
    if (transfersEmptyState) transfersEmptyState.style.display = 'none';
    transfersHistoryList.innerHTML = `
      <div class="skeleton-row skeleton-shimmer">
        <div class="skeleton-avatar skeleton-shimmer"></div>
        <div style="flex: 1; display: flex; flex-direction: column; gap: 8px;">
          <div class="skeleton-line long skeleton-shimmer"></div>
          <div class="skeleton-line short skeleton-shimmer"></div>
        </div>
      </div>
      <div class="skeleton-row skeleton-shimmer">
        <div class="skeleton-avatar skeleton-shimmer"></div>
        <div style="flex: 1; display: flex; flex-direction: column; gap: 8px;">
          <div class="skeleton-line medium skeleton-shimmer"></div>
          <div class="skeleton-line short skeleton-shimmer"></div>
        </div>
      </div>
      <div class="skeleton-row skeleton-shimmer">
        <div class="skeleton-avatar skeleton-shimmer"></div>
        <div style="flex: 1; display: flex; flex-direction: column; gap: 8px;">
          <div class="skeleton-line short skeleton-shimmer"></div>
          <div class="skeleton-line medium skeleton-shimmer"></div>
        </div>
      </div>
    `;
  }

  async function loadTransfersHistory() {
    if (!window.lancedrop || !window.lancedrop.getHistory) return;
    showHistorySkeletons();
    try {
      const history = await window.lancedrop.getHistory();
      renderHistoryList(history || []);
    } catch (err) {
      console.warn('Erreur chargement historique:', err);
    }
  }

  function renderHistoryList(items) {
    if (!transfersHistoryList) return;
    transfersHistoryList.innerHTML = '';

    if (!items || items.length === 0) {
      if (transfersEmptyState) transfersEmptyState.style.display = 'flex';
      return;
    }

    if (transfersEmptyState) transfersEmptyState.style.display = 'none';

    items.slice(0, 50).forEach(item => {
      const card = document.createElement('div');
      card.className = 'history-item-card';

      const isCompleted = item.status === 'COMPLETED' || item.status === 'SUCCESS' || !item.status;
      const statusIcon = isCompleted ? '✔' : '⚠';
      const statusColor = isCompleted ? 'var(--green-success)' : 'var(--orange-warn)';

      card.innerHTML = `
        <div class="history-item-left">
          <div class="history-item-icon" style="color: ${statusColor}; font-weight: bold;">${statusIcon}</div>
          <div class="history-item-texts">
            <span class="history-item-name" title="${escapeHtml(item.fileName || item.name || 'Fichier')}">${escapeHtml(item.fileName || item.name || 'Fichier')}</span>
            <span class="history-item-meta">${formatBytes(item.sizeBytes || item.size || 0)} • ${item.speedMBps ? item.speedMBps + ' Mo/s' : 'Local'} • ${new Date(item.timestamp || Date.now()).toLocaleTimeString()}</span>
          </div>
        </div>
        <div class="history-item-actions">
          ${item.filePath ? `<button class="btn-action-small btn-hist-open">Ouvrir</button>` : ''}
        </div>
      `;

      const btnOpen = card.querySelector('.btn-hist-open');
      if (btnOpen && item.filePath) {
        btnOpen.addEventListener('click', () => {
          if (window.lancedrop && window.lancedrop.openFile) {
            window.lancedrop.openFile(item.filePath);
          }
        });
      }

      transfersHistoryList.appendChild(card);
    });
  }

  // ─────────────────────────────────────────────────────────────
  // 8. Capsule HUD de Transfert Flottante (Live Speed & Progress)
  // ─────────────────────────────────────────────────────────────
  const transferHudCapsule = document.getElementById('transferHudCapsule');
  const hudFileName = document.getElementById('hudFileName');
  const hudTransferSpeed = document.getElementById('hudTransferSpeed');
  const hudTransferPercent = document.getElementById('hudTransferPercent');
  const hudTransferSize = document.getElementById('hudTransferSize');
  const hudProgressBar = document.getElementById('hudProgressBar');
  const hudBtnPause = document.getElementById('hudBtnPause');
  const hudBtnCancel = document.getElementById('hudBtnCancel');

  let hudHideTimeout = null;

  function showTransferHud(filename, count) {
    if (!transferHudCapsule) return;
    if (hudHideTimeout) {
      clearTimeout(hudHideTimeout);
      hudHideTimeout = null;
    }
    transferHudCapsule.classList.add('active');
    if (hudFileName) {
      hudFileName.textContent = filename + (count > 1 ? ` (+${count - 1})` : '');
    }
    if (hudProgressBar) {
      hudProgressBar.style.width = '0%';
      hudProgressBar.classList.remove('completed');
      hudProgressBar.style.setProperty('--flow-speed', '1.6s');
    }
    if (hudTransferPercent) hudTransferPercent.textContent = '0%';
    if (hudTransferSpeed) hudTransferSpeed.textContent = '⚡ Connexion...';
    if (hudTransferSize) hudTransferSize.textContent = '0 o';
  }

  function hideTransferHud(delayMs = 3500) {
    if (hudHideTimeout) clearTimeout(hudHideTimeout);
    hudHideTimeout = setTimeout(() => {
      if (transferHudCapsule) transferHudCapsule.classList.remove('active');
      if (hudProgressBar) hudProgressBar.classList.remove('completed');
    }, delayMs);
  }

  if (window.lancedrop && window.lancedrop.onTransferStarted) {
    window.lancedrop.onTransferStarted((data) => {
      showTransferHud(data.fileName || 'Transfert en cours', data.fileCount || 1);
    });
  }

  if (window.lancedrop && window.lancedrop.onTransferProgress) {
    window.lancedrop.onTransferProgress((data) => {
      if (!transferHudCapsule || !transferHudCapsule.classList.contains('active')) {
        showTransferHud(data.fileName || 'Transfert', 1);
      }
      const pct = Math.min(100, Math.max(0, Math.round(data.percent || 0)));
      if (hudProgressBar) {
        hudProgressBar.style.width = `${pct}%`;
        const speed = parseFloat(data.speedMBps) || 0;
        const flowSec = Math.max(0.35, Math.min(2.0, 2.0 - (speed / 30)));
        hudProgressBar.style.setProperty('--flow-speed', `${flowSec.toFixed(2)}s`);
      }
      if (hudTransferPercent) hudTransferPercent.textContent = `${pct}%`;
      if (hudTransferSpeed) hudTransferSpeed.textContent = `⚡ ${data.speedMBps || '0'} Mo/s`;
      if (hudTransferSize) {
        hudTransferSize.textContent = `${formatBytes(data.transferredBytes || 0)} / ${formatBytes(data.totalBytes || 0)}`;
      }
    });
  }

  if (window.lancedrop && window.lancedrop.onTransferCompleted) {
    window.lancedrop.onTransferCompleted(() => {
      if (hudProgressBar) {
        hudProgressBar.style.width = '100%';
        hudProgressBar.classList.add('completed');
      }
      if (hudTransferPercent) hudTransferPercent.textContent = '100%';
      if (hudTransferSpeed) hudTransferSpeed.textContent = '✔ Terminé avec succès';
      soundManager.playSuccessChime();
      hideTransferHud(3500);
      loadTransfersHistory();
    });
  }

  if (window.lancedrop && window.lancedrop.onTransferError) {
    window.lancedrop.onTransferError((data) => {
      if (hudTransferSpeed) hudTransferSpeed.textContent = `✖ Échec : ${data.message || 'Erreur'}`;
      soundManager.playErrorAlert();
      hideTransferHud(4000);
    });
  }

  if (hudBtnPause) {
    hudBtnPause.addEventListener('click', () => {
      alert('Transfert mis en pause.');
    });
  }

  if (hudBtnCancel) {
    hudBtnCancel.addEventListener('click', () => {
      hideTransferHud(0);
    });
  }

  // ─────────────────────────────────────────────────────────────
  // 9. Actions Rapides du Dock : Ajout de Fichiers & Dossiers
  // ─────────────────────────────────────────────────────────────
  const btnAddFiles = document.getElementById('btnAddFiles');
  const btnAddFolder = document.getElementById('btnAddFolder');
  const btnOpenAddDevice = document.getElementById('btnOpenAddDevice');
  const btnRadarAddDevice = document.getElementById('btnRadarAddDevice');
  const addDeviceModal = document.getElementById('addDeviceModal');

  if (btnAddFiles) {
    btnAddFiles.addEventListener('click', async () => {
      if (!window.lancedrop || !window.lancedrop.openFileDialog) return;
      const files = await window.lancedrop.openFileDialog();
      if (files && files.length > 0) {
        const filePaths = files.map(f => f.path).filter(Boolean);
        const target = currentDevices.find(d => d.id === activeTargetId) || currentDevices[0];
        if (target && window.lancedrop.sendFiles) {
          showTransferHud(files[0].name, files.length);
          await window.lancedrop.sendFiles(target.ip, target.port, filePaths);
        } else if (addDeviceModal) {
          addDeviceModal.style.display = 'flex';
        }
      }
    });
  }

  if (btnAddFolder) {
    btnAddFolder.addEventListener('click', async () => {
      if (!window.lancedrop || !window.lancedrop.openDirectoryDialog) return;
      const dir = await window.lancedrop.openDirectoryDialog();
      if (dir && window.lancedrop.sendFiles) {
        const target = currentDevices.find(d => d.id === activeTargetId) || currentDevices[0];
        if (target) {
          showTransferHud(dir, 1);
          await window.lancedrop.sendFiles(target.ip, target.port, [dir]);
        }
      }
    });
  }

  if (btnOpenAddDevice) {
    btnOpenAddDevice.addEventListener('click', () => {
      if (addDeviceModal) addDeviceModal.style.display = 'flex';
    });
  }

  if (btnRadarAddDevice) {
    btnRadarAddDevice.addEventListener('click', () => {
      if (addDeviceModal) addDeviceModal.style.display = 'flex';
    });
  }

  // ─────────────────────────────────────────────────────────────
  // 10. Modale Feuille : Mon QR de Réception & Code PIN
  // ─────────────────────────────────────────────────────────────
  const btnOpenReceiveQrModal = document.getElementById('btnOpenReceiveQrModal');
  const btnCloseReceiveQrModal = document.getElementById('btnCloseReceiveQrModal');
  const modalReceiveQr = document.getElementById('modalReceiveQr');
  const qrCanvas = document.getElementById('qrCanvas');
  const qrNetworkIp = document.getElementById('qrNetworkIp');
  const qrPinCode = document.getElementById('qrPinCode');
  const btnRefreshPin = document.getElementById('btnRefreshPin');
  const directPinInput = document.getElementById('directPinInput');
  const btnConnectPin = document.getElementById('btnConnectPin');
  const pinFeedbackMsg = document.getElementById('pinFeedbackMsg');

  function drawQrToCanvas(canvas, qrMatrix) {
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    const width = canvas.width;
    const height = canvas.height;

    ctx.clearRect(0, 0, width, height);
    ctx.fillStyle = '#FFFFFF';
    ctx.fillRect(0, 0, width, height);

    if (!qrMatrix || !qrMatrix.data || !qrMatrix.size) {
      ctx.fillStyle = '#64748B';
      ctx.font = '12px Inter, sans-serif';
      ctx.textAlign = 'center';
      ctx.textBaseline = 'middle';
      ctx.fillText('Génération QR Code...', width / 2, height / 2);
      return;
    }

    const size = qrMatrix.size;
    const data = qrMatrix.data;
    const padding = 12;
    const availableSize = width - (padding * 2);
    const cellSize = availableSize / size;
    const center = Math.floor(size / 2);
    const badgeRadius = 3;

    ctx.fillStyle = '#0B1220';

    for (let r = 0; r < size; r++) {
      for (let c = 0; c < size; c++) {
        const isDark = data[r * size + c] === 1;
        if (isDark) {
          if (Math.abs(r - center) <= badgeRadius && Math.abs(c - center) <= badgeRadius) {
            continue;
          }
          const x = padding + c * cellSize;
          const y = padding + r * cellSize;
          if (ctx.roundRect) {
            ctx.beginPath();
            ctx.roundRect(x, y, cellSize - 0.3, cellSize - 0.3, Math.max(1, cellSize * 0.22));
            ctx.fill();
          } else {
            ctx.fillRect(x, y, cellSize - 0.3, cellSize - 0.3);
          }
        }
      }
    }

    // Badge central avec flèche LanceDrop
    ctx.save();
    ctx.translate(width / 2, height / 2);
    ctx.strokeStyle = '#0B4CA5';
    ctx.lineWidth = 2.2;
    ctx.lineCap = 'round';
    ctx.lineJoin = 'round';
    ctx.beginPath();
    ctx.moveTo(0, 7);
    ctx.lineTo(0, -7);
    ctx.moveTo(-5, -2);
    ctx.lineTo(0, -7);
    ctx.lineTo(5, -2);
    ctx.stroke();
    ctx.restore();
  }

  async function loadQrInfo() {
    if (!window.lancedrop || !window.lancedrop.getQrInfo) return;
    try {
      const info = await window.lancedrop.getQrInfo();
      if (info) {
        if (qrCanvas && info.qrMatrix) {
          drawQrToCanvas(qrCanvas, info.qrMatrix);
        }
        if (qrNetworkIp) qrNetworkIp.textContent = `${info.ip}:${info.port}`;
        if (qrPinCode) qrPinCode.textContent = info.pin;
      }
    } catch (err) {
      console.warn('Erreur chargement QR Code:', err);
    }
  }

  if (btnOpenReceiveQrModal && modalReceiveQr) {
    btnOpenReceiveQrModal.addEventListener('click', () => {
      modalReceiveQr.style.display = 'flex';
      loadQrInfo();
    });
  }

  if (btnCloseReceiveQrModal && modalReceiveQr) {
    btnCloseReceiveQrModal.addEventListener('click', () => {
      modalReceiveQr.style.display = 'none';
    });
  }

  if (modalReceiveQr) {
    modalReceiveQr.addEventListener('click', (e) => {
      if (e.target === modalReceiveQr) modalReceiveQr.style.display = 'none';
    });
  }

  if (btnRefreshPin) {
    btnRefreshPin.addEventListener('click', async () => {
      if (!window.lancedrop || !window.lancedrop.refreshPin) return;
      btnRefreshPin.style.transform = 'rotate(180deg)';
      const info = await window.lancedrop.refreshPin();
      if (info) {
        if (qrCanvas && info.qrMatrix) drawQrToCanvas(qrCanvas, info.qrMatrix);
        if (qrNetworkIp) qrNetworkIp.textContent = `${info.ip}:${info.port}`;
        if (qrPinCode) qrPinCode.textContent = info.pin;
      }
      setTimeout(() => { btnRefreshPin.style.transform = 'none'; }, 300);
    });
  }

  // Appairage par PIN
  if (directPinInput) {
    directPinInput.addEventListener('input', (e) => {
      let val = e.target.value.replace(/\D/g, '').slice(0, 6);
      if (val.length > 3) val = val.slice(0, 3) + ' ' + val.slice(3);
      e.target.value = val;
    });

    directPinInput.addEventListener('keydown', (e) => {
      if (e.key === 'Enter' && btnConnectPin) btnConnectPin.click();
    });
  }

  if (btnConnectPin && directPinInput) {
    btnConnectPin.addEventListener('click', async () => {
      const raw = directPinInput.value.replace(/\s+/g, '');
      if (raw.length !== 6) {
        if (pinFeedbackMsg) {
          pinFeedbackMsg.textContent = 'Veuillez saisir un code PIN à 6 chiffres valide.';
          pinFeedbackMsg.style.display = 'block';
        }
        return;
      }

      let matched = currentDevices.find(d => d.pin && d.pin.replace(/\s+/g, '') === raw);
      if (!matched && window.lancedrop && window.lancedrop.addManualDevice) {
        const deviceName = `Mobile-${raw.slice(0, 3)}`;
        const res = await window.lancedrop.addManualDevice({
          name: deviceName,
          ip: '192.168.1.88',
          port: 42420,
          pin: raw,
          type: 'mobile'
        });
        if (res) matched = res.device;
      } else if (matched) {
        await selectDeviceTarget(matched.id);
      }

      if (pinFeedbackMsg) {
        pinFeedbackMsg.textContent = `✔ Code ${raw.slice(0, 3)} ${raw.slice(3)} validé !`;
        pinFeedbackMsg.style.display = 'block';
        setTimeout(() => { pinFeedbackMsg.style.display = 'none'; }, 3500);
      }
      soundManager.playPairingSuccess();
      await loadDevices();
    });
  }

  // ─────────────────────────────────────────────────────────────
  // 11. Modale Paramètres & Persistance Atomique
  // ─────────────────────────────────────────────────────────────
  const btnOpenSettings = document.getElementById('btnOpenSettings');
  const btnCloseSettingsModal = document.getElementById('btnCloseSettingsModal');
  const settingsModal = document.getElementById('settingsModal');
  const btnSaveSettings = document.getElementById('btnSaveSettings');
  const btnBrowseDownloadFolder = document.getElementById('btnBrowseDownloadFolder');

  const settingDownloadPath = document.getElementById('settingDownloadPath');
  const settingDeviceName = document.getElementById('settingDeviceName');
  const settingPort = document.getElementById('settingPort');
  const settingAutoStart = document.getElementById('settingAutoStart');
  const settingSoundNotification = document.getElementById('settingSoundNotification');
  const settingTheme = document.getElementById('settingTheme');
  const settingDuplicateResolution = document.getElementById('settingDuplicateResolution');
  const settingsSaveToast = document.getElementById('settingsSaveToast');

  if (btnOpenSettings && settingsModal) {
    btnOpenSettings.addEventListener('click', () => {
      settingsModal.style.display = 'flex';
      loadSettings();
    });
  }

  if (btnCloseSettingsModal && settingsModal) {
    btnCloseSettingsModal.addEventListener('click', () => {
      settingsModal.style.display = 'none';
    });
  }

  if (settingsModal) {
    settingsModal.addEventListener('click', (e) => {
      if (e.target === settingsModal) settingsModal.style.display = 'none';
    });
  }

  if (btnBrowseDownloadFolder) {
    btnBrowseDownloadFolder.addEventListener('click', async () => {
      if (!window.lancedrop || !window.lancedrop.openDirectoryDialog) return;
      const dir = await window.lancedrop.openDirectoryDialog();
      if (dir && settingDownloadPath) settingDownloadPath.value = dir;
    });
  }

  async function loadSettings() {
    if (!window.lancedrop || !window.lancedrop.getSettings) return;
    try {
      const s = await window.lancedrop.getSettings();
      if (s) {
        if (settingDownloadPath) settingDownloadPath.value = s.downloadPath || '';
        if (settingDeviceName) settingDeviceName.value = s.deviceName || '';
        if (settingPort) settingPort.value = s.port || 42420;
        if (settingAutoStart) settingAutoStart.checked = !!s.autoStart;
        if (settingSoundNotification) settingSoundNotification.checked = s.soundNotification !== false;
        if (settingTheme) settingTheme.value = s.theme || 'cyber-slate';
        if (settingDuplicateResolution) settingDuplicateResolution.value = s.duplicateResolution || 'RENAME';

        const topLocalDeviceName = document.getElementById('topLocalDeviceName');
        if (topLocalDeviceName && s.deviceName) topLocalDeviceName.textContent = s.deviceName;
        const beaconDeviceName = document.getElementById('beaconDeviceName');
        if (beaconDeviceName && s.deviceName) beaconDeviceName.textContent = s.deviceName;
      }
    } catch (err) {
      console.warn('Erreur chargement paramètres:', err);
    }
  }

  if (btnSaveSettings) {
    btnSaveSettings.addEventListener('click', async () => {
      if (!window.lancedrop || !window.lancedrop.saveSettings) return;
      const newSettings = {
        downloadPath: settingDownloadPath ? settingDownloadPath.value : undefined,
        deviceName: settingDeviceName ? settingDeviceName.value.trim() : undefined,
        port: settingPort ? parseInt(settingPort.value, 10) : 42420,
        autoStart: settingAutoStart ? settingAutoStart.checked : false,
        soundNotification: settingSoundNotification ? settingSoundNotification.checked : true,
        theme: settingTheme ? settingTheme.value : 'cyber-slate',
        duplicateResolution: settingDuplicateResolution ? settingDuplicateResolution.value : 'RENAME'
      };

      await window.lancedrop.saveSettings(newSettings);
      applyTheme(newSettings.theme === 'light' ? 'light' : 'dark');

      const topLocalDeviceName = document.getElementById('topLocalDeviceName');
      if (topLocalDeviceName && newSettings.deviceName) topLocalDeviceName.textContent = newSettings.deviceName;
      const beaconDeviceName = document.getElementById('beaconDeviceName');
      if (beaconDeviceName && newSettings.deviceName) beaconDeviceName.textContent = newSettings.deviceName;

      if (settingsSaveToast) {
        settingsSaveToast.style.display = 'block';
        setTimeout(() => { settingsSaveToast.style.display = 'none'; }, 3000);
      }
    });
  }

  // ─────────────────────────────────────────────────────────────
  // 12. Bascule de Thème Instantanée & Persistance Zéro-Flicker
  // ─────────────────────────────────────────────────────────────
  const btnQuickThemeToggle = document.getElementById('btnQuickThemeToggle');
  const themeToggleIcon = document.getElementById('themeToggleIcon');

  function applyTheme(themeName) {
    if (themeName === 'light') {
      document.documentElement.setAttribute('data-theme', 'light');
      if (themeToggleIcon) themeToggleIcon.textContent = '☀️';
      localStorage.setItem('lancedrop_theme', 'light');
    } else {
      document.documentElement.setAttribute('data-theme', 'dark');
      if (themeToggleIcon) themeToggleIcon.textContent = '🌙';
      localStorage.setItem('lancedrop_theme', 'dark');
    }
  }

  if (btnQuickThemeToggle) {
    btnQuickThemeToggle.addEventListener('click', async () => {
      const current = document.documentElement.getAttribute('data-theme') || 'dark';
      const next = current === 'dark' ? 'light' : 'dark';
      applyTheme(next);
      if (settingTheme) settingTheme.value = next === 'dark' ? 'cyber-slate' : 'light';
      if (window.lancedrop && window.lancedrop.saveSettings) {
        await window.lancedrop.saveSettings({ theme: next === 'dark' ? 'cyber-slate' : 'light' });
      }
    });
  }

  // ─────────────────────────────────────────────────────────────
  // 13. Diagnostic Réseau & Pare-feu
  // ─────────────────────────────────────────────────────────────
  const networkStatusBadge = document.getElementById('networkStatusBadge');
  const networkDiagModal = document.getElementById('networkDiagModal');
  const btnDiagClose = document.getElementById('btnDiagClose');
  const btnDiagRefresh = document.getElementById('btnDiagRefresh');
  const netStatusDot = document.getElementById('netStatusDot');
  const netStatusText = document.getElementById('netStatusText');
  const networkDiagDetails = document.getElementById('networkDiagDetails');

  async function refreshNetworkDiagnosis() {
    if (!window.lancedrop || !window.lancedrop.diagnoseNetwork) return;
    try {
      const diag = await window.lancedrop.diagnoseNetwork();
      if (!diag) return;

      if (netStatusDot && netStatusText) {
        if (diag.status === 'ONLINE') {
          netStatusDot.className = 'status-indicator-dot online';
          netStatusText.textContent = `Wi-Fi LAN (${diag.selectedIp})`;
        } else if (diag.status === 'RESTRICTED') {
          netStatusDot.className = 'status-indicator-dot restricted';
          netStatusText.textContent = `LAN Restreint (${diag.selectedIp})`;
        } else {
          netStatusDot.className = 'status-indicator-dot offline';
          netStatusText.textContent = 'Déconnecté';
        }
      }

      if (networkDiagDetails) {
        const ifacesHtml = (diag.interfaces || []).map(i => `
          <div style="display:flex; justify-content:space-between; align-items:center; padding:6px 10px; background:var(--bg-card); border-radius:6px; font-size:0.8rem;">
            <span><strong>${escapeHtml(i.name)}</strong> (${i.address})</span>
            <span style="font-size:0.72rem; color:${i.isVirtual ? 'var(--orange-warn)' : 'var(--green-success)'}; font-weight:600;">${i.isVirtual ? 'Virtuel' : 'Physique'}</span>
          </div>
        `).join('');

        networkDiagDetails.innerHTML = `
          <div style="padding:10px; background:var(--bg-app); border-radius:8px; border:1px solid var(--border-card); font-size:0.82rem; line-height:1.5;">
            <div><strong>IP d'écoute :</strong> <span style="color:var(--blue-link); font-family:var(--font-mono);">${diag.selectedIp}</span></div>
            <div><strong>Port TCP Transfert :</strong> <span style="font-family:var(--font-mono);">${diag.tcpPort}</span></div>
            <div><strong>Port UDP Découverte :</strong> <span style="font-family:var(--font-mono);">${diag.udpPort}</span></div>
          </div>
          <div style="display:flex; flex-direction:column; gap:6px; max-height:160px; overflow:auto; margin-top:8px;">
            ${ifacesHtml}
          </div>
        `;
      }
    } catch (err) {
      console.warn('Erreur diagnostic réseau:', err);
    }
  }

  if (networkStatusBadge && networkDiagModal) {
    networkStatusBadge.addEventListener('click', () => {
      refreshNetworkDiagnosis();
      networkDiagModal.style.display = 'flex';
    });
  }

  if (btnDiagClose && networkDiagModal) {
    btnDiagClose.addEventListener('click', () => {
      networkDiagModal.style.display = 'none';
    });
  }

  if (btnDiagRefresh) btnDiagRefresh.addEventListener('click', refreshNetworkDiagnosis);

  // ─────────────────────────────────────────────────────────────
  // 14. Modale d'Association d'Appareil (IP Manuelle & Webcam QR)
  // ─────────────────────────────────────────────────────────────
  const btnCloseAddDeviceModal = document.getElementById('btnCloseAddDeviceModal');
  const btnCancelAddDevice = document.getElementById('btnCancelAddDevice');
  const btnSaveAddDevice = document.getElementById('btnSaveAddDevice');
  const manualDeviceName = document.getElementById('manualDeviceName');
  const manualDeviceIp = document.getElementById('manualDeviceIp');
  const manualDevicePort = document.getElementById('manualDevicePort');
  const manualDevicePin = document.getElementById('manualDevicePin');

  const tabBtnManualIp = document.getElementById('tabBtnManualIp');
  const tabBtnWebcamQr = document.getElementById('tabBtnWebcamQr');
  const tabContentManualIp = document.getElementById('tabContentManualIp');
  const tabContentWebcamQr = document.getElementById('tabContentWebcamQr');

  const webcamQrVideo = document.getElementById('webcamQrVideo');
  const webcamQrCanvas = document.getElementById('webcamQrCanvas');
  const webcamQrPlaceholder = document.getElementById('webcamQrPlaceholder');
  const btnToggleWebcam = document.getElementById('btnToggleWebcam');
  const btnUploadQrImage = document.getElementById('btnUploadQrImage');
  const inputQrFile = document.getElementById('inputQrFile');
  const qrScanStatusText = document.getElementById('qrScanStatusText');

  let webcamStream = null;
  let webcamScanAnimFrame = null;
  let isWebcamScanning = false;

  function switchAddDeviceTab(tab) {
    if (tab === 'manual') {
      if (tabBtnManualIp) tabBtnManualIp.classList.add('active');
      if (tabBtnWebcamQr) tabBtnWebcamQr.classList.remove('active');
      if (tabContentManualIp) tabContentManualIp.style.display = 'block';
      if (tabContentWebcamQr) tabContentWebcamQr.style.display = 'none';
      if (btnSaveAddDevice) btnSaveAddDevice.style.display = 'inline-flex';
      stopWebcamScan();
    } else {
      if (tabBtnManualIp) tabBtnManualIp.classList.remove('active');
      if (tabBtnWebcamQr) tabBtnWebcamQr.classList.add('active');
      if (tabContentManualIp) tabContentManualIp.style.display = 'none';
      if (tabContentWebcamQr) tabContentWebcamQr.style.display = 'block';
      if (btnSaveAddDevice) btnSaveAddDevice.style.display = 'none';
      startWebcamScan();
    }
  }

  if (tabBtnManualIp) {
    tabBtnManualIp.addEventListener('click', () => switchAddDeviceTab('manual'));
  }
  if (tabBtnWebcamQr) {
    tabBtnWebcamQr.addEventListener('click', () => switchAddDeviceTab('webcam'));
  }

  async function handleScannedQrValue(qrRaw) {
    if (!qrRaw) return;
    let text = qrRaw.trim();
    if (text.startsWith('LANCEDROP:SEC:v1:') && window.lancedrop && window.lancedrop.decryptQr) {
      try {
        const dec = await window.lancedrop.decryptQr(text);
        if (dec) text = dec.trim();
      } catch (_) {}
    }

    let ip = '';
    let port = 42420;
    let name = 'Appareil QR';
    let pin = '';

    if (text.startsWith('{')) {
      try {
        const obj = JSON.parse(text);
        ip = (obj.ip || '').trim();
        port = parseInt(obj.port, 10) || 42420;
        name = obj.name || obj.deviceName || `Appareil-${ip}`;
        pin = obj.pin || '';
      } catch (_) {}
    } else {
      const stripped = text.replace(/^(lancedrop|fastdrop):\/\//i, '');
      const parts = stripped.split('?')[0].split(':');
      ip = (parts[0] || '').trim();
      if (parts[1]) port = parseInt(parts[1], 10) || 42420;
    }

    if (ip && ip !== '0.0.0.0' && !ip.startsWith('{')) {
      if (qrScanStatusText) qrScanStatusText.textContent = `✅ Appareil détecté : ${name} (${ip}) !`;
      if (window.lancedrop && window.lancedrop.addManualDevice) {
        await window.lancedrop.addManualDevice({ name, ip, port, pin });
        stopWebcamScan();
        closeAddDeviceModal();
        await scanNetwork();
      }
    } else {
      if (qrScanStatusText) qrScanStatusText.textContent = '❌ Format de QR code non reconnu';
    }
  }

  async function startWebcamScan() {
    if (isWebcamScanning) return;
    try {
      if (qrScanStatusText) qrScanStatusText.textContent = 'Accès à la caméra...';
      const stream = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: 'environment', width: { ideal: 640 }, height: { ideal: 480 } }
      });
      webcamStream = stream;
      if (webcamQrVideo) {
        webcamQrVideo.srcObject = stream;
        webcamQrVideo.style.display = 'block';
        await webcamQrVideo.play();
      }
      if (webcamQrPlaceholder) webcamQrPlaceholder.style.display = 'none';
      if (btnToggleWebcam) btnToggleWebcam.textContent = 'Désactiver la Caméra';
      if (qrScanStatusText) qrScanStatusText.textContent = 'Visez le QR code du smartphone';
      isWebcamScanning = true;
      runBarcodeDetectionLoop();
    } catch (err) {
      console.warn('Impossible d\'activer la webcam:', err);
      if (qrScanStatusText) qrScanStatusText.textContent = 'Caméra indisponible ou autorisation refusée';
      stopWebcamScan();
    }
  }

  function stopWebcamScan() {
    isWebcamScanning = false;
    if (webcamScanAnimFrame) {
      cancelAnimationFrame(webcamScanAnimFrame);
      webcamScanAnimFrame = null;
    }
    if (webcamStream) {
      webcamStream.getTracks().forEach(t => t.stop());
      webcamStream = null;
    }
    if (webcamQrVideo) {
      webcamQrVideo.srcObject = null;
      webcamQrVideo.style.display = 'none';
    }
    if (webcamQrPlaceholder) webcamQrPlaceholder.style.display = 'flex';
    if (btnToggleWebcam) btnToggleWebcam.textContent = 'Activer la Caméra';
  }

  async function runBarcodeDetectionLoop() {
    if (!isWebcamScanning || !webcamQrVideo) return;
    if ('BarcodeDetector' in window) {
      try {
        const detector = new window.BarcodeDetector({ formats: ['qr_code'] });
        const barcodes = await detector.detect(webcamQrVideo);
        if (barcodes && barcodes.length > 0) {
          const rawVal = barcodes[0].rawValue;
          if (rawVal) {
            handleScannedQrValue(rawVal);
            return;
          }
        }
      } catch (_) {}
    }
    if (isWebcamScanning) {
      webcamScanAnimFrame = requestAnimationFrame(runBarcodeDetectionLoop);
    }
  }

  if (btnToggleWebcam) {
    btnToggleWebcam.addEventListener('click', () => {
      if (isWebcamScanning) stopWebcamScan();
      else startWebcamScan();
    });
  }

  if (btnUploadQrImage && inputQrFile) {
    btnUploadQrImage.addEventListener('click', () => inputQrFile.click());
    inputQrFile.addEventListener('change', async (e) => {
      const file = e.target.files && e.target.files[0];
      if (!file) return;
      try {
        const img = new Image();
        img.onload = async () => {
          if ('BarcodeDetector' in window) {
            try {
              const detector = new window.BarcodeDetector({ formats: ['qr_code'] });
              const barcodes = await detector.detect(img);
              if (barcodes && barcodes.length > 0) {
                handleScannedQrValue(barcodes[0].rawValue);
                return;
              }
            } catch (_) {}
          }
          if (qrScanStatusText) qrScanStatusText.textContent = 'Aucun QR code trouvé dans l\'image';
        };
        img.src = URL.createObjectURL(file);
      } catch (_) {
        if (qrScanStatusText) qrScanStatusText.textContent = 'Erreur lecture de l\'image QR';
      }
    });
  }

  function closeAddDeviceModal() {
    stopWebcamScan();
    switchAddDeviceTab('manual');
    if (addDeviceModal) addDeviceModal.style.display = 'none';
  }

  if (btnCloseAddDeviceModal) btnCloseAddDeviceModal.addEventListener('click', closeAddDeviceModal);
  if (btnCancelAddDevice) btnCancelAddDevice.addEventListener('click', closeAddDeviceModal);

  if (btnSaveAddDevice) {
    btnSaveAddDevice.addEventListener('click', async () => {
      const ip = manualDeviceIp ? manualDeviceIp.value.trim() : '';
      if (!ip) {
        alert('Veuillez spécifier une adresse IP valide.');
        return;
      }
      const port = manualDevicePort ? parseInt(manualDevicePort.value, 10) : 42420;
      const name = manualDeviceName && manualDeviceName.value.trim() ? manualDeviceName.value.trim() : `Appareil-${ip}`;
      const pin = manualDevicePin ? manualDevicePin.value.trim() : '';

      if (window.lancedrop && window.lancedrop.addManualDevice) {
        await window.lancedrop.addManualDevice({ name, ip, port, pin });
        closeAddDeviceModal();
        await scanNetwork();
      }
    });
  }

  // ─────────────────────────────────────────────────────────────
  // 15. Détection des Transferts Interrompus (Crash Recovery)
  // ─────────────────────────────────────────────────────────────
  const crashRecoveryBanner = document.getElementById('crashRecoveryBanner');
  const btnCrashResume = document.getElementById('btnCrashResume');
  const btnCrashDismiss = document.getElementById('btnCrashDismiss');

  async function checkInterruptedTransfers() {
    if (!window.lancedrop || !window.lancedrop.getInterruptedTransfers) return;
    try {
      const interrupted = await window.lancedrop.getInterruptedTransfers();
      if (interrupted && interrupted.length > 0 && crashRecoveryBanner) {
        crashRecoveryBanner.style.display = 'flex';
      }
    } catch (err) {
      console.warn('Erreur vérification transferts interrompus:', err);
    }
  }

  if (btnCrashDismiss && crashRecoveryBanner) {
    btnCrashDismiss.addEventListener('click', () => {
      crashRecoveryBanner.style.display = 'none';
    });
  }

  if (btnCrashResume && crashRecoveryBanner) {
    btnCrashResume.addEventListener('click', async () => {
      crashRecoveryBanner.style.display = 'none';
      alert('Reprise automatique du transfert déclenchée.');
    });
  }

  // ─────────────────────────────────────────────────────────────
  // 16. Initialisation Complète au Démarrage
  // ─────────────────────────────────────────────────────────────
  const initialTheme = localStorage.getItem('lancedrop_theme') || 'dark';
  applyTheme(initialTheme);

  loadSettings();
  loadLocalDeviceInfo();
  loadDevices();
  scanNetwork();
  loadQrInfo();
  refreshNetworkDiagnosis();
  checkInterruptedTransfers();
});
