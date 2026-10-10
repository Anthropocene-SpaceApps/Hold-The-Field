// Every sound is generated in code (WebAudio), like the desktop build: rain, warning chime, harvest, thunder.
import { BRAND } from './brand.js';

let ctx = null, master = null, rainGain = null, muted = false;
try { muted = localStorage.getItem(`${BRAND.storageKey}-muted`) === '1'; } catch { /* ignore */ }

/** Must be called from a user gesture (browsers block audio before one). */
export function unlock() {
  if (ctx) { if (ctx.state === 'suspended') ctx.resume(); return; }
  const AC = window.AudioContext || window.webkitAudioContext;
  if (!AC) return;
  ctx = new AC();
  master = ctx.createGain();
  master.gain.value = muted ? 0 : 0.7;
  master.connect(ctx.destination);
  // rain: looped filtered noise
  const len = ctx.sampleRate * 2, buf = ctx.createBuffer(1, len, ctx.sampleRate), d = buf.getChannelData(0);
  for (let i = 0; i < len; i++) d[i] = Math.random() * 2 - 1;
  const src = ctx.createBufferSource();
  src.buffer = buf; src.loop = true;
  const lp = ctx.createBiquadFilter();
  lp.type = 'lowpass'; lp.frequency.value = 1600;
  rainGain = ctx.createGain();
  rainGain.gain.value = 0;
  src.connect(lp).connect(rainGain).connect(master);
  src.start();
}

export const isMuted = () => muted;
export function toggleMute() {
  muted = !muted;
  try { localStorage.setItem(`${BRAND.storageKey}-muted`, muted ? '1' : '0'); } catch { /* ignore */ }
  if (master) master.gain.setTargetAtTime(muted ? 0 : 0.7, ctx.currentTime, 0.05);
  return muted;
}

export function setRain(level) {
  if (rainGain) rainGain.gain.setTargetAtTime(Math.min(0.35, level * 0.35), ctx.currentTime, 0.4);
}

function tone(freq, start, dur, type = 'sine', vol = 0.18) {
  if (!ctx) return;
  const o = ctx.createOscillator(), g = ctx.createGain(), t = ctx.currentTime + start;
  o.type = type; o.frequency.value = freq;
  g.gain.setValueAtTime(0, t);
  g.gain.linearRampToValueAtTime(vol, t + 0.02);
  g.gain.exponentialRampToValueAtTime(0.001, t + dur);
  o.connect(g).connect(master);
  o.start(t); o.stop(t + dur + 0.05);
}

export const sfx = {
  click: () => tone(660, 0, 0.08, 'triangle', 0.08),
  beep: () => tone(1320, 0, 0.06, 'sine', 0.05),          // the satellite terminal
  place: () => { tone(160, 0, 0.18, 'square', 0.08); tone(120, 0.06, 0.2, 'square', 0.06); },
  water: () => { tone(520, 0, 0.12, 'sine', 0.1); tone(780, 0.05, 0.16, 'sine', 0.07); },
  warning: () => { tone(880, 0, 0.22, 'triangle', 0.16); tone(660, 0.25, 0.22, 'triangle', 0.16); tone(880, 0.5, 0.3, 'triangle', 0.16); },
  error: () => tone(180, 0, 0.25, 'sawtooth', 0.07),
  harvest: () => [523, 659, 784, 1047].forEach((f, i) => tone(f, i * 0.09, 0.35, 'triangle', 0.12)),
  achieve: () => [784, 988, 1175].forEach((f, i) => tone(f, i * 0.08, 0.4, 'sine', 0.1)),
  flood: () => { tone(110, 0, 0.9, 'sawtooth', 0.06); tone(82, 0.1, 1.1, 'sine', 0.12); },
  thunder() {
    if (!ctx) return;
    const len = ctx.sampleRate * 2.5, buf = ctx.createBuffer(1, len, ctx.sampleRate), d = buf.getChannelData(0);
    for (let i = 0; i < len; i++) d[i] = (Math.random() * 2 - 1) * Math.pow(1 - i / len, 2);
    const src = ctx.createBufferSource(), lp = ctx.createBiquadFilter(), g = ctx.createGain();
    src.buffer = buf; lp.type = 'lowpass'; lp.frequency.value = 280; g.gain.value = 0.6;
    src.connect(lp).connect(g).connect(master);
    src.start();
  },
};
