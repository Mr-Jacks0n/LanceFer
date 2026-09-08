/**
 * LanceDrop Desktop — Point d'entrée Electron (CommonJS)
 */
const { app, BrowserWindow, Menu, ipcMain, dialog, clipboard } = require('electron')
const path = require('path')

// Suppression complète du menu natif blanc obsolète (File, Edit, etc.)
Menu.setApplicationMenu(null)

let mainWindow = null

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1280,
    height: 820,
    minWidth: 1040,
    minHeight: 680,
    title: 'LanceDrop Desktop',
    backgroundColor: '#f8fafc',
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

let connectedDevice = null

ipcMain.handle('device:get-info', async () => {
  return connectedDevice
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
  return {
    success: true,
    text: text,
    timestamp: Date.now(),
    targetDevice: connectedDevice ? connectedDevice.name : 'Mobile connecté'
  }
})

app.whenReady().then(createWindow)

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit()
})
