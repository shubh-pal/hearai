'use strict';

/**
 * Best-effort transliteration of non-Latin transcript text into Latin script, so
 * captions always read as "tu kaisa hai" rather than "तू कैसा है".
 *
 * The Gemini setup also carries a system instruction asking the model to romanize;
 * this is the client-side safety net for when it doesn't. Devanagari (Hindi/Marathi)
 * is handled with a syllable walker tuned for casual "texting" romanization —
 * word-final inherent-schwa deletion, nasal assimilation. Other scripts fall
 * through unchanged (the model instruction is the only lever there).
 */

const V_IND = {
  'अ': 'a', 'आ': 'a', 'इ': 'i', 'ई': 'i', 'उ': 'u', 'ऊ': 'u', 'ऋ': 'ri',
  'ए': 'e', 'ऐ': 'ai', 'ओ': 'o', 'औ': 'au', 'ऑ': 'o', 'ऒ': 'o', 'ऍ': 'e', 'ऌ': 'l',
};
const V_SIGN = {
  'ा': 'a', 'ि': 'i', 'ी': 'i', 'ु': 'u', 'ू': 'u', 'ृ': 'ri',
  'े': 'e', 'ै': 'ai', 'ो': 'o', 'ौ': 'au', 'ॉ': 'o', 'ॅ': 'e', 'ॊ': 'o',
};
const CONS = {
  'क': 'k', 'ख': 'kh', 'ग': 'g', 'घ': 'gh', 'ङ': 'ng',
  'च': 'ch', 'छ': 'chh', 'ज': 'j', 'झ': 'jh', 'ञ': 'ny',
  'ट': 't', 'ठ': 'th', 'ड': 'd', 'ढ': 'dh', 'ण': 'n',
  'त': 't', 'थ': 'th', 'द': 'd', 'ध': 'dh', 'न': 'n',
  'प': 'p', 'फ': 'ph', 'ब': 'b', 'भ': 'bh', 'म': 'm',
  'य': 'y', 'र': 'r', 'ल': 'l', 'व': 'v', 'ळ': 'l',
  'श': 'sh', 'ष': 'sh', 'स': 's', 'ह': 'h',
  'क़': 'q', 'ख़': 'kh', 'ग़': 'g', 'ज़': 'z', 'ड़': 'r', 'ढ़': 'rh', 'फ़': 'f', 'य़': 'y',
};
const DIGITS = { '०': '0', '१': '1', '२': '2', '३': '3', '४': '4', '५': '5', '६': '6', '७': '7', '८': '8', '९': '9' };
const VIRAMA = '्';
const NUKTA = '़';
const ANUSVARA = 'ं';
const CHANDRABINDU = 'ँ';
const VISARGA = 'ः';
const LABIALS = new Set(['p', 'ph', 'b', 'bh', 'm', 'f']);

const isDevanagari = (s) => /[ऀ-ॿ]/.test(s);

/** Transliterate a single Devanagari word, deleting the trailing inherent schwa. */
function word(chars) {
  // syllables: {c: consonant-latin|'', v: vowel-latin|'a'|'', raw: string}
  const syl = [];
  for (let i = 0; i < chars.length; i++) {
    let ch = chars[i];

    if (chars[i + 1] === NUKTA && CONS[ch + NUKTA]) { ch = ch + NUKTA; i++; }

    if (CONS[ch]) {
      const cons = CONS[ch];
      const next = chars[i + 1];
      if (next === VIRAMA) { syl.push({ c: cons, v: '' }); i++; }
      else if (V_SIGN[next]) { syl.push({ c: cons, v: V_SIGN[next] }); i++; }
      else { syl.push({ c: cons, v: 'a', inherent: true }); }
      // trailing anusvara/chandrabindu/visarga on this syllable
      while (V_SIGN[chars[i + 1]]) { syl[syl.length - 1].v += V_SIGN[chars[i + 1]]; i++; }
      continue;
    }
    if (V_IND[ch]) { syl.push({ c: '', v: V_IND[ch] }); continue; }
    if (V_SIGN[ch]) { syl.push({ c: '', v: V_SIGN[ch] }); continue; }
    if (ch === ANUSVARA || ch === CHANDRABINDU) {
      const nextCons = CONS[chars[i + 1]] || CONS[(chars[i + 1] || '') + (chars[i + 2] === NUKTA ? NUKTA : '')];
      syl.push({ c: LABIALS.has(nextCons) ? 'm' : 'n', v: '', nasal: true });
      continue;
    }
    if (ch === VISARGA) { syl.push({ c: 'h', v: '' }); continue; }
    if (ch === VIRAMA || ch === NUKTA) continue;
    if (DIGITS[ch]) { syl.push({ raw: DIGITS[ch] }); continue; }
    if (ch === 'ॐ') { syl.push({ raw: 'om' }); continue; }
    if (ch === '।' || ch === '॥') { syl.push({ raw: '.' }); continue; }
    syl.push({ raw: ch });
  }

  // Delete a word-final bare inherent schwa ("नंबर" → "nambar", not "nambara"),
  // but keep it if the word is a single syllable ("ना" stays, "न" → "na").
  for (let k = syl.length - 1; k >= 0; k--) {
    if (syl[k].raw !== undefined || syl[k].nasal) continue;
    if (syl[k].inherent && syl[k].c && k > 0) syl[k].v = '';
    break;
  }

  return syl.map((s) => (s.raw !== undefined ? s.raw : s.c + s.v)).join('');
}

function devanagariToLatin(text) {
  let out = '';
  let buf = [];
  const flush = () => { if (buf.length) { out += word(buf); buf = []; } };
  for (const ch of text) {
    if (/[ऀ-ॿ]/.test(ch)) { buf.push(ch); continue; }
    flush();
    if (ch === '।' || ch === '॥') out += '.';
    else out += ch;
  }
  flush();
  return out.replace(/\s{2,}/g, ' ');
}

export function romanize(text) {
  if (!text) return text;
  if (isDevanagari(text)) return devanagariToLatin(text);
  return text;
}

export { isDevanagari };
