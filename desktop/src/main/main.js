'use strict';

const {
  app, BrowserWindow, Tray, Menu, ipcMain, screen, nativeImage, desktopCapturer, session, globalShortcut,
} = require('electron');
const path = require('path');
const config = require('./config');

let cfg = config.load();

/** @type {BrowserWindow} */ let overlayWin = null;
/** @type {BrowserWindow} */ let engineWin = null;
/** @type {BrowserWindow} */ let settingsWin = null;
/** @type {Tray} */ let tray = null;

let listening = false;
let followTimer = null;
let lastConnection = 'idle';

const RENDERER = (f) => path.join(__dirname, '..', 'renderer', f);
const PRELOAD = path.join(__dirname, '..', 'preload', 'preload.js');

// ---------------------------------------------------------------------------
// Overlay window — transparent, always-on-top, click-through. Renders captions.
// ---------------------------------------------------------------------------
function createOverlay() {
  overlayWin = new BrowserWindow({
    width: cfg.boxWidth,
    height: 140,
    show: false,
    frame: false,
    transparent: true,
    backgroundColor: '#00000000',
    hasShadow: false,
    resizable: false,
    movable: false,
    minimizable: false,
    maximizable: false,
    skipTaskbar: true,
    focusable: false,
    fullscreenable: false,
    acceptFirstMouse: false,
    webPreferences: { preload: PRELOAD, contextIsolation: true, nodeIntegration: false },
  });

  overlayWin.setAlwaysOnTop(true, 'screen-saver');
  overlayWin.setVisibleOnAllWorkspaces(true, { visibleOnFullScreen: true });
  // Click-through: mouse events pass to whatever is underneath.
  overlayWin.setIgnoreMouseEvents(true, { forward: true });
  // Keep the caption box out of screen recordings / shares while staying visible locally.
  applyContentProtection();

  overlayWin.loadFile(RENDERER('overlay.html'));
  overlayWin.on('closed', () => { overlayWin = null; });
}

function applyContentProtection() {
  if (overlayWin) overlayWin.setContentProtection(!!cfg.hideFromScreenShare);
}

// ---------------------------------------------------------------------------
// Engine window — hidden. Owns mic/loopback capture, VAD, Gemini Live socket.
// ---------------------------------------------------------------------------
function createEngine() {
  engineWin = new BrowserWindow({
    show: false,
    webPreferences: {
      preload: PRELOAD,
      contextIsolation: true,
      nodeIntegration: false,
      // getDisplayMedia loopback needs a normal (non-throttled) renderer.
      backgroundThrottling: false,
    },
  });
  engineWin.loadFile(RENDERER('engine.html'));
  engineWin.on('closed', () => { engineWin = null; });
}

// ---------------------------------------------------------------------------
// Settings / control window.
// ---------------------------------------------------------------------------
function openSettings() {
  if (settingsWin) { settingsWin.focus(); return; }
  settingsWin = new BrowserWindow({
    width: 460,
    height: 620,
    title: 'HearAI Desktop',
    resizable: false,
    webPreferences: { preload: PRELOAD, contextIsolation: true, nodeIntegration: false },
  });
  settingsWin.loadFile(RENDERER('settings.html'));
  settingsWin.on('closed', () => { settingsWin = null; });
}

// ---------------------------------------------------------------------------
// Cursor-follow loop.
// ---------------------------------------------------------------------------
function startFollow() {
  stopFollow();
  followTimer = setInterval(positionOverlay, 40);
}
function stopFollow() {
  if (followTimer) { clearInterval(followTimer); followTimer = null; }
}

function positionOverlay() {
  if (!overlayWin) return;
  const [w, h] = overlayWin.getSize();

  if (cfg.positionMode === 'pinned') {
    const disp = screen.getPrimaryDisplay();
    const x = clamp(cfg.pinnedX, disp.workArea.x, disp.workArea.x + disp.workArea.width - w);
    const y = clamp(cfg.pinnedY, disp.workArea.y, disp.workArea.y + disp.workArea.height - h);
    overlayWin.setPosition(Math.round(x), Math.round(y));
    return;
  }

  // follow mode
  const pt = screen.getCursorScreenPoint();
  const disp = screen.getDisplayNearestPoint(pt);
  const area = disp.workArea;

  let x = pt.x + cfg.cursorOffsetX;
  let y = pt.y + cfg.cursorOffsetY;
  // Flip to the other side of the cursor when we'd run off the edge.
  if (x + w > area.x + area.width) x = pt.x - cfg.cursorOffsetX - w;
  if (y + h > area.y + area.height) y = pt.y - cfg.cursorOffsetY - h;
  x = clamp(x, area.x, area.x + area.width - w);
  y = clamp(y, area.y, area.y + area.height - h);
  overlayWin.setPosition(Math.round(x), Math.round(y));
}

const clamp = (v, lo, hi) => Math.max(lo, Math.min(hi, v));

// ---------------------------------------------------------------------------
// Listening lifecycle.
// ---------------------------------------------------------------------------
function startListening() {
  if (!cfg.apiKey) { pushStatus('error', 'Add your Gemini API key in Settings first.'); return; }
  if (!engineWin) createEngine();
  listening = true;
  const send = () => engineWin.webContents.send('engine:start', cfg);
  if (engineWin.webContents.isLoading()) engineWin.webContents.once('did-finish-load', send);
  else send();

  if (overlayWin) { overlayWin.showInactive(); }
  startFollow();
  refreshTray();
  pushStatus('connecting', null);
}

function stopListening() {
  listening = false;
  if (engineWin) engineWin.webContents.send('engine:stop');
  stopFollow();
  if (overlayWin) overlayWin.hide();
  refreshTray();
  pushStatus('idle', null);
}

function pushStatus(connection, message) {
  lastConnection = connection;
  if (settingsWin) settingsWin.webContents.send('status', { listening, connection, message });
  if (overlayWin) overlayWin.webContents.send('status', { listening, connection, message });
}

// ---------------------------------------------------------------------------
// Tray.
// ---------------------------------------------------------------------------
function trayIcon() {
  // 16x16 rounded square with "cc" — good enough as a placeholder.
  const png =
    'iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAAiElEQVQ4y2NgGAWjgP7g/38GBgYG' +
    'BgYGhv///zPQAzADFf7//5+BHoAJKvz//38GegAmqPD/////GegBmKDC////Z6AHYIIK////n4Ee' +
    'gAkq/P///wz0AExQ4f///zPQAzBBhf////8Z6AGYoML/////GegBmKDC////n4EegAkqPPr////P' +
    'QA/ABBWOglEwCgB9lB9lWtC1WwAAAABJRU5ErkJggg==';
  return nativeImage.createFromBuffer(Buffer.from(png, 'base64'));
}

function refreshTray() {
  if (!tray) return;
  tray.setToolTip(`HearAI Desktop — ${listening ? 'listening' : 'stopped'}`);
  tray.setContextMenu(Menu.buildFromTemplate([
    { label: listening ? 'Stop listening' : 'Start listening', click: () => (listening ? stopListening() : startListening()) },
    { type: 'separator' },
    {
      label: 'Follow cursor',
      type: 'radio',
      checked: cfg.positionMode === 'follow',
      click: () => { cfg = config.save({ positionMode: 'follow' }); positionOverlay(); refreshTray(); },
    },
    {
      label: 'Pinned',
      type: 'radio',
      checked: cfg.positionMode === 'pinned',
      click: () => { cfg = config.save({ positionMode: 'pinned' }); positionOverlay(); refreshTray(); },
    },
    { type: 'separator' },
    { label: 'Settings…', click: openSettings },
    { label: 'Quit', click: () => { app.quit(); } },
  ]));
}

// ---------------------------------------------------------------------------
// IPC.
// ---------------------------------------------------------------------------
ipcMain.handle('config:get', () => cfg);
ipcMain.handle('config:set', (_e, patch) => {
  cfg = config.save(patch || {});
  if ('boxWidth' in (patch || {}) && overlayWin) overlayWin.setSize(cfg.boxWidth, 140);
  if ('hideFromScreenShare' in (patch || {})) applyContentProtection();
  if (overlayWin) overlayWin.webContents.send('config', cfg);
  if (listening && engineWin) engineWin.webContents.send('engine:config', cfg);
  positionOverlay();
  refreshTray();
  return cfg;
});
ipcMain.handle('listen:start', () => { startListening(); return true; });
ipcMain.handle('listen:stop', () => { stopListening(); return true; });
ipcMain.handle('listen:state', () => ({ listening, connection: lastConnection }));

// From engine renderer:
ipcMain.on('engine:transcript', (_e, payload) => {
  if (overlayWin) overlayWin.webContents.send('transcript', payload);
});
ipcMain.on('engine:status', (_e, payload) => {
  pushStatus(payload.connection, payload.message || null);
});

// Pinned-drag support: settings window can nudge the pinned position.
ipcMain.on('overlay:setPinned', (_e, { x, y }) => {
  cfg = config.save({ pinnedX: x, pinnedY: y });
  positionOverlay();
});

// ---------------------------------------------------------------------------
// App bootstrap.
// ---------------------------------------------------------------------------
app.on('second-instance', openSettings);
if (!app.requestSingleInstanceLock()) { app.quit(); }

app.whenReady().then(() => {
  // Loopback (system) audio for getDisplayMedia — Windows.
  session.defaultSession.setDisplayMediaRequestHandler((request, callback) => {
    desktopCapturer.getSources({ types: ['screen'] }).then((sources) => {
      callback({ video: sources[0], audio: 'loopback' });
    }).catch(() => callback({}));
  }, { useSystemPicker: false });

  createOverlay();
  createEngine();

  tray = new Tray(trayIcon());
  tray.on('click', openSettings);
  refreshTray();

  globalShortcut.register('CommandOrControl+Shift+H', () => (listening ? stopListening() : startListening()));
  globalShortcut.register('CommandOrControl+Shift+P', () => {
    cfg = config.save({ positionMode: cfg.positionMode === 'follow' ? 'pinned' : 'follow' });
    positionOverlay(); refreshTray();
  });

  openSettings();
});

// macOS: clicking the dock icon reopens the settings window.
app.on('activate', openSettings);

// Tray app: closing every window must not quit. Handler present = no auto-quit.
app.on('window-all-closed', () => {});
app.on('will-quit', () => { globalShortcut.unregisterAll(); });
