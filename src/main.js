// Boot, game loop and wiring between the sim, the 3D world, the HUD and the screens.
import { BRAND } from './brand.js';
import { loadLanguages, onLanguage, setLanguage, language, translateDom, t, f } from './i18n.js';
import { loadSeason, fetchFromPower, clearLive, cached, provenance, CATALOG } from './data/seasons.js';
import { isDrought, FLOOD } from './sim/config.js';
import { Session, SPEEDS, MODE_RAHIM } from './game/session.js';
import * as Adv from './game/advancements.js';
import { reportText, reportCsv, download } from './game/report.js';
import * as audio from './audio.js';
import { createHud, toast, notice } from './ui/hud.js';
import * as screens from './ui/screens.js';

const $ = (id) => document.getElementById(id);
const mobile = matchMedia('(pointer: coarse)').matches || Math.min(screen.width, screen.height) < 700;
const COLORS = { scout: '#8fd0ff', rahim: '#ffd27a', warn: '#ff6b6b', watch: '#ffc266', flood: '#ff9a6b', loss: '#cccccc', good: '#b8f59a', adv: '#ffff77' };

let world = null;
let session = null;
let lastPlan = null;          // what the player chose on the plan screen
let satellite = false;
let titleSeasonId = 'haor-2017';
const fetching = new Set();
const isFetching = (id) => fetching.has(id);

// ---------------------------------------------------------------- boot
async function boot() {
  await loadLanguages();
  document.documentElement.lang = language();
  translateDom();
  try {
    const { createWorld } = await import('./world/world.js');
    world = createWorld($('world'), { mobile });
  } catch (e) {
    console.error(e);
    document.body.classList.remove('booting');
    $('boot').innerHTML = `<div style="max-width:420px;text-align:center;padding:20px">${t('This device could not start 3D graphics (WebGL).')}<br><br><a style="color:#8fd0ff" href="https://github.com/Anthropocene-SpaceApps/Hold-The-Field">GitHub</a></div>`;
    document.body.classList.add('booting');
    return;
  }
  world.resize();
  addEventListener('resize', () => world.resize());

  // title scene: the haor, before the season starts
  const haor = await loadSeason('haor-2017');
  await loadSeason('barind-2022').catch(() => null);
  showTitleScene(haor);
  hud.setSound(audio.isMuted());
  openTitle();
  document.body.classList.remove('booting');
  requestAnimationFrame(loop);
  // Real data comes from the committed data/*.json (see the nasa-data GitHub Action). Fetching live from NASA POWER
  // in the browser is offered in Data & Model; it is not automatic, because real data must be calibrated first.
  wireInput();
}

function showTitleScene(season) {
  world.setScenario(season.hazard);
  // a mid-season look: green paddy, a heavy sky over the hills
  const s = new Session(season, MODE_RAHIM, 'long');
  let st = s.state;
  for (let k = 0; k < 40 && !s.over; k++) { s.paused = false; s.tick(1); st = s.state; }
  world.setState(st, season.days[st.i], s.cfg, season);
  world.setMode('title');
}

async function refetch(id, { quiet = false } = {}) {
  if (fetching.has(id)) return;
  fetching.add(id);
  screens.refresh();
  const s = await fetchFromPower(id);
  fetching.delete(id);
  if (s) {
    if (!quiet) notice(f('Loaded real NASA POWER data: {} days', s.days.length));
  } else if (!quiet) {
    notice('Could not reach NASA POWER. Using the bundled data.');
  }
  screens.refresh();
}

function dataState() {
  if (fetching.has(titleSeasonId)) return 'fetching';
  const s = cached(titleSeasonId);
  return s ? provenance(s) : 'sample';
}

// ---------------------------------------------------------------- screens
function openTitle() {
  const hadSession = !!session;
  endSession();
  hud.show(false);
  if (hadSession && cached('haor-2017')) showTitleScene(cached('haor-2017'));
  world.setMode('title');
  screens.titleScreen({
    onPlay: () => { audio.unlock(); audio.sfx.click(); openPrologue(); },
    onAbout: () => openAbout(openTitle),
    onAdvancements: () => screens.advancementsScreen({ onClose: openTitle }),
    dataState,
  });
}

function openPrologue() {
  screens.prologueScreen({ season: cached('haor-2017'), cfg: FLOOD, onContinue: () => openPlan(), onBack: openTitle, onLine: () => audio.sfx.beep() });
}

function openPlan(back = openTitle) {
  hud.show(false);
  if (satellite) toggleSatellite(false);
  screens.planScreen({
    initial: lastPlan,
    onBack: () => (session && !session.endShown ? openMenu() : back()),
    onStart: async (plan) => {
      lastPlan = plan;
      audio.unlock();
      const season = await loadSeason(plan.scenario);
      startSession(new Session(season, plan.mode, plan.variety, plan.plantDate));
    },
  });
}

function openAbout(back) {
  if (session) session.paused = true;
  screens.aboutScreen({
    seasons: () => ['haor-2017', 'barind-2022'].map((id) => cached(id)),
    fetching: isFetching,
    onClose: back,
    onRefetch: (id) => refetch(id),
    onUseBundled: async (id) => { clearLive(id); await loadSeason(id); screens.refresh(); },
  });
}

function openMenu() {
  if (!session) return;
  session.paused = true;
  hud.setTime(session);
  screens.menuScreen({
    onResume: () => { screens.close(); hud.update(session); },
    onRestart: () => startSession(new Session(session.season, session.mode, session.variety, session.plantDate)),
    onPlan: () => openPlan(openMenu),
    onAbout: () => openAbout(openMenu),
    onTitle: openTitle,
  });
}

function openDashboard() {
  if (!session) return;
  const wasPaused = session.paused;
  session.paused = true;
  hud.setTime(session);
  screens.dashboardScreen({ session, onClose: () => { screens.close(); session.paused = wasPaused; hud.update(session); } });
}

function showDebrief() {
  session.endShown = true;
  const other = session.baseline();
  grant(Adv.onEnd(session.state, other, session.actionsAllowed(), isDrought(session.cfg)));
  if (satellite) toggleSatellite(false);
  screens.debriefScreen({
    session, other,
    onAgain: () => startSession(new Session(session.season, session.mode, session.variety, session.plantDate)),
    onPlan: () => openPlan(openTitle),
    onReport: (noWarning, withScout) => {
      const stamp = new Date().toISOString().slice(0, 16).replace(/[:T]/g, '-');
      const name = `${BRAND.storageKey}-${session.season.id}-${stamp}`;
      download(`${name}.txt`, reportText(session, other, noWarning, withScout));
      setTimeout(() => download(`${name}.csv`, reportCsv(session), 'text/csv'), 400);
      notice('Report saved: a text summary and a CSV of every day');
    },
    onLook: () => { screens.close(); hud.update(session); },
    onTitle: openTitle,
  });
}

// ---------------------------------------------------------------- sessions
function startSession(s) {
  screens.close();
  session = s;
  satellite = false;
  world.setScenario(s.season.hazard);
  world.setMode('play');
  world.resetCamera();
  world.setState(s.state, s.day, s.cfg, s.season);
  hud.show(true);
  hud.satellite(false);
  hud.clearFeed();
  hud.hideSpeech();
  const dry = isDrought(s.cfg);
  if (dry) {
    hud.say('Rahim', 'Welcome to the Barind. The soil is hard and the sky is wide. Watch the rain.', COLORS.rahim);
    hud.say('Scout', 'Satellite Scout online. NASA measures the rain and the soil wetness at the farm.', COLORS.scout);
  } else {
    hud.say('Rahim', 'Welcome to the haor. Walk to the lake and watch the sky.', COLORS.rahim);
    hud.say('Scout', 'Satellite Scout online. NASA measures the rain in the hills upstream.', COLORS.scout);
  }
  if (s.mode === MODE_RAHIM) hud.say(null, "Rahim's way: no warning reaches the farm and no action is taken. Press Space and watch the real weather decide.", '#b6d8ff');
  if (provenance(s.season) === 'sample') hud.say(null, 'SAMPLE DATA: this season uses the labelled sample file until the real NASA POWER data is added.', '#ff9c9c');
  hud.update(s);
  hud.prompt(s);
}

function endSession() {
  session = null;
  satellite = false;
  hud.satellite(false);
  hud.prompt(null);
}

function togglePause() {
  if (!session || screens.currentScreen()) return;
  audio.unlock();
  if (session.over) { if (session.endShown) showDebrief(); return; }
  session.paused = !session.paused;
  hud.prompt(session);
  hud.setTime(session);
}

function setSpeed(i) {
  if (!session) return;
  session.speedIdx = Math.max(0, Math.min(SPEEDS.length - 1, i));
  hud.setTime(session);
}

function useSlot(i) {
  if (!session || screens.currentScreen()) return;
  audio.unlock();
  hud.flashSlot(i);
  const dry = isDrought(session.cfg);
  if (i === 2) return toggleSatellite();
  if (i === 3) { audio.sfx.click(); return openDashboard(); }
  if (satellite) toggleSatellite(false);
  const type = i === 0 ? (dry ? 'irrigate' : 'raiseBund') : 'harvest';
  const r = session.act(type);
  if (!r.ok) { notice(r.reason); audio.sfx.error(); return; }
  if (type === 'raiseBund') { audio.sfx.place(); world.burst('bund'); }
  if (type === 'irrigate') { audio.sfx.water(); world.burst('water'); }
  afterChange();
}

function toggleSatellite(force) {
  if (!session) return;
  satellite = force ?? !satellite;
  world.setMode(satellite ? 'satellite' : 'play');
  hud.satellite(satellite, session);
  if (satellite) hud.prompt(null); else hud.prompt(session);
  audio.sfx.click();
}

function talkToRahim() {
  const st = session.state, dry = isDrought(session.cfg);
  let line;
  if (!st.alive) line = dry ? 'The sun took it. My father always said water is the real harvest... next season I will watch the soil and keep the tank for the flowering weeks.'
    : 'The water took it. My father always said the sky warns before it takes... next season I will plant earlier and watch the data.';
  else if (st.harvested && st.yieldPct > 0.4) line = dry ? 'The rice is home. We watched the soil, we kept the water for the flowering weeks. Alhamdulillah!'
    : 'The rice is home. We watched the rain in the hills and we did not wait. Alhamdulillah!';
  else if (st.harvested) line = 'We saved a little. Less than I hoped, but more than nothing.';
  else if (!dry && st.flooded) line = 'The water is over the bund! Save what you can, cut the rice if it is ripe enough!';
  else if (dry && st.stressed) line = 'The soil is below what the rice can bear. Irrigate now, from the tank, and hope the rain returns.';
  else if (st.status === 'warning') line = dry ? 'The scout says the rain has stopped and the soil is drying. Irrigate before the leaves roll, and keep some water for later.'
    : 'The scout says very heavy rain in the hills. That water reaches the haor in about two days. Raise the bund, or cut the rice if it is 80% ripe.';
  else if (st.status === 'watch') line = dry ? 'The rain is thinning out. Keep an eye on the scout, and on the soil gauge.'
    : 'Rain is building up in the hills. Keep an eye on the scout, and on the water gauge by the embankment.';
  else if (st.maturity >= session.cfg.minHarvestMaturity) line = dry ? 'The rice is ripe enough to cut. The dry weather is on our side now.' : 'The rice is ripe enough to cut. Every day we wait, the spring rains get closer.';
  else {
    hud.speech(f(dry ? 'The rice is {}% grown. The soil must stay wet while it flowers, and the NASA data shows us when it is drying.'
      : 'The rice is {}% grown. Rain in the hills takes about two days to reach us, so the NASA data gives us a head start.', Math.round(st.maturity * 100)));
    audio.sfx.click();
    return;
  }
  hud.speech(line);
  audio.sfx.click();
}

/** After a sim step or an action: messages, advancements, world and HUD. */
function afterChange() {
  const s = session, dry = isDrought(s.cfg);
  for (const e of s.drainEvents()) {
    switch (e.type) {
      case 'watch': hud.say('Scout', dry ? 'The rain is thinning out and the soil is starting to dry.' : 'Rain is building in the Meghalaya hills.', COLORS.watch); break;
      case 'warning':
        if (dry) {
          hud.say('Scout', 'DROUGHT WARNING: no rain for days and the soil is drying fast. Irrigate before the rice suffers!', COLORS.warn);
          hud.say('Rahim', "The cracks are opening in the neighbours' fields. We must save our water for the flowering weeks.", COLORS.rahim);
        } else {
          hud.say('Scout', 'FLOOD WARNING: heavy rain upstream. Water may reach the haor within days!', COLORS.warn);
          hud.say('Rahim', 'The sky over the hills has been heavy all week. Something is coming.', COLORS.rahim);
        }
        audio.sfx.warning();
        break;
      case 'flood': hud.say('Rahim', 'The water is over the bund!', COLORS.flood); audio.sfx.flood(); world.burst('flood'); break;
      case 'stress': hud.say('Rahim', 'The leaves are rolling up. The rice is thirsty.', COLORS.flood); audio.sfx.error(); break;
      case 'loss': hud.say('Rahim', dry ? 'Everything we planted... dried up in the field.' : 'Everything we planted... gone under the water.', COLORS.loss); audio.sfx.error(); break;
      case 'harvest':
        hud.say('Rahim', s.state.yieldPct > 0 ? 'The rice is home!' : 'The season is over.', COLORS.good);
        if (s.state.yieldPct > 0) { audio.sfx.harvest(); world.burst('harvest'); }
        break;
      case 'action': hud.say('You', e.text, '#ffffff'); break;
      default:
    }
    grant(Adv.onEvent(e.type, s.state, dry));
  }
  world.setState(s.state, s.day, s.cfg, s.season);
  hud.update(s);
  if (!satellite) hud.prompt(s);
}

function grant(list) {
  for (const a of list) {
    toast(a.title);
    audio.sfx.achieve();
    hud.say('Advancement', `${t(a.title)}: ${t(a.description)}`, COLORS.adv);
  }
}

// ---------------------------------------------------------------- loop
let last = 0, slowFrames = 0, frames = 0;
function loop(ts) {
  const raw = last ? (ts - last) / 1000 : 0;
  const dt = Math.min(0.1, raw);
  last = ts;
  if (session) {
    const modal = !!screens.currentScreen();
    if (!modal && session.tick(Math.min(0.5, raw))) afterChange();
    if (session.over && !session.endShown) {
      session.endTimer += Math.min(0.5, raw);
      if (session.endTimer >= 2.4 && !modal) showDebrief();
    }
    if (satellite) hud.satLabels(session, world);
    audio.setRain(Math.min(1, session.day.rainFarm / 35 + (isDrought(session.cfg) ? 0 : session.day.rainUp / 400)));
  } else {
    audio.setRain(0);
  }
  hud.tick(performance.now());
  world.frame(dt);
  // adaptive quality: if frames are slow for a few seconds, lower the resolution
  frames++;
  if (dt > 1 / 32) slowFrames++;
  if (frames >= 180) { if (slowFrames > 90) world.degrade(); frames = 0; slowFrames = 0; }
  requestAnimationFrame(loop);
}

// ---------------------------------------------------------------- input
function wireInput() {
  addEventListener('keydown', (e) => {
    if (e.target.closest?.('input, textarea')) return;
    const scr = screens.currentScreen();
    if (e.key === 'Escape') {
      if (scr === 'menu') { screens.close(); return; }
      if (scr === 'dashboard' || scr === 'about') { $('screen').querySelector('#dClose, #aClose')?.click(); return; }
      if (satellite) { toggleSatellite(false); return; }
      if (session && !scr) { openMenu(); return; }
      return;
    }
    if (!session) return;
    if (scr === 'dashboard' && (e.key === '4' || e.key.toLowerCase() === 'e')) { $('dClose')?.click(); return; }
    if (scr) return;
    const k = e.key.toLowerCase();
    if (e.code === 'Space' || k === 'p') { e.preventDefault(); togglePause(); }
    else if (k === '1' || k === '2' || k === '3' || k === '4') useSlot(Number(k) - 1);
    else if (k === 'm') useSlot(2);
    else if (k === 'e') useSlot(3);
    else if (k === '+' || k === '=') setSpeed(session.speedIdx + 1);
    else if (k === '-' || k === '_') setSpeed(session.speedIdx - 1);
    else if (k === 'r') world.resetCamera();
  });
  // click Rahim (a tap that is not a drag)
  const canvas = $('world');
  let down = null;
  canvas.addEventListener('pointerdown', (e) => { down = { x: e.clientX, y: e.clientY }; audio.unlock(); });
  canvas.addEventListener('pointerup', (e) => {
    if (!down || !session || screens.currentScreen()) return;
    if (Math.hypot(e.clientX - down.x, e.clientY - down.y) < 6 && world.pickRahim(e.clientX, e.clientY)) talkToRahim();
    down = null;
  });
  document.addEventListener('visibilitychange', () => {
    if (document.hidden && session && !session.paused && !session.over) { session.paused = true; hud.setTime(session); }
  });
  onLanguage(() => {
    translateDom();
    screens.refresh();
    if (session) { hud.update(session); hud.prompt(session); if (satellite) hud.satellite(true, session); }
  });
}

const hud = createHud({
  onSlot: useSlot,
  onTime: togglePause,
  onSpeed: setSpeed,
  onMenu: openMenu,
  onSound: () => { audio.unlock(); hud.setSound(audio.toggleMute()); },
  onSatBack: () => toggleSatellite(false),
});

// expose a tiny hook for automated screenshots / debugging
window.__agrocene = {
  get session() { return session; },
  /** Fast-forward n days (testing and screen recording). */
  advance(n = 1) { for (let k = 0; k < n && session && !session.over; k++) { session.paused = false; session.acc = 1; session.tick(0); afterChange(); } if (session) { session.paused = true; hud.setTime(session); } }, setLanguage, useSlot, togglePause, openDashboard, showDebrief: () => session && showDebrief(), toggleSatellite, CATALOG };

boot();
