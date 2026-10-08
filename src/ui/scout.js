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

/** Whole-season chart for the end screen: water level against bund height, with event markers. */
export function drawSeason(canvas, state, data) {
  const dpr = Math.min(window.devicePixelRatio || 1, 2);
  const w = canvas.clientWidth, h = canvas.clientHeight;
  canvas.width = w * dpr; canvas.height = h * dpr;
  const ctx = canvas.getContext('2d');
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  const hist = state.history, n = Math.max(1, data.days.length - 1);
  const max = Math.max(1, ...hist.map((p) => Math.max(p.level, p.bund))) * 1.1;
  const X = (i) => 4 + (i / n) * (w - 8), Y = (v) => h - 14 - (v / max) * (h - 50);
  ctx.fillStyle = 'rgba(79,134,184,.55)';
  ctx.beginPath(); ctx.moveTo(X(0), Y(0));
  hist.forEach((p, i) => ctx.lineTo(X(i), Y(p.level)));
  ctx.lineTo(X(hist.length - 1), Y(0)); ctx.closePath(); ctx.fill();
  ctx.strokeStyle = '#7a5530'; ctx.lineWidth = 2.5; ctx.beginPath();
  hist.forEach((p, i) => (i ? ctx.lineTo(X(i), Y(p.bund)) : ctx.moveTo(X(i), Y(p.bund))));
  ctx.stroke();
  const mark = { warning: '#c0452f', flood: '#1d4f7c', harvest: '#3e6b2a', loss: '#342e28' };
  ctx.font = '11px Kalam, sans-serif';
  let row = 0;
  for (const e of state.events) {
    if (!mark[e.type]) continue;
    ctx.strokeStyle = mark[e.type]; ctx.fillStyle = mark[e.type]; ctx.lineWidth = 1.5;
    ctx.beginPath(); ctx.moveTo(X(e.i), 4); ctx.lineTo(X(e.i), h - 14); ctx.stroke();
    ctx.fillText(e.type, Math.min(X(e.i) + 3, w - 40), 10 + (row++ % 3) * 11);
  }
  ctx.strokeStyle = '#342e28'; ctx.lineWidth = 1.5;
  ctx.beginPath(); ctx.moveTo(0, h - 13.5); ctx.lineTo(w, h - 13.5); ctx.stroke();
  ctx.fillStyle = '#6b6257';
  ctx.fillText(fmtDate(data.days[0].date), 4, h - 1);
  ctx.textAlign = 'right'; ctx.fillText(fmtDate(data.days.at(-1).date), w - 4, h - 1);
}

export function fillTimeline(ul, events) {
  ul.innerHTML = '';
  for (const e of events.filter((x) => x.type !== 'watch')) {
    const li = document.createElement('li');
    li.className = e.type;
    li.textContent = `${fmtDate(e.date)}: ${e.text}`;
    ul.appendChild(li);
  }
}
