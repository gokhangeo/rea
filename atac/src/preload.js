'use strict';

const { contextBridge, ipcRenderer, webUtils } = require('electron');

contextBridge.exposeInMainWorld('atac', {
  // Sürükle-bırakta gerçek dosya yolunu al (Electron 32+)
  pathForFile: (file) => {
    try {
      return webUtils.getPathForFile(file);
    } catch {
      return '';
    }
  },
  // Ataçtaki dosyaları dışarı (Gezgin/uygulama) sürükle
  startDrag: (files) => ipcRenderer.send('atac:start-drag', files),
  hide: () => ipcRenderer.send('atac:hide'),
  toggle: () => ipcRenderer.send('atac:toggle'),
  expand: () => ipcRenderer.send('atac:expand'),
  collapse: () => ipcRenderer.send('atac:collapse'),
  onExpanded: (cb) => ipcRenderer.on('atac:expanded', (_e, v) => cb(v)),
  quit: () => ipcRenderer.send('atac:quit'),
  openPath: (p) => ipcRenderer.invoke('atac:open-path', p),
  reveal: (p) => ipcRenderer.invoke('atac:reveal', p),
  getSettings: () => ipcRenderer.invoke('atac:get-settings'),
  setAutostart: (on) => ipcRenderer.invoke('atac:set-autostart', on),
  // + ile klasör/dosya seç
  pick: (mode) => ipcRenderer.invoke('atac:pick', mode),
  stat: (p) => ipcRenderer.invoke('atac:stat', p),
});
