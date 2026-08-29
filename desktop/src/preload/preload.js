'use strict';

const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('hearai', {
  // config
  getConfig: () => ipcRenderer.invoke('config:get'),
  setConfig: (patch) => ipcRenderer.invoke('config:set', patch),
  onConfig: (cb) => ipcRenderer.on('config', (_e, c) => cb(c)),

  // listening control (settings window)
  start: () => ipcRenderer.invoke('listen:start'),
  stop: () => ipcRenderer.invoke('listen:stop'),
  getState: () => ipcRenderer.invoke('listen:state'),
  onStatus: (cb) => ipcRenderer.on('status', (_e, s) => cb(s)),

  // overlay window
  onTranscript: (cb) => ipcRenderer.on('transcript', (_e, t) => cb(t)),
  setPinned: (x, y) => ipcRenderer.send('overlay:setPinned', { x, y }),

  // engine window
  onEngineStart: (cb) => ipcRenderer.on('engine:start', (_e, c) => cb(c)),
  onEngineStop: (cb) => ipcRenderer.on('engine:stop', () => cb()),
  onEngineConfig: (cb) => ipcRenderer.on('engine:config', (_e, c) => cb(c)),
  engineTranscript: (payload) => ipcRenderer.send('engine:transcript', payload),
  engineStatus: (payload) => ipcRenderer.send('engine:status', payload),
});
