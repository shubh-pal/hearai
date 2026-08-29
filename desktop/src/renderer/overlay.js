'use strict';

const box = document.getElementById('box');
const linesEl = document.getElementById('lines');
const hintEl = document.getElementById('hint');

let finalLines = [];
let interim = null;
let maxLines = 2;
let lastUpdate = 0;

function apply(cfg) {
  document.documentElement.style.setProperty('--fs', `${cfg.fontSize}px`);
  document.documentElement.style.setProperty('--op', String(cfg.opacity));
  maxLines = cfg.maxLines || 2;
  render();
}

function render() {
  const rows = [...finalLines.map((t) => ({ t, cls: 'final' }))];
  if (interim) rows.push({ t: interim, cls: 'interim' });
  const shown = rows.slice(-maxLines);
  linesEl.innerHTML = '';
  for (const r of shown) {
    const d = document.createElement('div');
    d.className = r.cls;
    d.textContent = r.t;
    linesEl.appendChild(d);
  }
  box.classList.toggle('active', shown.length > 0);
  box.classList.toggle('idle', shown.length === 0);
}

window.hearai.getConfig().then(apply);
window.hearai.onConfig(apply);

window.hearai.onStatus((s) => {
  box.classList.toggle('error', s.connection === 'error' || s.connection === 'disconnected');
  if (s.connection === 'connecting') hintEl.textContent = 'Connecting…';
  else if (s.connection === 'connected') hintEl.textContent = 'HearAI — waiting for audio…';
  else if (s.message) hintEl.textContent = s.message;
  if (!s.listening) { finalLines = []; interim = null; render(); }
});

window.hearai.onTranscript((t) => {
  lastUpdate = Date.now();
  box.classList.remove('stale');
  if (t.isFinal) {
    finalLines.push(t.text);
    finalLines = finalLines.slice(-maxLines);
    interim = null;
  } else {
    interim = t.text;
  }
  render();
});

// Dim, then clear, when the conversation goes quiet for a while.
setInterval(() => {
  if (!lastUpdate) return;
  const age = Date.now() - lastUpdate;
  if (age > 6000) box.classList.add('stale');
  if (age > 20000) { finalLines = []; interim = null; lastUpdate = 0; box.classList.remove('stale'); render(); }
}, 1000);
