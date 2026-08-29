'use strict';

const $ = (id) => document.getElementById(id);
let cfg = {};
let listening = false;

// This page only works inside the Electron app (it needs the IPC bridge from preload).
if (!window.hearai) {
  document.body.innerHTML =
    '<div style="padding:24px;font:15px/1.5 system-ui;color:#eee;background:#1c1c1f">' +
    '<h1 style="font-size:17px">Run HearAI Desktop from a terminal, not a browser</h1>' +
    '<p>This is an Electron app. Opening the HTML file in Chrome won\'t work.</p>' +
    '<pre style="background:#111;padding:12px;border-radius:6px;white-space:pre-wrap">cd ' +
    'hearai/desktop\nnpm install\nnpm start</pre>' +
    '<p>The Settings window opens automatically when the app launches.</p></div>';
  throw new Error('hearai bridge missing — not running inside Electron');
}

function bindRange(id, valId, fmt = (v) => v, key = id, transform = (v) => v) {
  const el = $(id);
  const out = $(valId);
  const update = () => { out.textContent = fmt(el.value); };
  el.addEventListener('input', update);
  el.addEventListener('change', () => save({ [key]: transform(Number(el.value)) }));
  return { el, update };
}

async function save(patch) {
  cfg = await window.hearai.setConfig(patch);
  paint();
}

function paint() {
  $('apiKey').value = cfg.apiKey || '';
  $('keyState').textContent = cfg.apiKey ? 'saved' : 'not set';
  $('keyState').className = cfg.apiKey ? 'ok' : 'err';

  for (const r of document.getElementsByName('src')) r.checked = r.value === cfg.audioSource;
  for (const r of document.getElementsByName('pos')) r.checked = r.value === cfg.positionMode;
  $('pinnedRow').style.display = cfg.positionMode === 'pinned' ? '' : 'none';

  setRange('px', 'pxv', cfg.pinnedX, (v) => `${v}px`);
  setRange('py', 'pyv', cfg.pinnedY, (v) => `${v}px`);
  setRange('fontSize', 'fsv', cfg.fontSize, (v) => `${v}px`);
  setRange('boxWidth', 'bwv', cfg.boxWidth, (v) => `${v}px`);
  setRange('opacity', 'opv', Math.round(cfg.opacity * 100), (v) => `${v}%`);
  setRange('maxLines', 'mlv', cfg.maxLines, (v) => v);
  $('hideShare').checked = !!cfg.hideFromScreenShare;
  $('romanize').checked = cfg.romanizeOutput !== false;
  $('vad').value = cfg.vadSensitivity;

  $('toggle').textContent = listening ? 'Stop listening' : 'Start listening';
}

function setRange(id, valId, value, fmt) {
  $(id).value = value;
  $(valId).textContent = fmt(value);
}

function setStatus(s) {
  const el = $('status');
  const map = {
    idle: ['Stopped.', 'dim'],
    connecting: ['Connecting to Gemini…', 'dim'],
    connected: ['Connected — listening.', 'ok'],
    disconnected: ['Disconnected. ' + (s.message || ''), 'err'],
    error: ['Error: ' + (s.message || 'unknown'), 'err'],
  };
  const [text, cls] = map[s.connection] || [s.message || '', 'dim'];
  el.textContent = text;
  el.className = cls;
}

// --- wire controls ---
$('saveKey').addEventListener('click', () => save({ apiKey: $('apiKey').value.trim() }));
for (const r of document.getElementsByName('src')) r.addEventListener('change', () => save({ audioSource: r.value }));
for (const r of document.getElementsByName('pos')) r.addEventListener('change', () => save({ positionMode: r.value }));

bindRange('px', 'pxv', (v) => `${v}px`, 'pinnedX');
bindRange('py', 'pyv', (v) => `${v}px`, 'pinnedY');
bindRange('fontSize', 'fsv', (v) => `${v}px`, 'fontSize');
bindRange('boxWidth', 'bwv', (v) => `${v}px`, 'boxWidth');
bindRange('opacity', 'opv', (v) => `${v}%`, 'opacity', (v) => v / 100);
bindRange('maxLines', 'mlv', (v) => v, 'maxLines');
$('hideShare').addEventListener('change', () => save({ hideFromScreenShare: $('hideShare').checked }));
$('romanize').addEventListener('change', () => save({ romanizeOutput: $('romanize').checked }));
$('vad').addEventListener('change', () => save({ vadSensitivity: $('vad').value }));

$('toggle').addEventListener('click', async () => {
  if (listening) { await window.hearai.stop(); } else { await window.hearai.start(); }
});

window.hearai.onStatus((s) => { listening = s.listening; setStatus(s); $('toggle').textContent = listening ? 'Stop listening' : 'Start listening'; });

(async () => {
  cfg = await window.hearai.getConfig();
  const st = await window.hearai.getState();
  listening = st.listening;
  paint();
  setStatus({ connection: st.connection });
})();
