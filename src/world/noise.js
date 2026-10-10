// Seeded randomness and value noise, so the landscape is the same on every load.
let seed = 20170329;
export function rand() {
  seed |= 0; seed = (seed + 0x6D2B79F5) | 0;
  let t = Math.imul(seed ^ (seed >>> 15), 1 | seed);
  t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
  return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
}

function hash(x, y) {
  let h = (x * 374761393 + y * 668265263) | 0;
  h = Math.imul(h ^ (h >>> 13), 1274126177);
  return ((h ^ (h >>> 16)) >>> 0) / 4294967296;
}

/** Smooth value noise in [0, 1]. */
export function noise2(x, y) {
  const xi = Math.floor(x), yi = Math.floor(y), xf = x - xi, yf = y - yi;
  const u = xf * xf * (3 - 2 * xf), v = yf * yf * (3 - 2 * yf);
  const a = hash(xi, yi), b = hash(xi + 1, yi), c = hash(xi, yi + 1), d = hash(xi + 1, yi + 1);
  return a + (b - a) * u + (c - a) * v + (a - b - c + d) * u * v;
}

/** Fractal noise in [0, 1]. */
export function fbm(x, y, octaves = 4) {
  let s = 0, amp = 0.5, norm = 0;
  for (let o = 0; o < octaves; o++) { s += noise2(x, y) * amp; norm += amp; x *= 2.03; y *= 2.03; amp *= 0.5; }
  return s / norm;
}

/** Ridged noise in [0, 1]: sharp crests, like the Khasi hills. */
export function ridged(x, y, octaves = 5) {
  let s = 0, amp = 0.5, norm = 0;
  for (let o = 0; o < octaves; o++) {
    const n = 1 - Math.abs(noise2(x, y) * 2 - 1);
    s += n * n * amp; norm += amp; x *= 2.1; y *= 2.1; amp *= 0.5;
  }
  return s / norm;
}
