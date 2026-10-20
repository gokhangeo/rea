'use strict';

const { app, BrowserWindow, Tray, Menu, globalShortcut, ipcMain, screen, nativeImage, shell, dialog } = require('electron');
const path = require('path');
const fs = require('fs');

const DRAG_ICON = path.join(__dirname, 'renderer', 'assets', 'drag.png');
const APP_ICON = path.join(__dirname, '..', 'build', 'icon.png');
const SETTINGS_FILE = path.join(app.getPath('userData'), 'ayarlar.json');

const WIN_W = 180;
const WIN_H = 580;

let win = null;
let tray = null;

// ---- Ayarlar (kalıcı) ----
function loadSettings() {
  try {
    return JSON.parse(fs.readFileSync(SETTINGS_FILE, 'utf8'));
  } catch {
    return {};
  }
}
function saveSettings(s) {
  try {
    fs.writeFileSync(SETTINGS_FILE, JSON.stringify(s, null, 2));
  } catch {}
}
let settings = loadSettings();

// İlk açılışta: başlangıçta otomatik başlat (admin gerektirmeyen HKCU Run kaydı)
function applyAutostart(on) {
  settings.autostart = !!on;
  saveSettings(settings);
  if (process.platform === 'win32' && !process.windowsStore) {
    app.setLoginItemSettings({
      openAtLogin: !!on,
      args: ['--gizli-baslat'],
    });
  }
}

function defaultPosition() {
  const { workArea } = screen.getPrimaryDisplay();
  return {
    x: workArea.x + workArea.width - WIN_W - 20,
    y: workArea.y + Math.round((workArea.height - WIN_H) / 2),
  };
}

function createWindow() {
  const pos = settings.pos || defaultPosition();
  win = new BrowserWindow({
    width: WIN_W,
    height: WIN_H,
    x: pos.x,
    y: pos.y,
    frame: false,
    transparent: true,
    resizable: false,
    maximizable: false,
    minimizable: false,
    fullscreenable: false,
    skipTaskbar: true,
    alwaysOnTop: true,
    hasShadow: false,
    icon: APP_ICON,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      spellcheck: false,
    },
  });

  win.setAlwaysOnTop(true, 'floating');
  win.loadFile(path.join(__dirname, 'renderer', 'index.html'));

  // Konumu hatırla
  const remember = () => {
    if (!win) return;
    const [x, y] = win.getPosition();
    settings.pos = { x, y };
    saveSettings(settings);
  };
  win.on('moved', remember);
  win.on('close', remember);

  win.on('closed', () => {
    win = null;
  });
}

function toggleWindow() {
  if (!win) {
    createWindow();
    return;
  }
  if (win.isVisible()) {
    win.hide();
  } else {
    win.show();
    win.focus();
  }
}

function buildTray() {
  let img = nativeImage.createFromPath(APP_ICON);
  if (!img.isEmpty()) img = img.resize({ width: 16, height: 16 });
  tray = new Tray(img.isEmpty() ? nativeImage.createEmpty() : img);
  tray.setToolTip('Ataç — sürükle-bırak rafı');
  const menu = Menu.buildFromTemplate([
    { label: 'Ataç\'ı göster / gizle', click: toggleWindow },
    { type: 'separator' },
    {
      label: 'Windows açılışında başlat',
      type: 'checkbox',
      checked: settings.autostart !== false,
      click: (mi) => applyAutostart(mi.checked),
    },
    { type: 'separator' },
    { label: 'Çıkış', click: () => app.quit() },
  ]);
  tray.setContextMenu(menu);
  tray.on('click', toggleWindow);
}

// ---- Tek örnek kilidi ----
const gotLock = app.requestSingleInstanceLock();
if (!gotLock) {
  app.quit();
} else {
  app.on('second-instance', () => {
    if (win) {
      win.show();
      win.focus();
    }
  });

  app.whenReady().then(() => {
    // İlk çalıştırmada açılışta başlatmayı varsayılan olarak aç
    if (settings.initialized !== true) {
      settings.initialized = true;
      applyAutostart(true);
    }

    createWindow();
    buildTray();

    // Genel kısayol: aç/gizle
    globalShortcut.register('CommandOrControl+Shift+Space', toggleWindow);

    app.on('activate', () => {
      if (!win) createWindow();
      else win.show();
    });
  });

  app.on('window-all-closed', (e) => {
    // Tepsi simgesinde yaşamaya devam et
  });

  app.on('will-quit', () => {
    globalShortcut.unregisterAll();
  });
}

// ---- IPC ----
ipcMain.on('atac:start-drag', (e, files) => {
  if (!Array.isArray(files) || files.length === 0) return;
  const valid = files.filter((f) => {
    try {
      return fs.existsSync(f);
    } catch {
      return false;
    }
  });
  if (valid.length === 0) return;
  let icon = nativeImage.createFromPath(DRAG_ICON);
  if (!icon.isEmpty()) icon = icon.resize({ width: 56, height: 56 });
  try {
    e.sender.startDrag({ files: valid, file: valid[0], icon });
  } catch (err) {
    // Sürükleme başlatılamazsa sessizce geç
  }
});

ipcMain.on('atac:hide', () => {
  if (win) win.hide();
});

ipcMain.on('atac:quit', () => app.quit());

ipcMain.handle('atac:open-path', async (e, p) => {
  return shell.openPath(p);
});

ipcMain.handle('atac:reveal', async (e, p) => {
  shell.showItemInFolder(p);
  return true;
});

ipcMain.handle('atac:get-settings', async () => {
  return { autostart: settings.autostart !== false };
});

ipcMain.handle('atac:set-autostart', async (e, on) => {
  applyAutostart(on);
  return settings.autostart;
});

// Dosya bilgisi: klasör mü, boyut
ipcMain.handle('atac:stat', async (e, p) => {
  try {
    const st = fs.statSync(p);
    return { ok: true, isDir: st.isDirectory(), size: st.size };
  } catch {
    return { ok: false, isDir: false, size: 0 };
  }
});

// + düğmesi: klasör veya dosya seçip ataça ekle
ipcMain.handle('atac:pick', async (e, mode) => {
  const props =
    mode === 'folder'
      ? ['openDirectory', 'multiSelections']
      : ['openFile', 'multiSelections'];
  const res = await dialog.showOpenDialog(win, {
    title: mode === 'folder' ? 'Klasör ekle' : 'Dosya ekle',
    buttonLabel: 'Ataça ekle',
    properties: props,
  });
  if (res.canceled) return [];
  return res.filePaths || [];
});
