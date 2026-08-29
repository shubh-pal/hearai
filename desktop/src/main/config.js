'use strict';

const { app } = require('electron');
const fs = require('fs');
const path = require('path');

const CONFIG_PATH = path.join(app.getPath('userData'), 'config.json');

const DEFAULTS = {
  apiKey: '',
  audioSource: 'system', // 'system' (loopback) | 'mic'
  positionMode: 'follow', // 'follow' | 'pinned'
  pinnedX: 80,
  pinnedY: 80,
  cursorOffsetX: 18,
  cursorOffsetY: 26,
  fontSize: 15,
  maxLines: 2,
  opacity: 0.92,
  hideFromScreenShare: true, // window excluded from capture, still visible locally
  vadSensitivity: 'medium', // 'low' | 'medium' | 'high'
  boxWidth: 460,
  romanizeOutput: true, // always render captions in Latin script (e.g. "tu kaisa hai")
};

function load() {
  try {
    const raw = fs.readFileSync(CONFIG_PATH, 'utf8');
    return { ...DEFAULTS, ...JSON.parse(raw) };
  } catch {
    return { ...DEFAULTS };
  }
}

function save(patch) {
  const next = { ...load(), ...patch };
  try {
    fs.mkdirSync(path.dirname(CONFIG_PATH), { recursive: true });
    fs.writeFileSync(CONFIG_PATH, JSON.stringify(next, null, 2), 'utf8');
  } catch (e) {
    console.error('config save failed', e);
  }
  return next;
}

module.exports = { load, save, DEFAULTS, CONFIG_PATH };
