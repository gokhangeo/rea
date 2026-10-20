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
  quit: () => ipcRenderer.send('atac:quit'),
  openPath: (p) => ipcRenderer.invoke('atac:open-path', p),
  reveal: (p) => ipcRenderer.invoke('atac:reveal', p),
  getSettings: () => ipcRenderer.invoke('atac:get-settings'),
  setAutostart: (on) => ipcRenderer.invoke('atac:set-autostart', on),
  // + ile klasör/dosya seç
  pick: (mode) => ipcRenderer.invoke('atac:pick', mode),
  stat: (p) => ipcRenderer.invoke('atac:stat', p),
});
