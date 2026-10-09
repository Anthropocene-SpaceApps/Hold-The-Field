// Boot + game loop + wiring between sim, renderer and UI.
import { CONFIG } from './sim/config.js';
import { createState, step, act, autoplay, summarize } from './sim/engine.js';
import { createFarmRenderer } from './render/farm.js';
import { renderScout, fmtDate, drawSeason, fillTimeline } from './ui/scout.js';
import { loadSeason } from './data/loader.js';

const $ = (id) => document.getElementById(id);
const MS_PER_DAY = { 1: 380, 2: 150 };

let data, state, playing = false, speed = 1, acc = 0, last = 0, endShown = false;
const farm = createFarmRenderer($('farm'));

async function boot() {
  try {
    data = await loadSeason('haor-2017');
  } catch (e) {
    document.body.insertAdjacentHTML('afterbegin', `<p style="padding:16px;color:#c0452f">${e.message}</p>`);
    return;
  }
  $('sampleBadge').hidden = !data.sample;
  const { farm: f, upstream: u } = data.points;
  $('source').textContent = `${data.source} · farm ${f.lat}°N ${f.lon}°E · upstream ${u.lat}°N ${u.lon}°E`;
  $('aboutData').textContent = `Daily data from ${data.source}, ${fmtDate(data.days[0].date)} to ${fmtDate(data.days.at(-1).date)}. ` +
    `Farm point ${f.lat}°N ${f.lon}°E (Sunamganj haor); upstream point ${u.lat}°N ${u.lon}°E (Meghalaya hills).` +
    (data.sample ? ' THIS IS SAMPLE DATA: replace it by running the data pipeline.' : '');
  reset();
  wire();
  showHintOnce();
  requestAnimationFrame(loop);
}

function reset() {
  const mode = $('mode').value;
  if (mode === 'rahim') $('variety').value = 'long';
  state = createState(data, CONFIG, $('variety').value);
  playing = false; endShown = false; acc = 0;
  updateUI();
}

function loop(ts) {
  const dt = last ? ts - last : 0; last = ts;
  if (playing && !state.finished) {
    acc += dt;
    while (acc >= MS_PER_DAY[speed] && !state.finished) {
      acc -= MS_PER_DAY[speed];
      state = step(state, data, CONFIG);
    }
    updateUI();
  }
  farm.draw(state, CONFIG, ts);
  if (state.finished && !endShown) showEnd();
  requestAnimationFrame(loop);
}

function updateUI() {
  const rahim = $('mode').value === 'rahim';
  $('date').textContent = fmtDate(state.date);
  $('play').textContent = playing ? '❚❚ Pause' : '▶ Play';
  $('coins').textContent = state.coins;
  $('matFill').style.width = `${Math.round(state.maturity * 100)}%`;
  $('matPct').textContent = `${Math.round(state.maturity * 100)}%`;
  $('variety').disabled = state.i > 0 || rahim;
  $('bundBtn').disabled = rahim || state.harvested || !state.alive || state.bundRaises >= CONFIG.maxBundRaises || state.coins < CONFIG.bundRaiseCost;
  $('harvestBtn').disabled = rahim || state.harvested || !state.alive || state.maturity < CONFIG.minHarvestMaturity;
  renderScout(state, data, CONFIG);
}

function doAction(type) {
  const r = act(state, { type }, CONFIG);
  if (!r.ok) return toast(r.reason);
  state = r.state;
  updateUI();
}

function showEnd() {
  endShown = true; playing = false; updateUI();
  const rahim = $('mode').value === 'rahim';
  const other = rahim ? autoplay(data, CONFIG, 'scout') : autoplay(data, CONFIG, 'rahim');
  $('endYouLabel').textContent = rahim ? "Rahim's way (no scout)" : 'You, with the scout';
  $('endOtherLabel').textContent = rahim ? 'With the scout' : "Rahim's way (no scout)";
  $('endYou').textContent = `${Math.round(state.yieldPct * 100)}%`;
  $('endOther').textContent = `${Math.round(other.yieldPct * 100)}%`;
  $('endTitle').textContent = state.yieldPct > 0 ? 'The rice is home' : 'The water took it all';
  const sum = summarize(state);
  $('endLead').textContent = sum.leadDays === null
    ? (sum.floodDate ? 'The flood came, and no scout warning came before it.' : 'No flood reached the bund this season.')
    : `NASA data warned ${sum.leadDays} day${sum.leadDays === 1 ? '' : 's'} before the water arrived (${fmtDate(sum.warningDate)} → ${fmtDate(sum.floodDate)}).`;
  $('endLine').textContent = 'Same field, same real rain. The only difference is whether the warning reached the farmer.';
  $('end').showModal();
  drawSeason($('seasonChart'), state, data);
  fillTimeline($('endLog'), state.events);
}

function toast(msg) {
  const t = $('toast'); t.textContent = msg; t.hidden = false;
  clearTimeout(toast.t); toast.t = setTimeout(() => (t.hidden = true), 2200);
}

function showHintOnce() {
  let seen = false;
  try { seen = localStorage.getItem('htf-hint') === '1'; } catch { /* storage unavailable */ }
  if (!seen) $('hint').showModal();
}

function wire() {
  $('play').onclick = () => { if (state.finished) reset(); playing = !playing; updateUI(); };
  $('speed').onclick = () => { speed = speed === 1 ? 2 : 1; $('speed').textContent = `${speed}×`; };
  $('reset').onclick = reset;
  $('mode').onchange = reset;
  $('variety').onchange = () => { if (state.i === 0) reset(); };
  $('bundBtn').onclick = () => doAction('raiseBund');
  $('harvestBtn').onclick = () => doAction('harvest');
  $('aboutBtn').onclick = () => $('about').showModal();
  $('aboutOk').onclick = () => $('about').close();
  $('hintOk').onclick = () => { $('hint').close(); try { localStorage.setItem('htf-hint', '1'); } catch { /* ignore */ } };
  $('endAgain').onclick = () => { $('end').close(); reset(); };
  window.addEventListener('keydown', (e) => {
    if (e.target.tagName === 'SELECT') return;
    if (e.code === 'Space') { e.preventDefault(); $('play').click(); }
    if (e.key === 'b') $('bundBtn').click();
    if (e.key === 'h') $('harvestBtn').click();
  });
}

boot();
