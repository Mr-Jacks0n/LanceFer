/**
 * FastDrop Desktop — Point d'entrée Electron (CommonJS)
 * Nommé .cjs pour contourner "type": "module" du package.json racine
 */
const { app, BrowserWindow } = require('electron')
const path = require('path')

function createWindow () {
  const win = new BrowserWindow({
    width: 900,
    height: 620,
    title: 'FastDrop',
    webPreferences: {
      nodeIntegration: false,
      contextIsolation: true,
    },
  })
  win.loadFile(path.join(__dirname, 'ui', 'index.html'))
}

app.whenReady().then(createWindow)

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit()
})
