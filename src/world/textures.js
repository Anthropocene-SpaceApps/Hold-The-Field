// Surface detail textures, painted in code on small canvases (no image downloads). Each is tileable and close to
// mid-grey, so it adds grain without changing the colour the scene gives the surface.
import * as THREE from 'three';
import { rand, noise2, fbm } from './noise.js';

let maxAniso = 4;
export const setAnisotropy = (n) => { maxAniso = n; };
const cache = new Map();

function make(key, size, paint, { repeat = [1, 1], srgb = true } = {}) {
  const k = `${key}:${repeat.join('x')}`;
  if (cache.has(k)) return cache.get(k);
  let base = cache.get(`canvas:${key}`);
  if (!base) {
    base = document.createElement('canvas');
    base.width = base.height = size;
    paint(base.getContext('2d'), size);
    cache.set(`canvas:${key}`, base);
  }
  const t = new THREE.CanvasTexture(base);
  t.wrapS = t.wrapT = THREE.RepeatWrapping;
  t.repeat.set(repeat[0], repeat[1]);
  t.anisotropy = maxAniso;
  if (srgb) t.colorSpace = THREE.SRGBColorSpace;
  cache.set(k, t);
  return t;
}

/** Fill with tileable value noise around a mid tone. */
function grain(x, s, { lo, hi, scale, tint = [1, 1, 1] }) {
  const img = x.createImageData(s, s);
  for (let j = 0; j < s; j++) for (let i = 0; i < s; i++) {
    // tileable: blend four offset samples
    const u = i / s, v = j / s;
    const n = (fbm(u * scale, v * scale, 3) * (1 - u) * (1 - v) + fbm((u - 1) * scale, v * scale, 3) * u * (1 - v)
      + fbm(u * scale, (v - 1) * scale, 3) * (1 - u) * v + fbm((u - 1) * scale, (v - 1) * scale, 3) * u * v);
    const g = lo + (hi - lo) * n;
    const o = (j * s + i) * 4;
    img.data[o] = 255 * g * tint[0]; img.data[o + 1] = 255 * g * tint[1]; img.data[o + 2] = 255 * g * tint[2]; img.data[o + 3] = 255;
  }
  x.putImageData(img, 0, 0);
}

function speckle(x, s, n, color, len, width) {
  x.strokeStyle = color;
  x.lineWidth = width;
  x.lineCap = 'round';
  for (let k = 0; k < n; k++) {
    const px = rand() * s, py = rand() * s, a = rand() * Math.PI * 2, l = len * (0.4 + rand());
    for (const dx of [0, -s, s]) for (const dy of [0, -s, s]) {
      x.beginPath();
      x.moveTo(px + dx, py + dy);
      x.lineTo(px + dx + Math.cos(a) * l, py + dy + Math.sin(a) * l);
      x.stroke();
    }
  }
}

/** Grass and earth for the open land: tufts and grain. */
export const grassTexture = (repeat) => make('grass', 256, (x, s) => {
  grain(x, s, { lo: 0.78, hi: 1.0, scale: 6 });
  speckle(x, s, 900, 'rgba(40,60,20,0.18)', 5, 1.2);
  speckle(x, s, 500, 'rgba(255,255,230,0.12)', 4, 1);
}, { repeat });

/** Wet paddy mud with footprints and puddles. */
export const mudTexture = (repeat) => make('mud', 256, (x, s) => {
  grain(x, s, { lo: 0.72, hi: 1.0, scale: 5 });
  for (let k = 0; k < 40; k++) {
    const px = rand() * s, py = rand() * s, r = 4 + rand() * 14;
    const g = x.createRadialGradient(px, py, 0, px, py, r);
    g.addColorStop(0, 'rgba(255,255,255,0.18)');
    g.addColorStop(1, 'rgba(255,255,255,0)');
    x.fillStyle = g;
    x.fillRect(px - r, py - r, r * 2, r * 2);
  }
  speckle(x, s, 300, 'rgba(0,0,0,0.12)', 3, 1.5);
}, { repeat });

/** Packed earth for the embankment and paths. */
export const earthTexture = (repeat) => make('earth', 256, (x, s) => {
  grain(x, s, { lo: 0.7, hi: 1.0, scale: 9 });
  speckle(x, s, 400, 'rgba(0,0,0,0.15)', 3, 2);
  speckle(x, s, 200, 'rgba(255,240,210,0.15)', 2, 2);
}, { repeat });

/** Rock and forest for the hills seen from far away. */
export const hillTexture = (repeat) => make('hill', 256, (x, s) => {
  grain(x, s, { lo: 0.65, hi: 1.05, scale: 12 });
  speckle(x, s, 1200, 'rgba(10,30,10,0.22)', 2, 2.5);
}, { repeat });

/** Thatch: straw strands running down the roof. */
export const thatchTexture = (repeat) => make('thatch', 256, (x, s) => {
  x.fillStyle = '#c8a660';
  x.fillRect(0, 0, s, s);
  for (let k = 0; k < 2600; k++) {
    const px = rand() * s, py = rand() * s, l = 10 + rand() * 30, v = 0.7 + rand() * 0.5;
    x.strokeStyle = `rgba(${Math.round(150 * v)},${Math.round(118 * v)},${Math.round(60 * v)},0.55)`;
    x.lineWidth = 1 + rand();
    for (const dy of [0, -s, s]) {
      x.beginPath();
      x.moveTo(px, py + dy);
      x.lineTo(px + (rand() - 0.5) * 3, py + dy + l);
      x.stroke();
    }
  }
  for (let y = 0; y < s; y += 32) {         // layered rows
    x.fillStyle = 'rgba(60,40,15,0.25)';
    x.fillRect(0, y, s, 3);
  }
}, { repeat });

/** Mud-plastered wall. */
export const plasterTexture = (repeat) => make('plaster', 256, (x, s) => {
  grain(x, s, { lo: 0.78, hi: 1.0, scale: 7, tint: [1, 0.96, 0.9] });
  speckle(x, s, 160, 'rgba(70,45,25,0.16)', 14, 2);
}, { repeat });

/** One palm frond (alpha cut-out). */
export const frondTexture = () => make('frond', 128, (x, s) => {
  x.clearRect(0, 0, s, s);
  x.strokeStyle = '#ffffff';
  x.lineCap = 'round';
  x.lineWidth = 3;
  x.beginPath(); x.moveTo(s / 2, s); x.lineTo(s / 2, 0); x.stroke();
  x.lineWidth = 2.2;
  for (let y = 6; y < s - 4; y += 4) {
    const w = (s / 2 - 4) * Math.sin(Math.PI * (1 - y / s)) * (0.85 + rand() * 0.15);
    x.beginPath(); x.moveTo(s / 2, y); x.lineTo(s / 2 - w, y + 9); x.stroke();
    x.beginPath(); x.moveTo(s / 2, y); x.lineTo(s / 2 + w, y + 9); x.stroke();
  }
}, { srgb: false });

void noise2;
