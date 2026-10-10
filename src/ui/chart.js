// Interactive line/area chart over the season's days (port of the desktop ui/Chart.java).
// Values beyond visibleUntil stay hidden: the scout cannot see the future. Hover or touch shows a tooltip.
import { t, num } from '../i18n.js';
import { COLORS } from './theme.js';

export class Chart {
  constructor(title) {
    this.title = title;
    this.series = []; this.hlines = []; this.vlines = [];
    this.yMin = 0; this.yMax = NaN;
    this.visibleUntil = 0; this.cursorDay = 0; this.firstDay = 0; this.days = 0;
    this.dates = []; this.decimals = 0;
    this.hover = null;
  }
  line(name, values, color, unit = '') { this.series.push({ name, values, color, unit }); return this; }
  areaOf(name, values, color, unit = '') { this.series.push({ name, values, color, unit, area: true }); return this; }
  stepped(name, values, color, unit = '') { this.series.push({ name, values, color, unit, step: true }); return this; }
  hline(value, color, label) { this.hlines.push({ value, color, label }); return this; }
  vline(day, color, label) { this.vlines.push({ day, color, label }); return this; }

  /** Put the chart in a container element; it keeps itself sized and handles hover. */
  mount(el) {
    el.classList.add('chart');
    el.innerHTML = '';
    const head = document.createElement('div');
    head.className = 'chart-head';
    head.innerHTML = `<div class="label">${esc(t(this.title))}</div><div class="legend">${this.series
      .map((s) => `<span><i style="background:${s.color}"></i>${esc(t(s.name))}</span>`).join('')}</div>`;
    const canvas = document.createElement('canvas');
    el.append(head, canvas);
    this.canvas = canvas;
    const move = (e) => {
      const r = canvas.getBoundingClientRect();
      const p = e.touches ? e.touches[0] : e;
      this.hover = { x: p.clientX - r.left, y: p.clientY - r.top };
      this.draw();
    };
    canvas.addEventListener('pointermove', move);
    canvas.addEventListener('pointerdown', move);
    canvas.addEventListener('pointerleave', () => { this.hover = null; this.draw(); });
    this.ro = new ResizeObserver(() => this.draw());
    this.ro.observe(canvas);
    return this;
  }

  top() {
    if (!Number.isNaN(this.yMax)) return this.yMax;
    let m = 1e-9;
    for (const s of this.series) for (let i = 0; i < Math.min(s.values.length, this.visibleUntil + 1); i++) m = Math.max(m, s.values[i]);
    for (const h of this.hlines) m = Math.max(m, h.value * 1.05);
    return niceCeil(m * 1.08);
  }

  fmt(v) { return num(this.decimals === 0 ? String(Math.round(v)) : v.toFixed(this.decimals)); }

  draw() {
    const c = this.canvas;
    if (!c || !c.isConnected) return;
    const dpr = Math.min(window.devicePixelRatio || 1, 2);
    const w = c.clientWidth, h = c.clientHeight;
    if (!w || !h) return;
    if (c.width !== Math.round(w * dpr) || c.height !== Math.round(h * dpr)) { c.width = Math.round(w * dpr); c.height = Math.round(h * dpr); }
    const x = c.getContext('2d');
    x.setTransform(dpr, 0, 0, dpr, 0, 0);
    x.clearRect(0, 0, w, h);
    const font = getComputedStyle(document.body).fontFamily;
    const px = 40, py = 8, pw = w - px - 10, ph = h - py - 22;
    const top = this.top(), lo = this.yMin, span = Math.max(1e-9, top - lo), n = Math.max(1, this.days - 1);
    const X = (i) => px + pw * i / n, Y = (v) => py + ph - Math.max(0, Math.min(1, (v - lo) / span)) * ph;

    x.font = `10.5px ${font}`;
    x.textBaseline = 'middle';
    for (let g = 0; g <= 3; g++) {
      const gy = Math.round(py + ph - ph * g / 3) + 0.5;
      x.fillStyle = g === 0 ? 'rgba(255,255,255,.4)' : 'rgba(255,255,255,.12)';
      x.fillRect(px, gy, pw, 1);
      x.fillStyle = COLORS.faint;
      x.textAlign = 'right';
      x.fillText(this.fmt(lo + span * g / 3), px - 6, gy);
    }
    if (this.firstDay > 0) { x.fillStyle = 'rgba(0,0,0,.18)'; x.fillRect(px, py, pw * this.firstDay / n, ph); }
    if (this.visibleUntil < this.days - 1) {
      const fx = px + pw * (this.visibleUntil + 0.5) / n;
      x.fillStyle = 'rgba(0,0,0,.28)';
      x.fillRect(fx, py, px + pw - fx, ph);
    }
    x.font = `600 10px ${font}`;
    for (const hl of this.hlines) {
      const hy = Y(hl.value);
      x.fillStyle = hl.color;
      for (let dx = 0; dx < pw; dx += 8) x.fillRect(px + dx, hy, 4, 1.2);
      x.textAlign = 'right';
      x.fillText(t(hl.label), px + pw - 2, hy - 8);
    }
    for (const s of this.series) this.drawSeries(x, s, X, Y, py + ph);
    let row = 0;
    for (const vl of this.vlines) {
      const vx = X(vl.day);
      x.globalAlpha = 0.8; x.fillStyle = vl.color; x.fillRect(vx, py, 1.4, ph); x.globalAlpha = 1;
      const label = t(vl.label), tw = x.measureText(label).width;
      const bx = Math.min(vx + 3, px + pw - tw - 8), by = py + 2 + (row++ % 3) * 15;
      x.fillStyle = withAlpha(vl.color, 0.3);
      roundRect(x, bx, by, tw + 8, 13, 6); x.fill();
      x.fillStyle = vl.color; x.textAlign = 'left';
      x.fillText(label, bx + 4, by + 7);
    }
    x.fillStyle = 'rgba(255,255,255,.67)';
    x.fillRect(X(this.cursorDay), py, 1.2, ph);
    x.font = `10.5px ${font}`;
    x.fillStyle = COLORS.faint;
    if (this.dates.length) {
      x.textAlign = 'left'; x.fillText(this.dates[0], px, py + ph + 12);
      x.textAlign = 'right'; x.fillText(this.dates.at(-1), px + pw, py + ph + 12);
    }
    if (this.hover && this.hover.x >= px && this.hover.x <= px + pw) {
      const d = Math.max(0, Math.min(this.days - 1, Math.round((this.hover.x - px) / pw * n)));
      const hx = X(d);
      x.fillStyle = '#ffe08a'; x.fillRect(hx, py, 1.2, ph);
      const lines = [this.dates[d] ?? `Day ${d + 1}`];
      if (d > this.visibleUntil) lines.push(t('not yet known'));
      else for (const s of this.series) if (d < s.values.length) lines.push(`${t(s.name)}  ${this.fmt(s.values[d])}${s.unit ? ` ${s.unit}` : ''}`);
      x.font = `12px ${font}`;
      const tw = Math.max(...lines.map((l) => x.measureText(l).width));
      const bw = tw + 22, bh = lines.length * 17 + 12;
      const bx = hx + 12 + bw > w ? hx - 12 - bw : hx + 12;
      const by = Math.max(4, Math.min(this.hover.y - 10, h - bh - 4));
      x.fillStyle = 'rgba(16,24,38,.95)';
      roundRect(x, bx, by, bw, bh, 9); x.fill();
      x.strokeStyle = 'rgba(255,255,255,.4)'; x.lineWidth = 1; x.stroke();
      x.textAlign = 'left';
      lines.forEach((l, i) => { x.fillStyle = i === 0 ? '#ffe08a' : COLORS.text; x.font = `${i === 0 ? '600 ' : ''}12px ${font}`; x.fillText(l, bx + 11, by + 14 + i * 17); });
    }
  }

  drawSeries(x, s, X, Y, base) {
    const last = Math.min(this.visibleUntil, s.values.length - 1);
    if (last < 0) return;
    if (s.area) {
      const grad = x.createLinearGradient(0, Y(this.top()), 0, base);
      grad.addColorStop(0, withAlpha(s.color, 0.5));
      grad.addColorStop(1, withAlpha(s.color, 0.04));
      x.fillStyle = grad;
      x.beginPath(); x.moveTo(X(0), base);
      for (let i = 0; i <= last; i++) x.lineTo(X(i), Y(s.values[i]));
      x.lineTo(X(last), base); x.closePath(); x.fill();
    }
    x.strokeStyle = s.color; x.lineWidth = 2; x.lineJoin = 'round';
    x.beginPath();
    for (let i = 0; i <= last; i++) {
      const vx = X(i), vy = Y(s.values[i]);
      if (i === 0) x.moveTo(vx, vy);
      else if (s.step) { x.lineTo(vx, Y(s.values[i - 1])); x.lineTo(vx, vy); }
      else x.lineTo(vx, vy);
    }
    x.stroke();
    const lx = X(last), ly = Y(s.values[last]);
    x.fillStyle = s.color; x.beginPath(); x.arc(lx, ly, 3.6, 0, 7); x.fill();
    x.strokeStyle = withAlpha(s.color, 0.5); x.lineWidth = 1.4; x.beginPath(); x.arc(lx, ly, 6, 0, 7); x.stroke();
  }
}

function niceCeil(v) {
  const p = Math.pow(10, Math.floor(Math.log10(v))), f = v / p;
  return (f <= 1 ? 1 : f <= 2 ? 2 : f <= 2.5 ? 2.5 : f <= 5 ? 5 : 10) * p;
}

export function withAlpha(hex, a) {
  const n = parseInt(hex.slice(1), 16);
  return `rgba(${(n >> 16) & 255},${(n >> 8) & 255},${n & 255},${a})`;
}

function roundRect(x, X0, Y0, w, h, r) {
  x.beginPath();
  x.moveTo(X0 + r, Y0); x.arcTo(X0 + w, Y0, X0 + w, Y0 + h, r); x.arcTo(X0 + w, Y0 + h, X0, Y0 + h, r);
  x.arcTo(X0, Y0 + h, X0, Y0, r); x.arcTo(X0, Y0, X0 + w, Y0, r); x.closePath();
}

const esc = (s) => String(s).replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
