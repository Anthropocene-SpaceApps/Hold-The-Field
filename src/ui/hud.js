// In-game overlay: season timeline, NASA data card, water/soil gauge, action bar, field notes, Rahim's speech,
// satellite labels. Same layout and words as the desktop build's ui/Hud.java.
import { t, f, date, num } from '../i18n.js';
import { recentRain, threeDayUpstream, scoutStatus, stressThreshold } from '../sim/engine.js';
import { isDrought } from '../sim/config.js';
import { SPEEDS } from '../game/session.js';
import { provenance } from '../data/seasons.js';
import { COLORS, icon } from './theme.js';
import { withAlpha } from './chart.js';

const $ = (id) => document.getElementById(id);
const EVENT_COLORS = { warning: COLORS.bad, flood: COLORS.water, stress: COLORS.temp, harvest: COLORS.good, loss: '#dddddd', action: COLORS.soil };

export const ITEMS = [
  { id: 'primary', icon: 'bund', name: 'Raise bund', key: '1' },
  { id: 'harvest', icon: 'sickle', name: 'Harvest', key: '2' },
  { id: 'satellite', icon: 'satellite', name: 'Satellite view', key: '3' },
  { id: 'dashboard', icon: 'chart', name: 'Dashboard', key: '4' },
];

export function createHud({ onSlot, onTime, onSpeed, onMenu, onSound, onSatBack }) {
  const root = $('hud');
  const feedEl = $('feed');
  const lines = [];
  let speechTimer = 0;

  // slots
  $('slots').innerHTML = ITEMS.map((it, i) => `<button class="slot" data-slot="${i}"><kbd>${it.key}</kbd><span class="badge"></span>${icon(it.icon)}<b></b></button>`).join('');
  $('slots').addEventListener('click', (e) => {
    const b = e.target.closest('.slot');
    if (b) onSlot(Number(b.dataset.slot));
  });
  $('timeChip').addEventListener('click', onTime);
  $('promptPlay').addEventListener('click', onTime);
  $('speedBox').addEventListener('click', (e) => { const b = e.target.closest('button'); if (b) onSpeed(Number(b.dataset.speed)); });
  $('menuBtn').innerHTML = icon('menu');
  $('menuBtn').addEventListener('click', onMenu);
  $('soundBtn').addEventListener('click', onSound);
  $('satBack').addEventListener('click', onSatBack);

  function show(on) { root.hidden = !on; }

  function setSound(muted) { $('soundBtn').innerHTML = icon(muted ? 'mute' : 'sound'); }

  /** Everything that depends on the sim state. Cheap: called after every simulated day or action. */
  function update(s) {
    const st = s.state, cfg = s.cfg, dry = isDrought(cfg), days = s.season.days, d = days[st.i];
    const n = days.length, from = st.startIndex;
    // season card
    $('hudDate').textContent = date(st.date);
    $('hudDay').textContent = f('Day {} of {}', st.i - from + 1, n - from);
    const prog = (st.i - from) / Math.max(1, n - 1 - from);
    $('tlFill').style.width = `${prog * 100}%`;
    $('tlNow').style.left = `${prog * 100}%`;
    $('tlDots').innerHTML = st.events.filter((e) => EVENT_COLORS[e.type])
      .map((e) => `<i style="left:${((e.i - from) / Math.max(1, n - 1 - from)) * 100}%;background:${EVENT_COLORS[e.type]}"></i>`).join('');
    const chip = $('scoutChip');
    chip.className = `chip scout-chip ${st.status}`;
    const statusText = st.status === 'warning' ? (dry ? 'DROUGHT WARNING' : 'FLOOD WARNING') : st.status === 'watch' ? 'WATCH' : 'CALM';
    $('scoutText').textContent = f('SCOUT  {}', t(statusText));

    // NASA data card
    const prov = provenance(s.season);
    $('dataLabel').textContent = f('{}  /  today', t(prov === 'sample' ? 'SAMPLE DATA' : 'NASA POWER'));
    const metric = (ic, color, value, label) => `<div class="metric">${icon(ic)}<b>${value}</b><span>${t(label)}</span></div>`.replace('<svg', `<svg style="color:${color}"`);
    $('metrics').innerHTML = (dry
      ? metric('rain', COLORS.good, f('{} mm', d.rainFarm.toFixed(0)), 'rain on farm') + metric('rain', COLORS.rain, f('{} mm', recentRain(days, st.i, 7).toFixed(0)), 'rain, last 7 days')
      : metric('rain', COLORS.rain, f('{} mm', d.rainUp.toFixed(0)), 'rain upstream') + metric('rain', COLORS.good, f('{} mm', d.rainFarm.toFixed(0)), 'rain on farm'))
      + metric('thermo', COLORS.temp, f('{} C', d.tmax.toFixed(0)), 'max temperature')
      + metric('soil', COLORS.soil, num(d.soil.toFixed(2)), 'soil wetness');
    drawSpark($('spark'), s);

    // gauge
    const max = dry ? 1 : 1.6;
    const value = dry ? st.moisture : st.level, line = dry ? stressThreshold(cfg, st.variety) : st.bund;
    const wc = dry ? (value < line ? COLORS.bad : value < line + 0.08 ? COLORS.warn : COLORS.water)
      : (st.level > st.bund ? COLORS.bad : st.level > st.bund * 0.85 ? COLORS.warn : COLORS.water);
    $('gaugeLabel').textContent = t(dry ? 'Soil' : 'Water');
    $('gaugeFill').style.height = `${Math.min(1, value / max) * 100}%`;
    $('gaugeFill').style.background = wc;
    const boost = dry && st.boost > 0.01 ? Math.min(st.boost, value) / max : 0;
    $('gaugeBoost').style.height = `${boost * 100}%`;
    $('gaugeBoost').style.bottom = `${Math.max(0, value / max - boost) * 100}%`;
    $('gaugeLine').style.bottom = `${(line / max) * 100}%`;
    $('gaugeLineText').textContent = t(dry ? 'stress' : 'bund');
    $('gaugeVal').textContent = dry ? num(value.toFixed(2)) : f('{} m', st.level.toFixed(2));
    $('gaugeVal').style.color = wc;
    if (root.dataset.gaugeFor !== String(dry)) {
      root.dataset.gaugeFor = String(dry);
      let ticks = '';
      for (let i = 0; i <= (dry ? 4 : 6); i++) {
        const v = i * 0.25, y = 100 - (v / max) * 100;
        ticks += `<i style="top:${y}%;width:${i % 2 ? 4 : 7}px"></i>${i % 2 === 0 ? `<span style="top:${y}%">${num(v.toFixed(1))}</span>` : ''}`;
      }
      $('gaugeTicks').innerHTML = ticks;
    }

    // maturity, coins, slots
    const m = st.maturity;
    $('matFill').style.width = `${m * 100}%`;
    $('matFill').classList.toggle('ready', m >= 0.8);
    $('matText').textContent = f('Rice {}%', Math.round(m * 100));
    $('matHint').textContent = t(m >= 0.8 ? 'ready to cut' : 'ready at 80%');
    $('matHint').style.color = m >= 0.8 ? COLORS.good : '';
    $('coins').innerHTML = `${icon('coin')} ${f('Tk {}', st.coins)}`;
    const slots = $('slots').children;
    const allowed = s.actionsAllowed() && !s.over;
    ITEMS.forEach((it, i) => {
      const el = slots[i];
      const name = i === 0 ? (dry ? 'Irrigate' : 'Raise bund') : it.name;
      el.querySelector('b').textContent = t(name);
      if (i === 0) el.querySelector('svg').outerHTML = icon(dry ? 'drop' : 'bund');
      const usable = i >= 2 || allowed;
      el.classList.toggle('off', !usable);
      el.title = t(i === 0 ? (dry ? 'Irrigate the rice from the village tank' : 'Raise the earth embankment between the field and the haor') : i === 1 ? 'Cut the rice (from 80% maturity)' : i === 2 ? 'See what NASA sees' : 'Charts of the season so far');
      const badge = el.querySelector('.badge');
      let b = '', bad = false;
      if (i === 0) {
        if (dry) { b = f('Tank {}', Math.floor(st.tank)); bad = st.tank < 1; }
        else { const left = cfg.maxBundRaises - st.bundRaises; b = left > 0 ? f('Tk {}', cfg.bundRaiseCost) : t('max'); bad = left <= 0; }
      } else if (i === 1) b = m >= cfg.minHarvestMaturity ? t('ready') : '';
      badge.textContent = b;
      badge.classList.toggle('bad', bad);
    });
    // time chip
    setTime(s);
  }

  function setTime(s) {
    const paused = s.paused || s.over;
    $('timeChip').classList.toggle('paused', paused);
    $('timeIcon').innerHTML = icon(paused ? 'pause' : 'play');
    $('timeText').textContent = paused ? t('Paused') : f('Running  {}', `${SPEEDS[s.speedIdx]}x`);
    [...$('speedBox').children].forEach((b, i) => b.classList.toggle('on', i === s.speedIdx));
  }

  function drawSpark(c, s) {
    const dpr = Math.min(window.devicePixelRatio || 1, 2), w = c.clientWidth, h = c.clientHeight;
    if (!w) return;
    c.width = w * dpr; c.height = h * dpr;
    const x = c.getContext('2d');
    x.setTransform(dpr, 0, 0, dpr, 0, 0);
    const st = s.state, days = s.season.days, dry = isDrought(s.cfg), from = Math.max(0, st.i - 13);
    let max = dry ? 40 : 120;
    for (let i = from; i <= st.i; i++) max = Math.max(max, dry ? days[i].rainFarm : days[i].rainUp);
    const bw = w / 14;
    for (let i = from; i <= st.i; i++) {
      let col, v;
      if (dry) { const sc = scoutStatus(days, i, s.cfg); col = sc === 'warning' ? COLORS.bad : sc === 'watch' ? COLORS.warn : COLORS.rain; v = days[i].rainFarm; }
      else { const s3 = threeDayUpstream(days, i); col = s3 >= s.cfg.warningMm ? COLORS.bad : s3 >= s.cfg.watchMm ? COLORS.warn : COLORS.rain; v = days[i].rainUp; }
      const bh = Math.max(2, (v / max) * h);
      x.fillStyle = col;
      x.beginPath();
      x.roundRect ? x.roundRect((i - from) * bw + 1, h - bh, bw - 2, bh, 1.5) : x.rect((i - from) * bw + 1, h - bh, bw - 2, bh);
      x.fill();
    }
  }

  /** A line in the field notes: <Sender> message. */
  function say(sender, msg, color = '#ffffff') {
    const li = document.createElement('li');
    li.style.setProperty('--c', color);
    li.textContent = sender ? `<${t(sender)}> ${t(msg)}` : t(msg);
    feedEl.append(li);
    lines.push({ li, born: performance.now() });
    while (feedEl.children.length > 4) feedEl.firstElementChild.remove();
  }

  function clearFeed() { feedEl.innerHTML = ''; lines.length = 0; }

  function speech(text) {
    $('speechText').textContent = t(text);
    $('speech').hidden = false;
    clearTimeout(speechTimer);
    speechTimer = setTimeout(() => { $('speech').hidden = true; }, 9000);
  }
  function hideSpeech() { $('speech').hidden = true; }

  function prompt(s) {
    const show = s && s.paused && s.state.i === s.state.startIndex && !s.over;
    $('prompt').hidden = !show;
    if (!show) return;
    const touch = matchMedia('(pointer: coarse)').matches;
    $('promptTitle').textContent = touch ? t('Tap Play to begin the season') : t('Press Space to begin the season');
    $('promptText').textContent = t(isDrought(s.cfg)
      ? 'Look around first: drag to turn, scroll or pinch to zoom. Press 3 for the NASA satellite view.'
      : 'Look around first: drag to turn, scroll or pinch to zoom over the embankment. Press 3 for the NASA satellite view.');
    $('promptPlay').innerHTML = `${icon('play')} ${t('Start the season')}`;
    $('promptPlay').className = 'btn primary big';
  }

  /** Fade old notes (called every frame). */
  function tick(now) {
    for (const l of lines) if (now - l.born > 12000) l.li.classList.add('old');
  }

  // ---------------------------------------------------------------- satellite view
  function satellite(on, s) {
    $('satOverlay').hidden = !on;
    root.classList.toggle('sat', on);
    for (const id of ['slots', 'coins']) $(id).style.visibility = on ? 'hidden' : '';
    document.querySelector('.maturity').style.visibility = on ? 'hidden' : '';
    if (on && s) {
      const dry = isDrought(s.cfg);
      $('satSub').textContent = t(dry ? 'When the rain stops, the soil dries out within days' : 'Rain over the Meghalaya hills reaches the haor about two days later');
      $('legendRain').textContent = t(dry ? 'rain intensity' : 'rain intensity (upstream)');
    }
  }

  function satLabels(s, world) {
    const st = s.state, d = s.season.days[st.i], dry = isDrought(s.cfg), p = s.season.points;
    const a = world.anchors();
    const items = [];
    if (!dry) items.push({ at: a.upstream, color: COLORS.rain, title: t('UPSTREAM  Meghalaya hills'), coords: f('{} N  {} E', p.upstream.lat.toFixed(2), p.upstream.lon.toFixed(2)), data: f('rain {} mm/day   3-day {} mm', d.rainUp.toFixed(0), threeDayUpstream(s.season.days, st.i).toFixed(0)) });
    items.push({
      at: a.farm, color: COLORS.crop, title: t(dry ? 'FARM  Barind Tract' : 'FARM  Sunamganj haor'), coords: f('{} N  {} E', p.farm.lat.toFixed(2), p.farm.lon.toFixed(2)),
      data: dry ? f('rain {} mm   7-day {} mm   soil {}', d.rainFarm.toFixed(0), recentRain(s.season.days, st.i, 7).toFixed(0), d.soil.toFixed(2))
        : f('rain {} mm   soil {}   water {} m', d.rainFarm.toFixed(0), d.soil.toFixed(2), st.level.toFixed(2)),
    });
    $('satLabels').innerHTML = items.map((it) => {
      const pt = world.project(it.at);
      if (!pt) return '';
      return `<div class="pt" style="left:${pt.x}px;top:${pt.y}px"><div class="box"><b style="color:${it.color}">${it.title}</b><br>${it.coords}<br><span class="muted">${it.data}</span></div><div class="stem" style="background:${it.color}"></div><div class="dot" style="background:${it.color}"></div></div>`;
    }).join('');
  }

  function flashSlot(i) {
    const el = $('slots').children[i];
    if (!el) return;
    el.classList.remove('flash');
    void el.offsetWidth;
    el.classList.add('flash');
  }

  return { show, update, setTime, say, clearFeed, speech, hideSpeech, prompt, tick, satellite, satLabels, flashSlot, setSound };
}

/** Advancement toast (top centre) or a short notice. */
export function toast(title, header = 'Advancement made') {
  const host = $('toasts');
  const el = document.createElement('div');
  el.className = 'card toast';
  el.innerHTML = `<div class="medal">${icon('wheat')}</div><div><small>${t(header)}</small><b>${t(title)}</b></div>`;
  host.append(el);
  setTimeout(() => el.classList.add('out'), 4800);
  setTimeout(() => el.remove(), 5400);
}

export function notice(msg) {
  const host = $('toasts');
  const el = document.createElement('div');
  el.className = 'card toast note';
  el.textContent = t(msg);
  host.append(el);
  while (host.children.length > 3) host.firstElementChild.remove();
  setTimeout(() => el.classList.add('out'), 2600);
  setTimeout(() => el.remove(), 3100);
}

export { withAlpha };
