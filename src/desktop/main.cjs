/**
 * LanceDrop Desktop — Point d'entrée Electron (CommonJS)
 */
const { app, BrowserWindow, Menu, ipcMain, dialog } = require('electron')
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

ipcMain.handle('device:get-info', async () => {
  return {
    name: 'Samsung Galaxy A54',
    os: 'Android 14',
    status: 'Connecté',
    usedStorage: '124,6 Go',
    totalStorage: '256 Go',
    deviceId: 'dev-galaxy-a54',
    ip: '192.168.1.45'
  }
})

ipcMain.handle('file:delete', async (_, filename) => {
  return { success: true, filename }
})

app.whenReady().then(createWindow)

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit()
})
