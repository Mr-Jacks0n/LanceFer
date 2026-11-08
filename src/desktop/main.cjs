/**
 * LanceDrop Desktop — Point d'entrée Electron (CommonJS)
 */
const { app, BrowserWindow, Menu, ipcMain, dialog, clipboard, shell, powerSaveBlocker } = require('electron')
const path = require('path')
const dgram = require('dgram')
const net = require('net')
const fs = require('fs')
const crypto = require('crypto')
const { encryptQrPayload, decryptQrPayload } = require('./crypto_qr.cjs')
const { telemetry } = require('../telemetry/telemetry_manager.cjs')
telemetry.init()

// Suppression complète du menu natif blanc obsolète (File, Edit, etc.)
Menu.setApplicationMenu(null)

let mainWindow = null

function createWindow() {
  const isDark = !appSettings || appSettings.theme !== 'light';
  mainWindow = new BrowserWindow({
    width: 1200,
    height: 780,
    minWidth: 600,
    minHeight: 450,
    title: 'LanceDrop Desktop',
    backgroundColor: isDark ? '#0B0F17' : '#F0F4F9',
    autoHideMenuBar: true,
    show: false,
    webPreferences: {
      preload: path.join(__dirname, 'preload.cjs'),
      nodeIntegration: false,
      contextIsolation: true,
      sandbox: true,
    },
  })

  mainWindow.loadFile(path.join(__dirname, 'ui', 'index.html'))

  mainWindow.once('ready-to-show', () => {
    mainWindow.show()
  })
}

// IPC Handlers pour les contrôles de fenêtre et interactions
ipcMain.on('window:minimize', () => {
  if (mainWindow) mainWindow.minimize()
})

ipcMain.on('window:maximize', () => {
  if (mainWindow) {
    if (mainWindow.isMaximized()) {
      mainWindow.unmaximize()
    } else {
      mainWindow.maximize()
    }
  }
})

ipcMain.on('window:close', () => {
  if (mainWindow) mainWindow.close()
})

ipcMain.handle('dialog:open-files', async () => {
  if (!mainWindow) return []
  const result = await dialog.showOpenDialog(mainWindow, {
    properties: ['openFile', 'multiSelections'],
    title: 'Sélectionner des fichiers à transférer'
  })
  if (result.canceled) return []
  return result.filePaths.map(fp => ({
    name: path.basename(fp),
    path: fp
  }))
})

const os = require('os')
const QRCode = require('qrcode')

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
]

function getAllLanInterfaces() {
  const interfaces = os.networkInterfaces()
  const validList = []

  for (const name of Object.keys(interfaces)) {
    const isVirtual = VIRTUAL_ADAPTER_PATTERNS.some(pat => pat.test(name))
    for (const iface of interfaces[name]) {
      if (iface.family === 'IPv4' && !iface.internal) {
        if (iface.address.startsWith('169.254.') || iface.address.startsWith('127.')) continue

        let priority = 10
        const lowerName = name.toLowerCase()
        if (lowerName.includes('wi-fi') || lowerName.includes('wireless') || lowerName.includes('wlan')) {
          priority = 1
        } else if (lowerName.includes('ethernet') && !isVirtual) {
          priority = 2
        } else if (iface.address.startsWith('192.168.') && !isVirtual) {
          priority = 3
        } else if (iface.address.startsWith('10.') && !isVirtual) {
          priority = 4
        } else if (isVirtual) {
          priority = 20
        }

        validList.push({
          name,
          address: iface.address,
          netmask: iface.netmask,
          mac: iface.mac,
          isVirtual,
          priority
        })
      }
    }
  }

  validList.sort((a, b) => a.priority - b.priority)
  return validList
}

function getLocalIp() {
  const list = getAllLanInterfaces()
  const physical = list.find(item => !item.isVirtual)
  if (physical) return physical.address
  if (list.length > 0) return list[0].address
  return '127.0.0.1'
}

function getSubnetBroadcast(ip, netmask) {
  try {
    const ipParts = ip.split('.').map(Number)
    const maskParts = (netmask || '255.255.255.0').split('.').map(Number)
    if (ipParts.length !== 4 || maskParts.length !== 4) return '255.255.255.255'
    const broadcastParts = ipParts.map((part, i) => (part | (~maskParts[i] & 255)))
    return broadcastParts.join('.')
  } catch (_) {
    return '255.255.255.255'
  }
}

function getAllHostIps() {
  const ipSet = new Set(['127.0.0.1', '::1', '0.0.0.0', '::', 'localhost'])
  try {
    const interfaces = os.networkInterfaces()
    for (const name of Object.keys(interfaces)) {
      for (const iface of interfaces[name]) {
        if (iface.address) {
          const clean = iface.address.replace(/^::ffff:/i, '').trim().toLowerCase()
          ipSet.add(clean)
          ipSet.add(iface.address.toLowerCase())
        }
      }
    }
  } catch (_) {}
  return ipSet
}

let cachedHostIps = getAllHostIps()
let lastHostIpsRefresh = Date.now()

function isLocalAddress(addr) {
  if (!addr) return false
  const now = Date.now()
  if (now - lastHostIpsRefresh > 3000) {
    cachedHostIps = getAllHostIps()
    lastHostIpsRefresh = now
  }
  const clean = addr.replace(/^::ffff:/i, '').trim().toLowerCase()
  if (clean === '127.0.0.1' || clean === '::1' || clean.startsWith('127.') || clean === '0.0.0.0' || clean === '::') {
    return true
  }
  return cachedHostIps.has(clean) || cachedHostIps.has(addr.toLowerCase())
}

function generateQrMatrix(payload) {
  try {
    const qr = QRCode.create(payload, { errorCorrectionLevel: 'H' })
    return {
      size: qr.modules.size,
      data: Array.from(qr.modules.data)
    }
  } catch (err) {
    console.error('Erreur génération QR Code Matrix:', err)
    return null
  }
}

async function generateQrDataUrl(payload) {
  try {
    return await QRCode.toDataURL(payload, {
      width: 280,
      margin: 1,
      color: {
        dark: '#0F172A',
        light: '#FFFFFF'
      },
      errorCorrectionLevel: 'H'
    })
  } catch (err) {
    console.error('Erreur génération QR Code:', err)
    return null
  }
}

let sessionPin = Math.floor(100000 + Math.random() * 900000).toString()
let currentSessionKey = crypto.randomBytes(32).toString('hex')
const HTTP_STREAMING_PORT = 42421
let sharedFolderManager = null
let streamingServer = null

// ─────────────────────────────────────────────────────────────
// Gestionnaire d'Inhibition de Veille OS (Modern Standby Lock)
// ─────────────────────────────────────────────────────────────
let activeTransfersCount = 0
let powerBlockerId = null

function acquirePowerBlocker() {
  activeTransfersCount++
  if (powerBlockerId === null) {
    try {
      powerBlockerId = powerSaveBlocker.start('prevent-app-suspension')
      console.log('[LanceDrop Power] Veille Modern Standby inhibée pendant le transfert (ID:', powerBlockerId, ')')
    } catch (e) {
      console.warn('[LanceDrop Power] Impossible de bloquer la veille:', e.message)
    }
  }
}

function releasePowerBlocker() {
  activeTransfersCount = Math.max(0, activeTransfersCount - 1)
  if (activeTransfersCount === 0 && powerBlockerId !== null) {
    try {
      if (powerSaveBlocker.isStarted(powerBlockerId)) {
        powerSaveBlocker.stop(powerBlockerId)
        console.log('[LanceDrop Power] Suspension Modern Standby rétablie')
      }
    } catch (e) {}
    powerBlockerId = null
  }
}

ipcMain.handle('session:get-qr-info', async () => {
  const ip = getLocalIp()
  const port = 42420
  const deviceName = os.hostname()
  const formattedPin = sessionPin.slice(0, 3) + ' ' + sessionPin.slice(3)
  const plainPayload = JSON.stringify({
    app: 'LanceDrop',
    v: 1,
    type: 'desktop',
    ip: ip,
    port: port,
    httpPort: HTTP_STREAMING_PORT,
    name: deviceName,
    pin: sessionPin,
    sessionKey: currentSessionKey,
    ts: Date.now()
  })
  // Chiffrement cryptographique AES-256 du QR Code : illisible pour caméras et apps tierces
  const encryptedPayload = encryptQrPayload(plainPayload)
  const qrMatrix = generateQrMatrix(encryptedPayload)
  const qrDataUrl = await generateQrDataUrl(encryptedPayload)
  return {
    ip,
    port,
    httpPort: HTTP_STREAMING_PORT,
    deviceName,
    pin: formattedPin,
    sessionKey: currentSessionKey,
    payload: encryptedPayload,
    qrMatrix,
    qrDataUrl
  }
})

ipcMain.handle('session:refresh-pin', async () => {
  sessionPin = Math.floor(100000 + Math.random() * 900000).toString()
  currentSessionKey = crypto.randomBytes(32).toString('hex')
  const ip = getLocalIp()
  const port = 42420
  const deviceName = os.hostname()
  const formattedPin = sessionPin.slice(0, 3) + ' ' + sessionPin.slice(3)
  const plainPayload = JSON.stringify({
    app: 'LanceDrop',
    v: 1,
    type: 'desktop',
    ip: ip,
    port: port,
    httpPort: HTTP_STREAMING_PORT,
    name: deviceName,
    pin: sessionPin,
    sessionKey: currentSessionKey,
    ts: Date.now()
  })
  const encryptedPayload = encryptQrPayload(plainPayload)
  const qrMatrix = generateQrMatrix(encryptedPayload)
  const qrDataUrl = await generateQrDataUrl(encryptedPayload)
  return {
    ip,
    port,
    httpPort: HTTP_STREAMING_PORT,
    deviceName,
    pin: formattedPin,
    sessionKey: currentSessionKey,
    payload: encryptedPayload,
    qrMatrix,
    qrDataUrl
  }
})

// ─────────────────────────────────────────────────────────────
// Gestionnaire des Dossiers Partagés Distants (Remote Explorer)
// ─────────────────────────────────────────────────────────────
ipcMain.handle('get-shared-folders', async () => {
  if (!sharedFolderManager) return []
  return await sharedFolderManager.listFolders(true)
})

ipcMain.handle('add-shared-folder', async (_, folderPath) => {
  if (!sharedFolderManager) return null
  let targetPath = folderPath
  if (!targetPath && mainWindow) {
    const { filePaths } = await dialog.showOpenDialog(mainWindow, {
      title: 'Sélectionner un dossier à partager',
      properties: ['openDirectory']
    })
    if (filePaths && filePaths.length > 0) {
      targetPath = filePaths[0]
    }
  }
  if (!targetPath) return null
  return await sharedFolderManager.addFolder(targetPath)
})

ipcMain.handle('remove-shared-folder', async (_, shareIdOrPath) => {
  if (!sharedFolderManager) return false
  return await sharedFolderManager.removeFolder(shareIdOrPath)
})

ipcMain.handle('qr:decrypt', async (_, encryptedText) => {
  return decryptQrPayload(encryptedText)
})

// ─────────────────────────────────────────────────────────────
// Monitoring & Télémétrie (Sentry & Clarity IPC Handlers)
// ─────────────────────────────────────────────────────────────
ipcMain.handle('telemetry:breadcrumb', async (_, { category, message, data }) => {
  telemetry.addBreadcrumb(category, message, data)
  return true
})

ipcMain.handle('telemetry:capture-error', async (_, { message, stack, context }) => {
  const err = new Error(message || 'Renderer Error')
  if (stack) err.stack = stack
  telemetry.captureException(err, context)
  return true
})

ipcMain.handle('telemetry:get-clarity-config', async () => {
  return telemetry.getClarityConfig()
})

// ─────────────────────────────────────────────────────────────
// Gestion Multi-Appareils & Découverte Réseau Réelle (Zéro Mock Fictif)
// ─────────────────────────────────────────────────────────────
let knownDevices = []
let selectedTargetDeviceId = null
let lastDevicesIpcEmit = 0
let discoveryBeaconTimer = null
let discoveryCleanupTimer = null

function emitDevicesUpdated() {
  lastDevicesIpcEmit = Date.now()
  const myDeviceId = `win_${os.hostname()}`
  knownDevices = knownDevices.filter(d => d && !isLocalAddress(d.ip) && d.id !== myDeviceId && (!appSettings || d.id !== appSettings.deviceId))
  if (selectedTargetDeviceId && !knownDevices.some(d => d.id === selectedTargetDeviceId)) {
    selectedTargetDeviceId = knownDevices.length > 0 ? knownDevices[0].id : null
  }
  if (mainWindow && !mainWindow.isDestroyed()) {
    mainWindow.webContents.send('devices:updated', {
      devices: knownDevices,
      selectedTargetId: selectedTargetDeviceId
    })
  }
}

// Écouteur UDP réel de découverte réseau local (Port 42424)
let udpSocket = null
function initUdpDiscovery() {
  try {
    udpSocket = dgram.createSocket({ type: 'udp4', reuseAddr: true })
    udpSocket.on('error', (err) => {
      console.warn('[LanceDrop UDP] Warning socket:', err.message)
    })

    udpSocket.on('message', (msg, rinfo) => {
      try {
        if (!rinfo || !rinfo.address || isLocalAddress(rinfo.address)) {
          return
        }

        const text = msg.toString('utf8')
        const data = JSON.parse(text)
        if (!data) return

        const myHostname = os.hostname()
        const myDeviceId = `win_${myHostname}`
        if (data.deviceId === myDeviceId || (appSettings && data.deviceId === appSettings.deviceId)) {
          return
        }

        const myDeviceName = (appSettings && appSettings.deviceName) || myHostname
        if (data.deviceName === myDeviceName && data.platform === 'windows') {
          return
        }

        if (data && (data.app === 'LanceDrop' || data.type === 'BEACON' || data.platform || data.magic === 'FD')) {
          const deviceId = data.deviceId || `dev_${rinfo.address.replace(/\./g, '_')}`
          const isPhone = data.platform === 'android' || (data.name && data.name.toLowerCase().includes('phone')) || (data.type === 'mobile')
          const existing = knownDevices.find(d => d.id === deviceId || d.ip === rinfo.address)

          let changed = false
          const now = Date.now()

          if (existing) {
            existing.lastSeen = now
            if (existing.status !== 'online') {
              existing.status = 'online'
              changed = true
            }
            const newName = data.deviceName || data.name || existing.name
            const newPort = data.transferPort || data.port || existing.port
            if (existing.name !== newName || existing.port !== newPort) {
              existing.name = newName
              existing.port = newPort
              changed = true
            }
          } else {
            const newDev = {
              id: deviceId,
              name: data.deviceName || data.name || (isPhone ? `Android-${rinfo.address}` : `PC-${rinfo.address}`),
              type: isPhone ? 'mobile' : 'desktop',
              ip: rinfo.address,
              port: data.transferPort || data.port || 42420,
              pin: data.pin || '',
              status: 'online',
              isTarget: knownDevices.length === 0,
              lastSeen: now
            }
            knownDevices.push(newDev)
            changed = true
            if (!selectedTargetDeviceId) {
              selectedTargetDeviceId = deviceId
            }
            telemetry.addBreadcrumb('discovery', 'Peer discovered', {
              name: newDev.name,
              type: newDev.type,
              ip: newDev.ip
            })
          }

          // Régulation stricte : émettre uniquement si la liste a changé ou si le délai de 1000 ms est écoulé
          if (changed || (now - lastDevicesIpcEmit >= 1000)) {
            emitDevicesUpdated()
          }
        }
      } catch (_) {}
    })

    udpSocket.bind(42424, () => {
      try {
        udpSocket.setBroadcast(true)
      } catch (_) {}
    })

    // Émission périodique du Beacon sur broadcast global et broadcast des sous-réseaux physiques
    discoveryBeaconTimer = setInterval(() => {
      if (!udpSocket) return
      try {
        const beacon = Buffer.from(
          JSON.stringify({
            magic: 'FD',
            app: 'LanceDrop',
            type: 'BEACON',
            deviceId: `win_${os.hostname()}`,
            deviceName: (appSettings && appSettings.deviceName) || os.hostname(),
            platform: 'windows',
            transferPort: (appSettings && appSettings.port) || 42420,
            httpPort: HTTP_STREAMING_PORT,
            ts: Date.now()
          })
        )
        // 1. Diffusion globale
        udpSocket.send(beacon, 0, beacon.length, 42424, '255.255.255.255')

        // 2. Diffusion ciblée sur les cartes réseau physiques réelles
        const validIfaces = getAllLanInterfaces().filter(i => !i.isVirtual)
        for (const iface of validIfaces) {
          const bcast = getSubnetBroadcast(iface.address, iface.netmask)
          if (bcast && bcast !== '255.255.255.255') {
            udpSocket.send(beacon, 0, beacon.length, 42424, bcast)
          }
        }
      } catch (_) {}
    }, 3000)

    // Ramasse-miettes (TTL Garbage Collector) : élague les appareils déconnectés pour éviter les pairs fantômes
    discoveryCleanupTimer = setInterval(() => {
      const now = Date.now()
      let changed = false
      for (let i = knownDevices.length - 1; i >= 0; i--) {
        const dev = knownDevices[i]
        const elapsed = now - (dev.lastSeen || 0)
        // Marquer offline après 10s d'inactivité
        if (dev.status === 'online' && elapsed > 10000) {
          dev.status = 'offline'
          changed = true
        }
        // Purger de la liste après 30s d'inactivité sauf si cible couramment sélectionnée
        if (elapsed > 30000 && dev.id !== selectedTargetDeviceId) {
          knownDevices.splice(i, 1)
          changed = true
        }
      }
      if (changed) {
        emitDevicesUpdated()
      }
    }, 4000)
  } catch (err) {
    console.warn('[LanceDrop Discovery] Impossible de lier UDP 42424:', err.message)
  }
}

initUdpDiscovery()

ipcMain.handle('devices:get-list', async () => {
  const myDeviceId = `win_${os.hostname()}`
  knownDevices = knownDevices.filter(d => d && !isLocalAddress(d.ip) && d.id !== myDeviceId && (!appSettings || d.id !== appSettings.deviceId))
  return {
    devices: knownDevices,
    selectedTargetId: selectedTargetDeviceId
  }
})

ipcMain.handle('devices:select-target', async (_, deviceId) => {
  selectedTargetDeviceId = deviceId
  knownDevices.forEach(d => {
    d.isTarget = (d.id === deviceId)
  })
  if (mainWindow) {
    mainWindow.webContents.send('devices:updated', {
      devices: knownDevices,
      selectedTargetId: selectedTargetDeviceId
    })
  }
  return { success: true, selectedTargetId: selectedTargetDeviceId }
})

ipcMain.handle('devices:add-manual', async (_, newDevice) => {
  if (!newDevice || !newDevice.ip || isLocalAddress(newDevice.ip)) {
    return { success: false, error: 'Impossible d\'ajouter l\'appareil local en tant que pair.' }
  }
  const myDeviceId = `win_${os.hostname()}`
  if (newDevice.id === myDeviceId || (appSettings && newDevice.id === appSettings.deviceId)) {
    return { success: false, error: 'Impossible d\'ajouter l\'appareil local en tant que pair.' }
  }

  const device = {
    id: 'dev_' + Date.now(),
    name: newDevice.name || `Appareil-${newDevice.ip}`,
    type: newDevice.type || (newDevice.name && (newDevice.name.toLowerCase().includes('phone') || newDevice.name.toLowerCase().includes('mobile') || newDevice.name.toLowerCase().includes('galaxy')) ? 'mobile' : 'desktop'),
    ip: newDevice.ip,
    port: parseInt(newDevice.port, 10) || 42420,
    pin: newDevice.pin || '',
    status: 'online',
    isTarget: true,
    lastSeen: Date.now()
  }

  // Désélectionner les autres
  knownDevices.forEach(d => { d.isTarget = false })
  knownDevices.push(device)
  selectedTargetDeviceId = device.id

  if (mainWindow && !mainWindow.isDestroyed()) {
    mainWindow.webContents.send('devices:updated', {
      devices: knownDevices,
      selectedTargetId: selectedTargetDeviceId
    })
  }
  return { success: true, device }
})

ipcMain.handle('devices:remove', async (_, deviceId) => {
  knownDevices = knownDevices.filter(d => d.id !== deviceId)
  if (selectedTargetDeviceId === deviceId) {
    selectedTargetDeviceId = knownDevices.length > 0 ? knownDevices[0].id : null
    if (selectedTargetDeviceId && knownDevices.length > 0) {
      knownDevices[0].isTarget = true
    }
  }
  if (mainWindow) {
    mainWindow.webContents.send('devices:updated', {
      devices: knownDevices,
      selectedTargetId: selectedTargetDeviceId
    })
  }
  return { success: true }
})

ipcMain.handle('devices:scan', async () => {
  // Diffusion broadcast d'un probe UDP sur l'ensemble des sous-réseaux locaux
  try {
    if (udpSocket) {
      const probe = Buffer.from(JSON.stringify({
        magic: 'FD',
        app: 'LanceDrop',
        type: 'BEACON',
        deviceId: `win_${os.hostname()}`,
        deviceName: (appSettings && appSettings.deviceName) || os.hostname(),
        platform: 'windows',
        transferPort: (appSettings && appSettings.port) || 42420,
        httpPort: HTTP_STREAMING_PORT,
        ts: Date.now()
      }))
      // 1. Diffusion globale
      udpSocket.send(probe, 0, probe.length, 42424, '255.255.255.255')

      // 2. Diffusion ciblée sur les cartes réseau physiques réelles
      const validIfaces = getAllLanInterfaces().filter(i => !i.isVirtual)
      for (const iface of validIfaces) {
        const bcast = getSubnetBroadcast(iface.address, iface.netmask)
        if (bcast && bcast !== '255.255.255.255') {
          udpSocket.send(probe, 0, probe.length, 42424, bcast)
        }
      }
    }
  } catch (_) {}

  const myDeviceId = `win_${os.hostname()}`
  knownDevices = knownDevices.filter(d => d && !isLocalAddress(d.ip) && d.id !== myDeviceId && (!appSettings || d.id !== appSettings.deviceId))
  return {
    success: true,
    devices: knownDevices,
    selectedTargetId: selectedTargetDeviceId,
    count: knownDevices.length
  }
})

let connectedDevice = null

ipcMain.handle('device:get-info', async () => {
  const target = knownDevices.find(d => d.id === selectedTargetDeviceId)
  return target || connectedDevice
})

ipcMain.handle('device:get-local', async () => {
  const interfaces = getAllLanInterfaces()
  const physical = interfaces.filter(i => !i.isVirtual)
  const selectedIp = getLocalIp()
  const activeIps = interfaces.filter(i => !i.isVirtual).map(i => i.address)
  if (activeIps.length === 0 && interfaces.length > 0) {
    activeIps.push(interfaces[0].address)
  }
  if (activeIps.length === 0) {
    activeIps.push('127.0.0.1')
  }

  const deviceName = (appSettings && appSettings.deviceName) || os.hostname()
  const platform = os.platform()
  const osType = platform === 'win32' ? 'Windows' : (platform === 'darwin' ? 'macOS' : (platform === 'linux' ? 'Linux' : platform))
  const osRelease = os.release()
  const arch = os.arch()
  const status = physical.length > 0 ? 'Disponible (En ligne)' : (interfaces.length > 0 ? 'Réseau local restreint' : 'Hors ligne')
  const statusKey = physical.length > 0 ? 'online' : (interfaces.length > 0 ? 'restricted' : 'offline')

  return {
    deviceId: `win_${os.hostname()}`,
    deviceName,
    osType: `${osType} (${arch})`,
    osRelease,
    platform: 'windows',
    primaryIp: selectedIp,
    activeIps,
    interfaces,
    port: (appSettings && appSettings.port) || 42420,
    httpPort: HTTP_STREAMING_PORT,
    status,
    statusKey,
    isLocal: true
  }
})

ipcMain.handle('device:set-connected', async (_, device) => {
  connectedDevice = device
  if (mainWindow) {
    mainWindow.webContents.send('device:changed', connectedDevice)
  }
  return { success: true }
})

ipcMain.handle('file:delete', async (_, filename) => {
  return { success: true, filename }
})

// Presse-papier partagé (Clipboard Sharing)
ipcMain.handle('clipboard:read', async () => {
  try {
    return clipboard.readText()
  } catch (err) {
    return ''
  }
})

ipcMain.handle('clipboard:write', async (_, text) => {
  try {
    clipboard.writeText(text || '')
    return { success: true }
  } catch (err) {
    return { success: false, error: err.message }
  }
})

ipcMain.handle('clipboard:send', async (_, text) => {
  const target = knownDevices.find(d => d.id === selectedTargetDeviceId)
  const targetIp = target?.address || target?.ip
  const targetPort = target?.port || 42420

  if (targetIp) {
    try {
      const { encodeFrame } = await import('../protocol/framing.js')
      const { MESSAGE_TYPES } = await import('../protocol/constants.js')
      const client = net.connect({ host: targetIp, port: targetPort })
      await new Promise((res, rej) => {
        client.once('connect', () => {
          client.write(encodeFrame(MESSAGE_TYPES.CLIPBOARD_SHARE, Buffer.from(text || '', 'utf8')))
          setTimeout(() => { client.end(); res(); }, 150)
        })
        client.once('error', rej)
      })
    } catch (e) {
      console.warn('[LanceDrop Clipboard] Échec envoi réseau presse-papier:', e.message)
    }
  }

  return {
    success: true,
    text: text,
    timestamp: Date.now(),
    targetDevice: target ? target.name : 'Mobile connecté'
  }
})

// Configuration & Paramètres
const getSettingsFilePath = () => path.join(app.getPath('userData'), 'settings.json')

let appSettings = {
  downloadPath: path.join(os.homedir(), 'Downloads', 'LanceDrop'),
  port: 42420,
  deviceName: os.hostname(),
  autoStart: false,
  soundNotification: true,
  theme: 'cyber-slate',
  duplicateResolution: 'RENAME',
  isOnboarded: false
}

function loadPersistedSettings() {
  try {
    const sPath = getSettingsFilePath()
    if (fs.existsSync(sPath)) {
      const raw = fs.readFileSync(sPath, 'utf8')
      const parsed = JSON.parse(raw)
      appSettings = { ...appSettings, ...parsed }
      console.log('[LanceDrop Settings] Paramètres chargés depuis le disque :', sPath)
    }
  } catch (err) {
    console.warn('[LanceDrop Settings] Erreur chargement settings.json :', err.message)
  }
}

function savePersistedSettings(newSettings) {
  try {
    appSettings = { ...appSettings, ...newSettings }
    const sPath = getSettingsFilePath()
    const dir = path.dirname(sPath)
    if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true })
    const tmpPath = sPath + '.tmp'
    fs.writeFileSync(tmpPath, JSON.stringify(appSettings, null, 2), 'utf8')
    fs.renameSync(tmpPath, sPath)
    return true
  } catch (err) {
    console.error('[LanceDrop Settings] Erreur sauvegarde settings.json :', err.message)
    return false
  }
}

// Charger immédiatement les paramètres persistés
loadPersistedSettings()

const getHistoryFilePath = () => path.join(app.getPath('userData'), 'transfers_history.json')
let HistoryStoreClass = null
async function getHistoryStore() {
  if (!HistoryStoreClass) {
    const mod = await import('../core/history_store.js')
    HistoryStoreClass = mod.HistoryStore
  }
  return HistoryStoreClass
}

ipcMain.handle('settings:get', async () => appSettings)
ipcMain.handle('settings:save', async (_, newSettings) => {
  savePersistedSettings(newSettings)
  return { success: true, settings: appSettings }
})

ipcMain.handle('network:get-interfaces', async () => {
  return getAllLanInterfaces()
})

ipcMain.handle('network:diagnose', async () => {
  const interfaces = getAllLanInterfaces()
  const physical = interfaces.filter(i => !i.isVirtual)
  return {
    hasPhysicalInterface: physical.length > 0,
    selectedIp: getLocalIp(),
    interfaces,
    tcpPort: appSettings.port,
    udpPort: 42424,
    status: physical.length > 0 ? 'ONLINE' : (interfaces.length > 0 ? 'RESTRICTED' : 'OFFLINE')
  }
})

ipcMain.handle('file:get-preview-data', async (_, filePath) => {
  try {
    if (!filePath || !fs.existsSync(filePath)) {
      return { success: false, error: 'Fichier introuvable' }
    }
    const stat = fs.statSync(filePath)
    if (stat.isDirectory()) {
      return { success: true, isDirectory: true, name: path.basename(filePath), size: stat.size }
    }
    const ext = path.extname(filePath).toLowerCase()
    const name = path.basename(filePath)
    const size = stat.size

    const imageExts = ['.jpg', '.jpeg', '.png', '.gif', '.webp', '.svg', '.bmp', '.ico']
    const audioExts = ['.mp3', '.wav', '.ogg', '.aac', '.m4a', '.flac']
    const textExts = ['.txt', '.json', '.csv', '.log', '.md', '.xml', '.js', '.ts', '.html', '.css', '.py', '.java', '.kt', '.c', '.cpp', '.h', '.sh', '.bat', '.cmd', '.env', '.yaml', '.yml', '.ini', '.cfg']
    const videoExts = ['.mp4', '.mkv', '.webm', '.avi', '.mov']
    const archiveExts = ['.zip', '.rar', '.7z', '.tar', '.gz']
    const docExts = ['.pdf', '.doc', '.docx', '.xls', '.xlsx', '.ppt', '.pptx']

    if (imageExts.includes(ext)) {
      if (size <= 10 * 1024 * 1024) {
        const buf = fs.readFileSync(filePath)
        let mime = 'image/jpeg'
        if (ext === '.png') mime = 'image/png'
        else if (ext === '.gif') mime = 'image/gif'
        else if (ext === '.webp') mime = 'image/webp'
        else if (ext === '.svg') mime = 'image/svg+xml'
        else if (ext === '.bmp') mime = 'image/bmp'
        return {
          success: true,
          category: 'IMAGE',
          name,
          size,
          mime,
          dataUrl: `data:${mime};base64,${buf.toString('base64')}`
        }
      } else {
        return { success: true, category: 'IMAGE', name, size, tooLarge: true }
      }
    }

    if (audioExts.includes(ext)) {
      if (size <= 15 * 1024 * 1024) {
        const buf = fs.readFileSync(filePath)
        let mime = 'audio/mpeg'
        if (ext === '.wav') mime = 'audio/wav'
        else if (ext === '.ogg') mime = 'audio/ogg'
        else if (ext === '.flac') mime = 'audio/flac'
        return {
          success: true,
          category: 'AUDIO',
          name,
          size,
          mime,
          dataUrl: `data:${mime};base64,${buf.toString('base64')}`
        }
      } else {
        return { success: true, category: 'AUDIO', name, size, tooLarge: true }
      }
    }

    if (textExts.includes(ext)) {
      const readSize = Math.min(size, 64 * 1024)
      const fd = fs.openSync(filePath, 'r')
      const buffer = Buffer.alloc(readSize)
      fs.readSync(fd, buffer, 0, readSize, 0)
      fs.closeSync(fd)
      const text = buffer.toString('utf8')
      return {
        success: true,
        category: 'TEXT',
        name,
        size,
        text,
        truncated: size > readSize
      }
    }

    if (videoExts.includes(ext)) return { success: true, category: 'VIDEO', name, size }
    if (archiveExts.includes(ext)) return { success: true, category: 'ARCHIVE', name, size }
    if (docExts.includes(ext)) return { success: true, category: 'DOCUMENT', name, size }

    return { success: true, category: 'GENERIC', name, size }
  } catch (err) {
    return { success: false, error: err.message }
  }
})

ipcMain.handle('dialog:open-directory', async () => {
  const res = await dialog.showOpenDialog(mainWindow, {
    properties: ['openDirectory', 'createDirectory'],
    title: 'Choisir le dossier de téléchargement LanceDrop'
  })
  if (res.canceled || res.filePaths.length === 0) return null
  return res.filePaths[0]
})

// Métadonnées système réelles (Utilisateur OS, Machine, Stockage réel)
ipcMain.handle('system:get-info', async () => {
  try {
    const userInfo = os.userInfo()
    const username = userInfo?.username || process.env.USERNAME || 'Utilisateur'
    const hostname = os.hostname()
    const platform = os.platform()
    const release = os.release()
    const cpus = os.cpus()
    const totalMem = os.totalmem()
    const freeMem = os.freemem()

    let initials = username.slice(0, 2).toUpperCase()
    if (username.includes(' ') || username.includes('.')) {
      const parts = username.split(/[\s.]+/).filter(Boolean)
      if (parts.length >= 2) {
        initials = (parts[0][0] + parts[1][0]).toUpperCase()
      }
    }

    return {
      username,
      initials,
      hostname,
      platform,
      release,
      cpuModel: cpus?.[0]?.model || 'Processeur x64',
      totalMem,
      freeMem,
      downloadPath: appSettings.downloadPath
    }
  } catch (err) {
    return {
      username: os.hostname(),
      initials: 'PC',
      hostname: os.hostname(),
      platform: 'win32',
      release: '10.0',
      downloadPath: appSettings.downloadPath
    }
  }
})

ipcMain.handle('storage:get-info', async () => {
  try {
    const targetDir = appSettings.downloadPath || os.homedir()
    if (!fs.existsSync(targetDir)) {
      fs.mkdirSync(targetDir, { recursive: true })
    }
    const stats = await fs.promises.statfs(targetDir)
    const totalBytes = stats.blocks * stats.bsize
    const freeBytes = stats.bavail * stats.bsize
    const usedBytes = Math.max(0, totalBytes - freeBytes)
    return {
      totalBytes,
      freeBytes,
      usedBytes,
      percentUsed: totalBytes > 0 ? (usedBytes / totalBytes) : 0
    }
  } catch (err) {
    return null
  }
})

// IPC Handlers pour la persistance locale de l'historique et des métriques
ipcMain.handle('history:get', async () => {
  try {
    const store = await getHistoryStore()
    return await store.loadHistory(getHistoryFilePath())
  } catch (err) {
    console.error('[LanceDrop History] Erreur lecture historique:', err)
    return []
  }
})

ipcMain.handle('history:clear', async () => {
  try {
    const store = await getHistoryStore()
    await store.clearHistory(getHistoryFilePath())
    return { success: true }
  } catch (err) {
    return { success: false, error: err.message }
  }
})

ipcMain.handle('history:get-interrupted', async () => {
  try {
    const store = await getHistoryStore()
    return await store.getInterruptedTransfers(getHistoryFilePath())
  } catch (err) {
    return []
  }
})

ipcMain.handle('history:cancel-interrupted', async (_, transferId) => {
  try {
    const store = await getHistoryStore()
    await store.updateHistoryEntry(getHistoryFilePath(), transferId, { status: 'CANCELLED' })
    return { success: true }
  } catch (err) {
    return { success: false, error: err.message }
  }
})

ipcMain.handle('shell:open-file', async (_, targetPath) => {
  try {
    if (!targetPath || !fs.existsSync(targetPath)) return false
    const openErr = await shell.openPath(targetPath)
    if (openErr) {
      console.warn('[LanceDrop Shell] Erreur openPath, affichage dans le dossier:', openErr)
      shell.showItemInFolder(targetPath)
    }
    return true
  } catch (err) {
    console.error('Erreur shell:open-file:', err)
    return false
  }
})

ipcMain.handle('shell:show-in-folder', async (_, targetPath) => {
  try {
    if (!targetPath) return false
    if (fs.existsSync(targetPath)) {
      shell.showItemInFolder(targetPath)
      return true
    } else {
      const dir = path.dirname(targetPath)
      if (fs.existsSync(dir)) {
        await shell.openPath(dir)
        return true
      }
    }
    return false
  } catch (err) {
    console.error('Erreur shell:show-in-folder:', err)
    return false
  }
})

ipcMain.handle('shell:open-path', async (_, targetPath) => {
  try {
    if (!targetPath || !fs.existsSync(targetPath)) return false
    const openErr = await shell.openPath(targetPath)
    if (openErr) {
      shell.showItemInFolder(targetPath)
    }
    return true
  } catch (err) {
    console.error('Erreur shell:open-path:', err)
    return false
  }
})

// ─────────────────────────────────────────────────────────────
// Serveur Récepteur TCP Réel (Port 42420) & Client d'Émission
// ─────────────────────────────────────────────────────────────
let tcpServer = null

async function initTcpServer() {
  try {
    const { TransferReceiver } = await import('../core/transfer_engine.js')

    if (!fs.existsSync(appSettings.downloadPath)) {
      fs.mkdirSync(appSettings.downloadPath, { recursive: true })
    }

    tcpServer = net.createServer((socket) => {
      console.log('[LanceDrop TCP] Connexion entrante de :', socket.remoteAddress)
      const receiver = new TransferReceiver(socket, appSettings.downloadPath, {
        conflictResolution: appSettings.duplicateResolution || 'RENAME',
        sessionKey: currentSessionKey,
        onConflict: async (conflictInfo) => {
          if (appSettings.duplicateResolution && appSettings.duplicateResolution !== 'PROMPT') {
            return appSettings.duplicateResolution
          }
          if (mainWindow) {
            return new Promise((res) => {
              const channel = `transfer:conflict-response-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`
              ipcMain.once(channel, (_, choice) => res(choice))
              mainWindow.webContents.send('transfer:conflict-prompt', {
                ...conflictInfo,
                responseChannel: channel
              })
              setTimeout(() => res('RENAME'), 45000)
            })
          }
          return 'RENAME'
        }
      })

      receiver.on('transfer_started', async (info) => {
        acquirePowerBlocker()
        if (mainWindow) {
          mainWindow.webContents.send('transfer:started', info)
          mainWindow.setProgressBar(0.01)
        }
        try {
          const store = await getHistoryStore()
          await store.addHistoryEntry(getHistoryFilePath(), {
            id: info.transferId,
            fileName: info.fileName,
            filePath: info.filePath,
            peerName: socket.remoteAddress || 'Appareil distant',
            isIncoming: true,
            status: 'INTERRUPTED',
          })
        } catch (_) {}
      })

      // Throttling IPC anti-lag (max 1 émission toutes les 150 ms)
      let lastRxProgressTime = 0
      receiver.on('progress', (progress) => {
        const now = Date.now()
        const isEdge = progress.percent === 0 || progress.percent >= 100 || progress.bytesTransferred === progress.totalBytes
        if (isEdge || now - lastRxProgressTime >= 150) {
          lastRxProgressTime = now
          if (mainWindow) {
            mainWindow.webContents.send('transfer:progress', progress)
            if (typeof progress.percent === 'number') {
              mainWindow.setProgressBar(Math.min(1, Math.max(0, progress.percent / 100)))
            }
          }
        }
      })

      receiver.on('transfer_rejected', (rejectInfo) => {
        releasePowerBlocker()
        if (mainWindow) {
          mainWindow.webContents.send('transfer:rejected', rejectInfo)
          mainWindow.setProgressBar(-1)
        }
      })

      receiver.on('clipboard_received', (clipText) => {
        if (clipText) {
          clipboard.writeText(clipText)
          if (mainWindow) {
            mainWindow.webContents.send('clipboard:received', { text: clipText, timestamp: Date.now() })
          }
        }
      })

      receiver.on('transfer_skipped', () => {
        releasePowerBlocker()
      })

      receiver.on('transfer_completed', async (res) => {
        releasePowerBlocker()
        telemetry.addBreadcrumb('transfer', 'Receive completed', {
          transferId: res.transferId,
          size: res.size,
          filesCount: res.filesCount
        })
        if (mainWindow) {
          mainWindow.webContents.send('transfer:completed', res)
        }
        try {
          const store = await getHistoryStore()
          await store.updateHistoryEntry(getHistoryFilePath(), res.transferId, {
            status: 'COMPLETED',
            fileSize: res.size,
            sha256Checksum: res.sha256,
            filePath: res.filePath,
          })
        } catch (_) {}
      })

      socket.on('close', () => {
        releasePowerBlocker()
        if (mainWindow) {
          mainWindow.setProgressBar(-1)
        }
      })

      receiver.start().catch((err) => {
        releasePowerBlocker()
        telemetry.captureException(err, { context: 'receiver:start' })
        console.error('[LanceDrop TCP] Erreur session réception :', err.message)
        if (mainWindow) {
          mainWindow.webContents.send('transfer:error', { message: err.message })
          mainWindow.setProgressBar(-1)
        }
      })
    })

    tcpServer.on('error', (err) => {
      telemetry.captureException(err, { context: 'tcpServer:error' })
      console.warn('[LanceDrop TCP] Warning:', err.message)
    })

    tcpServer.listen(appSettings.port, '0.0.0.0', () => {
      console.log(`[LanceDrop TCP] Serveur de transfert actif sur le port ${appSettings.port}`)
    })
  } catch (err) {
    telemetry.captureException(err, { context: 'tcpServer:init' })
    console.error('[LanceDrop TCP] Échec lancement serveur TCP :', err)
  }
}

ipcMain.handle('transfer:send-files', async (_, { targetIp, targetPort, filePaths, sessionKey }) => {
  const { TransferSender } = await import('../core/transfer_engine.js')
  const results = []
  const port = parseInt(targetPort, 10) || appSettings.port

  telemetry.addBreadcrumb('transfer', 'Send started', {
    fileCount: filePaths ? filePaths.length : 0,
    targetIp,
    port
  })

  for (const fp of filePaths) {
    acquirePowerBlocker()
    const client = net.connect({ host: targetIp, port })
    await new Promise((res, rej) => {
      client.once('connect', res)
      client.once('error', rej)
    })

    const sender = new TransferSender(client)
    let lastTxProgressTime = 0
    sender.on('progress', (prog) => {
      const now = Date.now()
      const isEdge = prog.percent === 0 || prog.percent >= 100 || prog.bytesTransferred === prog.totalBytes
      if (isEdge || now - lastTxProgressTime >= 150) {
        lastTxProgressTime = now
        if (mainWindow) {
          mainWindow.webContents.send('transfer:progress', prog)
          if (typeof prog.percent === 'number') {
            mainWindow.setProgressBar(Math.min(1, Math.max(0, prog.percent / 100)))
          }
        }
      }
    })

    try {
      const stats = await sender.sendPath(fp, { sessionKey: sessionKey || currentSessionKey })
      results.push(stats)
      telemetry.addBreadcrumb('transfer', 'Send completed', {
        transferId: stats.transferId,
        bytesSent: stats.bytesSent,
        durationMs: stats.durationMs
      })
      if (mainWindow) {
        mainWindow.webContents.send('transfer:completed', stats)
        mainWindow.setProgressBar(-1)
      }
      try {
        const store = await getHistoryStore()
        await store.addHistoryEntry(getHistoryFilePath(), {
          id: stats.transferId,
          fileName: path.basename(fp),
          fileSize: stats.bytesSent,
          filePath: fp,
          peerName: targetIp,
          durationMs: stats.durationMs,
          isIncoming: false,
          status: stats.skipped ? 'SKIPPED' : 'COMPLETED',
          sha256Checksum: stats.sha256,
          isDirectory: Boolean(stats.filesCount && stats.filesCount > 1),
        })
      } catch (_) {}
    } catch (err) {
      telemetry.captureException(err, { context: 'transfer:send-files', targetIp, port })
      if (mainWindow) mainWindow.setProgressBar(-1)
      throw err
    } finally {
      releasePowerBlocker()
      client.end()
    }
  }

  return { success: true, results }
})

async function initStreamingServer() {
  try {
    const { SharedFolderManager } = await import('../streaming/shared_folder_manager.js')
    const { HttpStreamingServer } = await import('../streaming/http_streaming_server.js')
    const { TrustStore } = await import('../security/trust_store.js')

    const storageDir = app.getPath('userData')
    sharedFolderManager = new SharedFolderManager(storageDir)
    await sharedFolderManager.load()

    streamingServer = new HttpStreamingServer({
      port: HTTP_STREAMING_PORT,
      folderManager: sharedFolderManager,
      getSessionToken: () => currentSessionKey,
      getSessionPin: () => sessionPin,
      trustStore: new TrustStore(storageDir)
    })

    await streamingServer.start()
    console.log(`[LanceDrop HTTP] Serveur de streaming multimédia actif sur le port ${streamingServer.port}`)
  } catch (err) {
    telemetry.captureException(err, { context: 'streamingServer:init' })
    console.error('[LanceDrop HTTP] Échec lancement serveur de streaming :', err.message)
  }
}

app.whenReady().then(() => {
  createWindow()
  initTcpServer()
  initStreamingServer()
})

app.on('window-all-closed', () => {
  if (streamingServer) {
    try { streamingServer.stop() } catch (_) {}
    streamingServer = null
  }
  if (discoveryBeaconTimer) {
    clearInterval(discoveryBeaconTimer)
    discoveryBeaconTimer = null
  }
  if (discoveryCleanupTimer) {
    clearInterval(discoveryCleanupTimer)
    discoveryCleanupTimer = null
  }
  if (udpSocket) {
    try { udpSocket.close() } catch (_) {}
    udpSocket = null
  }
  if (process.platform !== 'darwin') app.quit()
})


