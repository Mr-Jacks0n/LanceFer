import { app, BrowserWindow, ipcMain, dialog, shell } from 'electron';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import os from 'node:os';
import { FastDropNode } from '../core/node.js';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
let mainWindow = null;
let fastDropNode = null;
let pendingPinCallback = null;

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1060,
    height: 680,
    backgroundColor: '#0f172a',
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false
    }
  });
  mainWindow.loadFile(path.join(__dirname, 'ui', 'index.html'));
}

app.whenReady().then(async () => {
  createWindow();
  fastDropNode = new FastDropNode({
    deviceName: `${os.hostname()} (PC)`,
    downloadDir: path.join(os.homedir(), 'Downloads', 'FastDrop'),
    onPinPrompt: (peer, pin) => new Promise(res => {
      pendingPinCallback = res;
      mainWindow.webContents.send('node:pin-prompt', { peer, pin });
    })
  });
  fastDropNode.on('peer:found', p => mainWindow.webContents.send('node:peer-found', p));
  fastDropNode.on('peer:lost', p => mainWindow.webContents.send('node:peer-lost', p));
  fastDropNode.on('transfer:progress', p => mainWindow.webContents.send('node:transfer-progress', p));
  fastDropNode.on('transfer:completed', r => mainWindow.webContents.send('node:transfer-completed', r));
  await fastDropNode.start().catch(() => {});
});

ipcMain.handle('node:get-peers', () => fastDropNode ? fastDropNode.getPeers() : []);
ipcMain.handle('node:confirm-pin', (_, ok) => { if (pendingPinCallback) { pendingPinCallback(ok); pendingPinCallback = null; } });
ipcMain.handle('node:open-downloads', () => { shell.openPath(path.join(os.homedir(), 'Downloads', 'FastDrop')); });
app.on('window-all-closed', () => { if (process.platform !== 'darwin') app.quit(); });
