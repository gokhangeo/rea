'use strict';

const { app, BrowserWindow, Tray, Menu, globalShortcut, ipcMain, screen, nativeImage, shell, dialog } = require('electron');
const path = require('path');
const fs = require('fs');

// Otomatik güncelleme (yalnızca paketlenmiş uygulamada)
let autoUpdater = null;
try {
  ({ autoUpdater } = require('electron-updater'));
} catch {}

let elleDenetim = false; // kullanıcı "Güncellemeleri denetle" ile mi başlattı

function bilgi(opts) {
  try { dialog.showMessageBox(Object.assign({ type: 'info', title: 'Ataç' }, opts)); } catch {}
}

function setupUpdater() {
  if (!autoUpdater) return;
  try {
    autoUpdater.autoDownload = true;
    autoUpdater.autoInstallOnAppQuit = true;

    autoUpdater.on('update-available', () => {
      if (elleDenetim) { elleDenetim = false; bilgi({ message: 'Yeni sürüm bulundu.', detail: 'Arka planda indiriliyor; bittiğinde haber vereceğim.' }); }
    });
    autoUpdater.on('update-not-available', () => {
      if (elleDenetim) { elleDenetim = false; bilgi({ message: 'Ataç güncel.', detail: 'Zaten en son sürümü kullanıyorsun (' + app.getVersion() + ').' }); }
    });
    autoUpdater.on('error', (e) => {
      if (elleDenetim) { elleDenetim = false; bilgi({ type: 'error', message: 'Güncelleme denetlenemedi.', detail: String((e && e.message) || e) }); }
    });
    autoUpdater.on('update-downloaded', (info) => {
      if (win) win.webContents.send('atac:update-ready');
      try {
        dialog.showMessageBox({
          type: 'info', title: 'Ataç',
          message: 'Güncelleme indirildi' + (info && info.version ? ' (' + info.version + ')' : '') + '.',
          detail: 'Uygulamayı kapatıp açtığında yeni sürüm uygulanır.',
          buttons: ['Sonra', 'Şimdi yeniden başlat'], defaultId: 1, cancelId: 0,
        }).then((r) => { if (r.response === 1) autoUpdater.quitAndInstall(); }).catch(() => {});
      } catch {}
    });

    if (app.isPackaged) {
      autoUpdater.checkForUpdates().catch(() => {});
      // 6 saatte bir denetle
      setInterval(() => autoUpdater.checkForUpdates().catch(() => {}), 6 * 60 * 60 * 1000);
    }
  } catch {}
}

// Tepsiden elle güncelleme denetimi (net geri bildirimli)
function guncellemeDenetle() {
  if (!autoUpdater || !app.isPackaged) {
    bilgi({
      message: 'Otomatik güncelleme yalnızca kurulan sürümde çalışır.',
      detail: 'Taşınabilir exe kullanıyorsan, sürüm sayfasından "atac-setup" kurulumunu bir kez indir; sonrası kendini günceller.\n\nhttps://github.com/gokhangeo/rea/releases/latest',
    });
    return;
  }
  elleDenetim = true;
  autoUpdater.checkForUpdates().catch((e) => {
    if (elleDenetim) { elleDenetim = false; bilgi({ type: 'error', message: 'Güncelleme denetlenemedi.', detail: String((e && e.message) || e) }); }
  });
}

const DRAG_ICON = path.join(__dirname, 'renderer', 'assets', 'drag.png');
const APP_ICON = path.join(__dirname, '..', 'build', 'icon.png');
const SETTINGS_FILE = path.join(app.getPath('userData'), 'ayarlar.json');

// Küçük ataç (kapalı) ve açık dik çubuk boyutları
const COL_W = 66;
const COL_H = 82;
const EXP_W = 150;
const EXP_H = 426;

let win = null;
let tray = null;
let expanded = false;
let anim = null;

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

// Kapalı (küçük ataç) durumunun sağ-üst çapası
function defaultAnchor() {
  const { workArea } = screen.getPrimaryDisplay();
  return {
    right: workArea.x + workArea.width - 20,
    top: workArea.y + Math.round((workArea.height - COL_H) / 2),
  };
}

function createWindow() {
  const a = settings.anchor || defaultAnchor();
  win = new BrowserWindow({
    width: COL_W,
    height: COL_H,
    x: a.right - COL_W,
    y: a.top,
    frame: false,
    transparent: true,
    resizable: false,
    maximizable: false,
    minimizable: false,
    fullscreenable: false,
    skipTaskbar: true,
    alwaysOnTop: false,
    hasShadow: false,
    focusable: false,
    acceptFirstMouse: true,
    icon: APP_ICON,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      spellcheck: false,
    },
  });

  win.loadFile(path.join(__dirname, 'renderer', 'index.html'));

  // Çapayı (sağ-üst) hatırla — sık taşımada yazmayı geciktir
  let kayitZaman = null;
  const remember = () => {
    if (!win) return;
    const b = win.getBounds();
    settings.anchor = { right: b.x + b.width, top: b.y };
    if (kayitZaman) clearTimeout(kayitZaman);
    kayitZaman = setTimeout(() => saveSettings(settings), 400);
  };
  win.on('moved', remember);
  win.on('close', remember);

  win.on('closed', () => { win = null; anim && clearInterval(anim); anim = null; });
}

// Pencereyi sağ kenar sabit kalacak şekilde yumuşakça yeniden boyutlandır
function animateTo(targetW, targetH) {
  if (!win) return;
  const start = win.getBounds();
  const right = start.x + start.width;
  const wa = screen.getDisplayMatching(start).workArea;
  let targetTop = start.y;
  if (targetTop + targetH > wa.y + wa.height) targetTop = wa.y + wa.height - targetH;
  if (targetTop < wa.y) targetTop = wa.y;
  const startTop = start.y;
  const steps = 11;
  let i = 0;
  if (anim) clearInterval(anim);
  anim = setInterval(() => {
    if (!win) { clearInterval(anim); anim = null; return; }
    i++;
    const t = i / steps;
    const e = 1 - Math.pow(1 - t, 3);
    const w = Math.round(start.width + (targetW - start.width) * e);
    const h = Math.round(start.height + (targetH - start.height) * e);
    const top = Math.round(startTop + (targetTop - startTop) * e);
    win.setBounds({ x: right - w, y: top, width: w, height: h });
    if (i >= steps) { clearInterval(anim); anim = null; }
  }, 16);
}

function setExpanded(v) {
  expanded = v;
  if (win) win.webContents.send('atac:expanded', v);
  animateTo(v ? EXP_W : COL_W, v ? EXP_H : COL_H);
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
    win.moveTop();
  }
}

function buildTray() {
  let img = nativeImage.createFromPath(APP_ICON);
  if (!img.isEmpty()) img = img.resize({ width: 16, height: 16 });
  tray = new Tray(img.isEmpty() ? nativeImage.createEmpty() : img);
  tray.setToolTip('Ataç — sürükle-bırak rafı');
  const menu = Menu.buildFromTemplate([
    { label: 'Ataç — sürüm ' + app.getVersion(), enabled: false },
    { type: 'separator' },
    { label: 'Ataç\'ı göster / gizle', click: toggleWindow },
    { type: 'separator' },
    {
      label: 'Windows açılışında başlat',
      type: 'checkbox',
      checked: settings.autostart !== false,
      click: (mi) => applyAutostart(mi.checked),
    },
    {
      label: 'Güncellemeleri denetle',
      enabled: true,
      click: guncellemeDenetle,
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
    setupUpdater();

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

ipcMain.on('atac:toggle', () => setExpanded(!expanded));
ipcMain.on('atac:expand', () => { if (!expanded) setExpanded(true); });
ipcMain.on('atac:collapse', () => { if (expanded) setExpanded(false); });

// Ataçı masaüstünde sürükleyerek taşıma
ipcMain.handle('atac:get-pos', () => (win ? win.getBounds() : { x: 0, y: 0 }));
ipcMain.on('atac:set-pos', (e, p) => {
  if (win && p) win.setPosition(Math.round(p.x), Math.round(p.y));
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
  const res = await dialog.showOpenDialog({
    title: mode === 'folder' ? 'Klasör ekle' : 'Dosya ekle',
    buttonLabel: 'Ataça ekle',
    properties: props,
  });
  if (res.canceled) return [];
  return res.filePaths || [];
});
