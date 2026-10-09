// Interface language. English strings are the keys: t('Done') returns the Bengali text when Bengali is selected
// and a translation exists, otherwise the English text. The translations are shared with the desktop build:
// game/src/main/resources/lang/bn.txt (desktop + web) and lang/bn-web.txt (strings only the web build uses).
import { BRAND } from './brand.js';

const SOURCES = ['game/src/main/resources/lang/bn.txt', 'lang/bn-web.txt'];
const MONTHS_EN = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
const MONTHS_BN = ['জানু', 'ফেব্রু', 'মার্চ', 'এপ্রিল', 'মে', 'জুন', 'জুলাই', 'আগস্ট', 'সেপ্টেম্বর', 'অক্টোবর', 'নভেম্বর', 'ডিসেম্বর'];
const BN_DIGITS = '০১২৩৪৫৬৭৮৯';

const table = new Map();
const templates = [];          // keys with {} placeholders, as regexes, for finished strings like 'Raised bund to 0.75 m'
const memo = new Map();
let lang = 'en';
const listeners = new Set();

export async function loadLanguages() {
  try { lang = localStorage.getItem(`${BRAND.storageKey}-lang`) === 'bn' ? 'bn' : 'en'; } catch { /* storage unavailable */ }
  await Promise.all(SOURCES.map(async (url) => {
    try {
      const res = await fetch(url);
      if (res.ok) parse(await res.text());
    } catch { /* a missing translation file just leaves English */ }
  }));
  templates.sort((a, b) => b.lit - a.lit);      // most specific template wins
}

function parse(text) {
  for (const raw of text.split('\n')) {
    const line = raw.trim();
    if (!line || line.startsWith('#')) continue;
    const at = line.indexOf(' => ');
    if (at < 0) continue;
    const un = (s) => s.trim().replaceAll('\\n', '\n');
    const key = un(line.slice(0, at)), val = un(line.slice(at + 4));
    table.set(key, val);
    if (key.includes('{}')) {
      const re = new RegExp('^' + key.split('{}').map((p) => p.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')).join('(.+?)') + '$', 's');
      templates.push({ re, val, lit: key.replace(/\{\}/g, '').length });
    }
  }
}

export const language = () => lang;
export const isBengali = () => lang === 'bn';

export function setLanguage(code) {
  lang = code === 'bn' ? 'bn' : 'en';
  memo.clear();
  try { localStorage.setItem(`${BRAND.storageKey}-lang`, lang); } catch { /* ignore */ }
  document.documentElement.lang = lang;
  for (const fn of listeners) fn(lang);
}

export const onLanguage = (fn) => listeners.add(fn);

/** Digits to Bengali digits when Bengali is on. */
export function num(s) {
  s = String(s);
  return lang === 'bn' ? s.replace(/[0-9]/g, (d) => BN_DIGITS[d]) : s;
}

/** Translate a fixed string. */
export function t(s) {
  if (lang !== 'bn' || s == null) return s;
  const hit = table.get(s);
  if (hit != null) return hit.replaceAll(BRAND_OLD, BRAND.name);
  if (memo.has(s)) return memo.get(s);
  let out = s;
  for (const { re, val } of templates) {
    const m = re.exec(s);
    if (!m) continue;
    let k = 1;
    out = num(val.replace(/\{\}/g, () => t(m[k++] ?? '')));
    break;
  }
  memo.set(s, out);
  return out;
}
const BRAND_OLD = 'Hold the Field';

/** Translate a template with {} placeholders, then fill it: f('Day {} of {}', 3, 90). */
export function f(template, ...vals) {
  let k = 0;
  const out = t(template).replace(/\{\}/g, () => (k < vals.length ? String(vals[k++]) : ''));
  return num(out);
}

/** '2017-03-26' -> '26 Mar 2017' (or ২৬ মার্চ ২০১৭). */
export function date(iso, withYear = true) {
  const [y, m, d] = iso.split('-').map(Number);
  const months = lang === 'bn' ? MONTHS_BN : MONTHS_EN;
  return num(`${d} ${months[m - 1]}${withYear ? ` ${y}` : ''}`);
}

/** Fill every element that carries data-i18n (text) or data-i18n-title (tooltip) with the current language. */
export function translateDom(root = document) {
  for (const el of root.querySelectorAll('[data-i18n]')) {
    if (!el.dataset.en) el.dataset.en = el.textContent.trim();
    el.textContent = t(el.dataset.en);
  }
  for (const el of root.querySelectorAll('[data-i18n-title]')) {
    if (!el.dataset.enTitle) el.dataset.enTitle = el.getAttribute('title') || '';
    el.setAttribute('title', t(el.dataset.enTitle));
  }
}
