// Satellite Scout panel: today's NASA readings, 14-day sparkline, water vs bund, event log.
import { threeDayUpstream } from '../sim/engine.js';

const $ = (id) => document.getElementById(id);
const STATUS_TEXT = { calm: 'Calm', watch: 'Watch', warning: 'Flood warning' };

export function renderScout(state, data, cfg) {
  const d = data.days[state.i];
  $('rUp').textContent = `${d.rainUp.toFixed(0)} mm`;
  $('rFarm').textContent = `${d.rainFarm.toFixed(0)} mm`;
  $('tmax').textContent = `${d.tmax.toFixed(0)} °C`;
  $('soil').textContent = d.soil.toFixed(2);

  const chip = $('status');
  chip.textContent = STATUS_TEXT[state.status];
  chip.className = `chip ${state.status}`;

  // water vs bund (scale: 0 .. 2 m)
  const max = 2;
  $('levelFill').style.width = `${Math.min(100, (state.level / max) * 100)}%`;
  $('levelBund').style.left = `${Math.min(100, (state.bund / max) * 100)}%`;

  drawSpark($('spark'), data.days, state.i, cfg);

  const log = $('log');
  log.innerHTML = '';
  for (const e of state.events.slice(-4).reverse()) {
    const li = document.createElement('li');
    li.className = e.type;
    li.textContent = `${fmtDate(e.date)}: ${e.text}`;
    log.appendChild(li);
  }
}

function drawSpark(canvas, days, i, cfg) {
  const dpr = Math.min(window.devicePixelRatio || 1, 2);
  const w = canvas.clientWidth, h = canvas.clientHeight;
  canvas.width = w * dpr; canvas.height = h * dpr;
  const ctx = canvas.getContext('2d');
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  const from = Math.max(0, i - 13);
  const slice = days.slice(from, i + 1);
  const max = Math.max(150, ...slice.map((d) => d.rainUp));
  const bw = w / 14;
  slice.forEach((d, k) => {
    const bh = (d.rainUp / max) * (h - 14);
    const sum3 = threeDayUpstream(days, from + k);
    ctx.fillStyle = sum3 >= cfg.warningMm ? '#c0452f' : sum3 >= cfg.watchMm ? '#d48a1f' : '#4f86b8';
    ctx.fillRect(k * bw + 2, h - bh, bw - 4, bh);
  });
  ctx.strokeStyle = '#342e28'; ctx.lineWidth = 1.5;
  ctx.beginPath(); ctx.moveTo(0, h - 0.5); ctx.lineTo(w, h - 0.5); ctx.stroke();
  ctx.fillStyle = '#6b6257'; ctx.font = '11px Kalam, sans-serif';
  ctx.fillText(`max ${Math.round(max)} mm/day`, 4, 11);
}

export function fmtDate(iso) {
  return new Date(iso + 'T00:00:00Z').toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric', timeZone: 'UTC' });
}
