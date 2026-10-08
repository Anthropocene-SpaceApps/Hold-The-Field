// Isometric farm renderer (Canvas 2D). Reads sim state, never changes it.
// To use Zawad's art later: load images in loadArt() and draw them in drawTile().

const N = 8;                // grid size
const COLORS = {
  ink: '#342e28',
  soil: '#c9a877',
  stubble: '#d9c28c',
  dead: '#9c8a6b',
  water: [79, 134, 184],
  lake: '#7fa8cc',
  bund: '#8a6238',
  stages: ['#b9d48f', '#7fb15a', '#a9b84a', '#d8b443'], // seedling, growing, ripening, golden
};

export function createFarmRenderer(canvas) {
  const ctx = canvas.getContext('2d');
  let W = 0, H = 0, dpr = 1;

  function resize() {
    dpr = Math.min(window.devicePixelRatio || 1, 2);
    const r = canvas.getBoundingClientRect();
    W = r.width; H = r.height;
    canvas.width = Math.round(W * dpr); canvas.height = Math.round(H * dpr);
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  }

  function draw(state, cfg, t = 0) {
    if (!W) resize();
    ctx.clearRect(0, 0, W, H);
    const tw = Math.min(W / (N + 4), (H / (N + 4)) * 2) * 1.05; // tile width
    const th = tw / 2;
    const ox = W / 2, oy = H / 2 - (N * th) / 2 + th * 0.6;
    const iso = (x, y) => [ox + (x - y) * tw / 2, oy + (x + y) * th / 2];

    // Haor lake beyond the bund (two rows on the y = -1, -2 side)
    const lakeAlpha = 0.35 + Math.min(0.5, state.level / Math.max(state.bund, 0.1) * 0.5);
    for (let y = -3; y < 0; y++) for (let x = -1; x <= N; x++) {
      poly(iso, x, y, `rgba(${COLORS.water.join(',')},${lakeAlpha})`, null);
    }
    wave(iso, t, tw);

    // Field tiles
    const overBund = state.level - state.bund;
    const floodRows = overBund > 0 ? Math.min(N, 1 + Math.floor(overBund / 0.04)) : 0;
    for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) {
      const fill = tileColor(state);
      poly(iso, x, y, fill, 'rgba(52,46,40,.35)');
      drawCrop(iso, x, y, state, tw);
      if (y < floodRows) poly(iso, x, y, `rgba(${COLORS.water.join(',')},0.72)`, null);
    }

    // Bund along the lake edge (y = 0 line), height grows with bund
    const bh = 4 + (state.bund - cfg.bundStart) * 28 + 6;
    const [ax, ay] = iso(0, 0), [bx, by] = iso(N, 0);
    ctx.fillStyle = COLORS.bund; ctx.strokeStyle = COLORS.ink; ctx.lineWidth = 1.5;
    ctx.beginPath(); ctx.moveTo(ax, ay); ctx.lineTo(bx, by); ctx.lineTo(bx, by - bh); ctx.lineTo(ax, ay - bh); ctx.closePath();
    ctx.fill(); ctx.stroke();

    // Hut + rice sacks at the far corner
    hut(iso(N + 1.2, N - 1.5), tw, state);

    // Flood label
    if (state.flooded && !state.harvested) label(W / 2, 28, 'FLOOD! Water over the bund', '#c0452f');
    else if (!state.alive) label(W / 2, 28, 'The crop is lost', '#c0452f');
    else if (state.harvested) label(W / 2, 28, `Rice is home: ${Math.round(state.yieldPct * 100)}%`, '#3e6b2a');
  }

  function poly(iso, x, y, fill, stroke) {
    const p = [iso(x, y), iso(x + 1, y), iso(x + 1, y + 1), iso(x, y + 1)];
    ctx.beginPath(); ctx.moveTo(...p[0]); for (const q of p.slice(1)) ctx.lineTo(...q); ctx.closePath();
    if (fill) { ctx.fillStyle = fill; ctx.fill(); }
    if (stroke) { ctx.strokeStyle = stroke; ctx.lineWidth = 1; ctx.stroke(); }
  }

  function tileColor(s) {
    if (!s.alive) return COLORS.dead;
    if (s.harvested) return COLORS.stubble;
    return COLORS.soil;
  }

  function drawCrop(iso, x, y, s, tw) {
    if (!s.alive || s.harvested) return;
    const stage = s.maturity < 0.25 ? 0 : s.maturity < 0.6 ? 1 : s.maturity < 0.9 ? 2 : 3;
    const [cx, cy] = iso(x + 0.5, y + 0.5);
    const h = tw * (0.12 + 0.22 * s.maturity);
    ctx.strokeStyle = COLORS.stages[stage]; ctx.lineWidth = Math.max(1.2, tw / 40);
    const seed = (x * 31 + y * 17) % 7;
    for (let k = -2; k <= 2; k++) {
      const bx = cx + k * tw * 0.07 + (seed - 3) * 0.6;
      ctx.beginPath(); ctx.moveTo(bx, cy + 2); ctx.quadraticCurveTo(bx + k * 2, cy - h * 0.6, bx + k * 3.5, cy - h); ctx.stroke();
    }
  }

  function wave(iso, t, tw) {
    ctx.strokeStyle = 'rgba(255,255,255,.55)'; ctx.lineWidth = 1.2;
    for (let k = 0; k < 6; k++) {
      const [x0, y0] = iso(k * 1.4 - 0.5, -2 + (k % 2) * 0.8);
      ctx.beginPath();
      for (let i = 0; i <= 10; i++) {
        const xx = x0 + i * tw * 0.06, yy = y0 + Math.sin(i * 0.9 + t / 400 + k) * 2;
        i ? ctx.lineTo(xx, yy) : ctx.moveTo(xx, yy);
      }
      ctx.stroke();
    }
  }

  function hut([x, y], tw, s) {
    const w = tw * 0.9, h = tw * 0.55;
    ctx.fillStyle = '#e8d6b0'; ctx.strokeStyle = COLORS.ink; ctx.lineWidth = 1.5;
    ctx.fillRect(x - w / 2, y - h, w, h); ctx.strokeRect(x - w / 2, y - h, w, h);
    ctx.fillStyle = '#9b7a4a';
    ctx.beginPath(); ctx.moveTo(x - w * 0.65, y - h); ctx.lineTo(x, y - h - tw * 0.45); ctx.lineTo(x + w * 0.65, y - h); ctx.closePath(); ctx.fill(); ctx.stroke();
    ctx.fillStyle = '#5a4630'; ctx.fillRect(x - w * 0.1, y - h * 0.6, w * 0.2, h * 0.6);
    if (s.harvested && s.yieldPct > 0) {
      const sacks = Math.max(1, Math.round(s.yieldPct * 6));
      for (let i = 0; i < sacks; i++) {
        const sx = x + w * 0.7 + (i % 3) * tw * 0.18, sy = y - Math.floor(i / 3) * tw * 0.14;
        ctx.fillStyle = '#e9d9a6'; ctx.beginPath(); ctx.ellipse(sx, sy - 6, tw * 0.08, tw * 0.1, 0, 0, Math.PI * 2); ctx.fill(); ctx.stroke();
      }
    }
  }

  function label(x, y, text, color) {
    ctx.font = '700 26px Caveat, Kalam, cursive';
    ctx.textAlign = 'center';
    ctx.fillStyle = color; ctx.fillText(text, x, y + 10);
  }

  window.addEventListener('resize', resize);
  return { draw, resize };
}
