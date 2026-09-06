import { contextBridge, ipcRenderer } from 'electron';

contextBridge.exposeInMainWorld('fastdrop', {
  getPeers: () => ipcRenderer.invoke('node:get-peers'),
  confirmPin: ok => ipcRenderer.invoke('node:confirm-pin', ok),
  openDownloads: () => ipcRenderer.invoke('node:open-downloads'),
  onPeerFound: cb => ipcRenderer.on('node:peer-found', (_, p) => cb(p)),
  onPeerLost: cb => ipcRenderer.on('node:peer-lost', (_, p) => cb(p)),
  onTransferProgress: cb => ipcRenderer.on('node:transfer-progress', (_, p) => cb(p)),
  onTransferCompleted: cb => ipcRenderer.on('node:transfer-completed', (_, r) => cb(r)),
  onPinPrompt: cb => ipcRenderer.on('node:pin-prompt', (_, d) => cb(d))
});
