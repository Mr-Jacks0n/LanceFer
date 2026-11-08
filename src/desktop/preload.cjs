/**
 * LanceDrop Desktop — Preload Bridge (CommonJS)
 */
const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('lancedrop', {
  minimize: () => ipcRenderer.send('window:minimize'),
  maximize: () => ipcRenderer.send('window:maximize'),
  close: () => ipcRenderer.send('window:close'),
  openFileDialog: () => ipcRenderer.invoke('dialog:open-files'),
  deleteFile: (filename) => ipcRenderer.invoke('file:delete', filename),
  getConnectedDevice: () => ipcRenderer.invoke('device:get-info'),
  getLocalDevice: () => ipcRenderer.invoke('device:get-local'),
  onTransferProgress: (cb) => ipcRenderer.on('transfer:progress', (_, data) => cb(data)),
  readClipboard: () => ipcRenderer.invoke('clipboard:read'),
  writeClipboard: (text) => ipcRenderer.invoke('clipboard:write', text),
  sendClipboardText: (text) => ipcRenderer.invoke('clipboard:send', text),
  onClipboardReceived: (cb) => ipcRenderer.on('clipboard:received', (_, data) => cb(data)),
  onDeviceChanged: (cb) => ipcRenderer.on('device:changed', (_, data) => cb(data)),
  getQrInfo: () => ipcRenderer.invoke('session:get-qr-info'),
  refreshPin: () => ipcRenderer.invoke('session:refresh-pin'),
  getDevices: () => ipcRenderer.invoke('devices:get-list'),
  selectTargetDevice: (id) => ipcRenderer.invoke('devices:select-target', id),
  addManualDevice: (device) => ipcRenderer.invoke('devices:add-manual', device),
  removeDevice: (id) => ipcRenderer.invoke('devices:remove', id),
  scanDevices: () => ipcRenderer.invoke('devices:scan'),
  onDevicesUpdated: (cb) => ipcRenderer.on('devices:updated', (_, data) => cb(data)),
  openDirectoryDialog: () => ipcRenderer.invoke('dialog:open-directory'),
  getSettings: () => ipcRenderer.invoke('settings:get'),
  saveSettings: (settings) => ipcRenderer.invoke('settings:save', settings),
  getSharedFolders: () => ipcRenderer.invoke('get-shared-folders'),
  addSharedFolder: (folderPath) => ipcRenderer.invoke('add-shared-folder', folderPath),
  removeSharedFolder: (shareIdOrPath) => ipcRenderer.invoke('remove-shared-folder', shareIdOrPath),
  decryptQr: (enc) => ipcRenderer.invoke('qr:decrypt', enc),
  sendFiles: (targetIp, targetPort, filePaths) => ipcRenderer.invoke('transfer:send-files', { targetIp, targetPort, filePaths }),
  onTransferStarted: (cb) => ipcRenderer.on('transfer:started', (_, data) => cb(data)),
  onTransferCompleted: (cb) => ipcRenderer.on('transfer:completed', (_, data) => cb(data)),
  onTransferError: (cb) => ipcRenderer.on('transfer:error', (_, data) => cb(data)),
  getHistory: () => ipcRenderer.invoke('history:get'),
  clearHistory: () => ipcRenderer.invoke('history:clear'),
  getInterruptedTransfers: () => ipcRenderer.invoke('history:get-interrupted'),
  cancelInterruptedTransfer: (transferId) => ipcRenderer.invoke('history:cancel-interrupted', transferId),
  openPath: (targetPath) => ipcRenderer.invoke('shell:open-path', targetPath),
  openFile: (targetPath) => ipcRenderer.invoke('shell:open-file', targetPath),
  showInFolder: (targetPath) => ipcRenderer.invoke('shell:show-in-folder', targetPath),
  getLanInterfaces: () => ipcRenderer.invoke('network:get-interfaces'),
  diagnoseNetwork: () => ipcRenderer.invoke('network:diagnose'),
  getPreviewData: (filePath) => ipcRenderer.invoke('file:get-preview-data', filePath),
  onConflictPrompt: (cb) => ipcRenderer.on('transfer:conflict-prompt', (_, data) => cb(data)),
  resolveConflict: (channel, choice) => ipcRenderer.send(channel, choice),
  getSystemInfo: () => ipcRenderer.invoke('system:get-info'),
  getStorageInfo: () => ipcRenderer.invoke('storage:get-info'),
  telemetry: {
    addBreadcrumb: (category, message, data) => ipcRenderer.invoke('telemetry:breadcrumb', { category, message, data }),
    captureError: (err, context) => ipcRenderer.invoke('telemetry:capture-error', { message: err?.message || String(err), stack: err?.stack, context }),
    getClarityConfig: () => ipcRenderer.invoke('telemetry:get-clarity-config')
  }
});

// Rétrocompatibilité
contextBridge.exposeInMainWorld('fastdrop', {
  getPeers: () => ipcRenderer.invoke('device:get-info'),
  confirmPin: (ok) => ipcRenderer.invoke('node:confirm-pin', ok),
  openDownloads: () => ipcRenderer.invoke('node:open-downloads'),
  onPeerFound: (cb) => ipcRenderer.on('node:peer-found', (_, p) => cb(p)),
  onPeerLost: (cb) => ipcRenderer.on('node:peer-lost', (_, p) => cb(p)),
  onTransferProgress: (cb) => ipcRenderer.on('transfer:progress', (_, p) => cb(p)),
  onTransferCompleted: (cb) => ipcRenderer.on('node:transfer-completed', (_, r) => cb(r)),
  onPinPrompt: (cb) => ipcRenderer.on('node:pin-prompt', (_, d) => cb(d))
});
