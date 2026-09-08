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
  onTransferProgress: (cb) => ipcRenderer.on('transfer:progress', (_, data) => cb(data)),
  readClipboard: () => ipcRenderer.invoke('clipboard:read'),
  writeClipboard: (text) => ipcRenderer.invoke('clipboard:write', text),
  sendClipboardText: (text) => ipcRenderer.invoke('clipboard:send', text),
  onClipboardReceived: (cb) => ipcRenderer.on('clipboard:received', (_, data) => cb(data)),
  onDeviceChanged: (cb) => ipcRenderer.on('device:changed', (_, data) => cb(data))
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
