const { app, BrowserWindow, ipcMain, dialog, shell } = require('electron');
const path = require('path');
const clean = require('./modules/clean');
const uninstall = require('./modules/uninstall');
const optimize = require('./modules/optimize');
const analyze = require('./modules/analyze');
const status = require('./modules/status');
const startup = require('./modules/startup');

let win;

function createWindow() {
  win = new BrowserWindow({
    width: 1280,
    height: 840,
    minWidth: 1000,
    minHeight: 680,
    backgroundColor: '#0b1226',
    title: 'WinKeeper',
    icon: path.join(__dirname, '..', 'build', 'icon.png'),
    titleBarStyle: 'hidden',
    titleBarOverlay: { color: '#0b1226', symbolColor: '#d8deea', height: 48 },
    show: false,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
      backgroundThrottling: false,
    },
  });
  win.once('ready-to-show', () => win.show());
  win.loadFile(path.join(__dirname, '..', 'dist', 'index.html'));
  win.webContents.setWindowOpenHandler(() => ({ action: 'deny' }));
}

ipcMain.on('win:theme', (_e, color) => {
  if (win && /^#[0-9a-f]{6}$/i.test(color)) {
    try { win.setTitleBarOverlay({ color, symbolColor: '#d8deea', height: 48 }); } catch {}
  }
});

ipcMain.handle('clean:scan', (e) => clean.scan((p) => e.sender.send('clean:progress', p)));
ipcMain.handle('clean:run', (e, args) => clean.run(args, (p) => e.sender.send('clean:runProgress', p)));

ipcMain.handle('uninstall:list', () => uninstall.list());
ipcMain.handle('uninstall:run', (_e, id) => uninstall.runUninstaller(id));
ipcMain.handle('uninstall:leftovers', (_e, id) => uninstall.leftovers(id));
ipcMain.handle('uninstall:trash', (_e, paths) => uninstall.trash(paths));
ipcMain.handle('uninstall:reveal', (_e, id) => uninstall.reveal(id));

ipcMain.handle('optimize:list', () => optimize.list());
ipcMain.handle('optimize:run', (e, ids) => optimize.runMany(ids, (u) => e.sender.send('optimize:update', u)));

ipcMain.handle('startup:list', () => startup.list());
ipcMain.handle('startup:set', (_e, id, on) => startup.set(id, on));
ipcMain.handle('startup:setMany', (_e, ids, on) => startup.setMany(ids, on));
ipcMain.handle('startup:remove', (_e, id) => startup.remove(id));
ipcMain.handle('startup:add', () => startup.add(win));
ipcMain.handle('startup:reveal', (_e, id) => startup.reveal(id));
ipcMain.handle('startup:openFolder', () => startup.openFolder());

ipcMain.handle('analyze:drives', () => analyze.drives());
ipcMain.handle('analyze:pick', async () => {
  const r = await dialog.showOpenDialog(win, { properties: ['openDirectory'] });
  return r.canceled ? null : r.filePaths[0];
});
ipcMain.handle('analyze:scan', (e, root) => analyze.scan(root, (p) => e.sender.send('analyze:progress', p)));
ipcMain.handle('analyze:cancel', () => analyze.cancel());
ipcMain.handle('analyze:node', (_e, p) => analyze.getNode(p));
ipcMain.handle('analyze:summary', () => analyze.summary());
ipcMain.handle('analyze:reveal', (_e, p) => analyze.reveal(p));
ipcMain.handle('analyze:open', (_e, p) => analyze.openItem(p));
ipcMain.handle('analyze:trash', (_e, p) => analyze.trash(p));

ipcMain.handle('status:get', () => status.get());
ipcMain.handle('status:kill', (_e, pid) => status.kill(pid));
ipcMain.handle('sys:openStartupSettings', () => shell.openExternal('ms-settings:startupapps'));

app.whenReady().then(createWindow);
app.on('window-all-closed', () => app.quit());
