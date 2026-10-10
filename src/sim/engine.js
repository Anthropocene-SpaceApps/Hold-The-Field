// Pure simulation. No DOM, no randomness: same data + same actions = same result.
// 1:1 port of game/src/main/java/org/anthropocene/htf/sim/Engine.java (flood + drought).
// Rendering and UI only read the state objects this module returns; every function returns a new state.
import { isDrought } from './config.js';

const DAY_MS = 86400000;
export const dayNumber = (iso) => Math.round(Date.parse(iso + 'T00:00:00Z') / DAY_MS);
export const addDays = (iso, n) => new Date((dayNumber(iso) + n) * DAY_MS).toISOString().slice(0, 10);

const RANK = { calm: 0, watch: 1, warning: 2 };

/** Scout status for day i: 'calm' | 'watch' | 'warning'. */
export function scoutStatus(days, i, cfg) {
  if (isDrought(cfg)) {
    const rain7 = recentRain(days, i, 7), soil = days[i].soil;
    if (rain7 < cfg.dryWarnMm && soil < cfg.soilWarn) return 'warning';
    if (rain7 < cfg.dryWatchMm && soil < cfg.soilWatch) return 'watch';
    return 'calm';
  }
  const sum = threeDayUpstream(days, i);
  if (sum >= cfg.warningMm) return 'warning';
  if (sum >= cfg.watchMm) return 'watch';
  return 'calm';
}

/** Rain on the farm over the last n days (drought scout). */
export function recentRain(days, i, n) {
  let s = 0;
  for (let k = Math.max(0, i - n + 1); k <= i; k++) s += days[k].rainFarm;
  return s;
}

export function threeDayUpstream(days, i) {
  let s = 0;
  for (let k = Math.max(0, i - 2); k <= i; k++) s += days[k].rainUp;
  return s;
}

/** Variety-specific soil wetness below which the crop suffers (drought scenario). */
export const stressThreshold = (cfg, variety) => cfg.varieties[variety].stressSoil;

/** First day at or after the requested date that the data covers. */
export function indexOnOrAfter(data, iso) {
  for (let k = 0; k < data.days.length; k++) if (data.days[k].date >= iso) return k;
  return data.days.length - 1;
}

export function createState(data, cfg, variety = 'long', transplantIso = cfg.defaultTransplant) {
  const startIndex = indexOnOrAfter(data, transplantIso);
  const s = {
    i: startIndex,                 // index into data.days (the day just simulated)
    startIndex,                    // index of the transplanting day (history[0])
    transplant: data.days[startIndex].date,
    date: data.days[startIndex].date,
    variety,
    coins: cfg.startCoins,
    bund: cfg.bundStart,
    bundRaises: 0,
    level: 0,
    maturity: 0,
    alive: true,
    harvested: false,
    yieldPct: 0,
    underwaterDays: 0,
    flooded: false,
    // drought scenario
    moisture: 0,                   // root-zone wetness the crop feels today: NASA soil plus irrigation
    boost: 0,                      // irrigation still working in the soil
    tank: 0,                       // irrigation water left
    stressLoad: 0,                 // accumulated crop stress; the crop fails at 1
    stressDays: 0,
    irrigations: 0,
    stressed: false,
    status: 'calm',
    finished: false,
    events: [],                    // [{ i, date, type, text }]
    history: [{ level: 0, bund: cfg.bundStart }],
  };
  if (isDrought(cfg)) {
    s.tank = cfg.tankStart;
    s.moisture = data.days[s.i].soil;
    s.history[0] = { level: s.moisture, bund: stressThreshold(cfg, variety) };
  }
  return s;
}

export function maturityOn(dayIso, variety, transplantIso, cfg) {
  const m = (dayNumber(dayIso) - dayNumber(transplantIso)) / cfg.varieties[variety].fieldDays;
  return Math.max(0, Math.min(1, m));
}

/** True once the run is decided: crop saved, lost, or the season ran out. */
export const isOver = (s) => s.finished || s.harvested || !s.alive;

const copy = (s) => ({ ...s, events: s.events.slice(), history: s.history.slice() });

/** Advance one day. Returns a new state. */
export function step(state, data, cfg) {
  if (state.finished) return state;
  const s = copy(state);
  const i = s.i + 1;
  if (i >= data.days.length) {
    s.finished = true;
    if (s.alive && !s.harvested) {
      s.harvested = true;
      s.yieldPct = isDrought(cfg) ? droughtYield(s, cfg) : s.maturity * cfg.varieties[s.variety].potential;
      s.events.push(ev(s, 'harvest', 'Season ended: crop harvested'));
    }
    return s;
  }
  const day = data.days[i];
  const lagged = data.days[Math.max(0, i - cfg.lagDays)];
  s.i = i;
  s.date = day.date;
  if (isDrought(cfg)) return stepDrought(s, state, data, cfg, day, i);

  s.level = Math.max(0, s.level * cfg.drain + cfg.a * lagged.rainUp + cfg.b * day.rainFarm - cfg.baseLoss);
  s.history.push({ level: s.level, bund: s.bund });
  s.status = scoutStatus(data.days, i, cfg);
  if (RANK[s.status] > RANK[state.status]) {
    s.events.push(ev(s, s.status, s.status === 'warning'
      ? 'Scout: FLOOD WARNING, heavy rain upstream'
      : 'Scout: watch, rain building upstream'));
  }

  const wasFlooded = s.flooded;
  s.flooded = s.level > s.bund;
  if (s.flooded && !wasFlooded) s.events.push(ev(s, 'flood', 'Flash flood: water over the bund'));

  if (!s.harvested && s.alive) {
    s.maturity = maturityOn(day.date, s.variety, s.transplant, cfg);
    if (s.flooded) {
      s.underwaterDays += 1;
      if (s.underwaterDays >= cfg.daysUnderwaterToKill) {
        s.alive = false;
        s.yieldPct = 0;
        s.events.push(ev(s, 'loss', 'Crop lost under water'));
      }
    } else {
      s.underwaterDays = 0;
    }
    if (s.alive && s.maturity >= 1) {
      s.harvested = true;
      s.yieldPct = cfg.varieties[s.variety].potential;
      s.events.push(ev(s, 'harvest', 'Full harvest'));
    }
  }
  if (i === data.days.length - 1) return step(s, data, cfg);
  return s;
}

/** One day of the drought scenario. */
function stepDrought(s, prev, data, cfg, day, i) {
  s.boost *= cfg.boostDecay;
  s.tank = Math.min(cfg.tankMax, s.tank + cfg.tankPerMm * day.rainFarm);
  s.moisture = Math.max(0, Math.min(1, day.soil + s.boost));
  const thr = stressThreshold(cfg, s.variety);
  s.history.push({ level: s.moisture, bund: thr });
  s.status = scoutStatus(data.days, i, cfg);
  if (RANK[s.status] > RANK[prev.status] && !announcedRecently(s, s.status, 12)) {
    s.events.push(ev(s, s.status, s.status === 'warning'
      ? 'Scout: DROUGHT WARNING, the rain has stopped and the soil is drying'
      : 'Scout: watch, rain is thinning out and the soil is drying'));
  }
  if (!s.harvested && s.alive) {
    s.maturity = maturityOn(day.date, s.variety, s.transplant, cfg);
    const deficit = Math.max(0, thr - s.moisture) / thr;
    s.stressed = deficit > 0;
    if (s.stressed) {
      // flowering and grain filling (55% to 90% maturity) are the thirsty weeks; ripening hardly cares
      const sensitivity = s.maturity > 0.9 ? 0.3 : s.maturity >= 0.55 ? 2.0 : s.maturity >= 0.35 ? 1.3 : 1.0;
      const heat = day.tmax > cfg.heatC ? 1.5 : 1.0;      // heat on top of dry soil hurts more
      if (s.stressDays === 0) s.events.push(ev(s, 'stress', 'Crop stress: the soil is too dry for the rice'));
      s.stressDays += 1;
      s.stressLoad += sensitivity * heat * deficit * cfg.stressRate;
    }
    if (s.stressLoad >= 1) {
      s.alive = false;
      s.yieldPct = 0;
      s.events.push(ev(s, 'loss', 'Crop failed in the drought'));
    }
    if (s.alive && s.maturity >= 1) {
      s.harvested = true;
      s.yieldPct = droughtYield(s, cfg);
      s.events.push(ev(s, 'harvest', 'Full harvest'));
    }
  }
  if (i === data.days.length - 1) return step(s, data, cfg);
  return s;
}

/** True if the scout already raised this alert within the last n days (a flickering index must not nag). */
function announcedRecently(s, type, n) {
  for (let k = s.events.length - 1; k >= 0; k--) {
    const e = s.events[k];
    if (s.i - e.i > n) break;
    if (e.type === type) return true;
  }
  return false;
}

/** Harvest share: maturity x variety potential x what the dry weeks left of the crop. */
function droughtYield(s, cfg) {
  return s.maturity * cfg.varieties[s.variety].potential * Math.max(0, 1 - s.stressLoad);
}

/** Player actions: 'raiseBund' | 'harvest' | 'irrigate'. Returns { state, ok, reason }. */
export function act(state, type, cfg) {
  const s = copy(state);
  const no = (reason) => ({ state, ok: false, reason });
  const dry = isDrought(cfg);
  switch (type) {
    case 'raiseBund': {
      if (dry) return no('Unknown action');
      if (s.harvested || !s.alive) return no('The season is already decided');
      if (s.bundRaises >= cfg.maxBundRaises) return no('Bund is already at maximum height');
      if (s.coins < cfg.bundRaiseCost) return no('Not enough coins');
      s.coins -= cfg.bundRaiseCost;
      s.bund += cfg.bundRaise;
      s.bundRaises += 1;
      s.history[s.history.length - 1] = { level: s.history.at(-1).level, bund: s.bund };
      s.events.push(ev(s, 'action', `Raised bund to ${s.bund.toFixed(2)} m`));
      return { state: s, ok: true, reason: null };
    }
    case 'irrigate': {
      if (!dry) return no('Unknown action');
      if (s.harvested || !s.alive) return no('The season is already decided');
      if (s.tank < 1) return no('The water tank is empty');
      if (s.coins < cfg.irrigationCost) return no('Not enough coins');
      s.coins -= cfg.irrigationCost;
      s.tank -= 1;
      s.irrigations += 1;
      s.boost = Math.min(cfg.boostMax, s.boost + cfg.irrigationBoost);
      s.moisture = Math.min(1, s.moisture + cfg.irrigationBoost);
      s.history[s.history.length - 1] = { level: s.moisture, bund: stressThreshold(cfg, s.variety) };
      s.events.push(ev(s, 'action', `Irrigated the field, ${Math.round(s.tank)} tank loads left`));
      return { state: s, ok: true, reason: null };
    }
    case 'harvest': {
      if (s.harvested || !s.alive) return no('Nothing to harvest');
      if (s.maturity < cfg.minHarvestMaturity) {
        return no(`Rice is only ${Math.round(s.maturity * 100)}% mature (needs ${Math.round(cfg.minHarvestMaturity * 100)}%)`);
      }
      s.harvested = true;
      s.yieldPct = dry ? droughtYield(s, cfg) : s.maturity * cfg.varieties[s.variety].potential;
      s.events.push(ev(s, 'harvest', `Harvested early at ${Math.round(s.maturity * 100)}% maturity`));
      return { state: s, ok: true, reason: null };
    }
    default:
      return no('Unknown action');
  }
}

/** Run a whole season with a fixed policy. 'rahim' = no actions; 'scout' = act on warnings. */
export function autoplay(data, cfg, policy = 'rahim', variety = policy === 'rahim' ? 'long' : 'short', transplantIso = cfg.defaultTransplant) {
  let s = createState(data, cfg, variety, transplantIso);
  const dry = isDrought(cfg);
  while (!s.finished) {
    s = step(s, data, cfg);
    if (policy !== 'scout' || s.harvested || !s.alive) continue;
    if (dry) {
      // irrigate when the scout is worried and the soil is about to cross the crop's stress line
      if (s.status !== 'calm' && s.moisture < stressThreshold(cfg, s.variety) + 0.14) {
        const r = act(s, 'irrigate', cfg);
        if (r.ok) s = r.state;
      }
    } else {
      if (s.status === 'warning') {
        const h = act(s, 'harvest', cfg);
        if (h.ok) { s = h.state; continue; }
      }
      if (s.status !== 'calm') {
        const r = act(s, 'raiseBund', cfg);
        if (r.ok) s = r.state;
      }
    }
  }
  return s;
}

/** Try every variety and transplanting option on the same real weather.
 *  policy 'rahim' = no warning, no action; 'scout' = act on the satellite warning. */
export function planningWindow(data, cfg, policy = 'rahim') {
  const out = [];
  for (const variety of ['short', 'long']) for (const [date, label] of cfg.transplantOptions) {
    if (data.days[0].date > date) continue;
    const s = autoplay(data, cfg, policy, variety, date);
    out.push({ variety, transplant: date, label, yieldPct: s.yieldPct, lost: !s.alive });
  }
  return out;
}

/** Season recap for the end screen: key days and how much warning the scout gave. */
export function summarize(state) {
  const first = (type) => state.events.find((e) => e.type === type);
  const warning = first('warning');
  const flood = first('flood') ?? first('stress');   // drought: the first day the crop felt it
  return {
    yieldPct: state.yieldPct,
    lost: !state.alive,
    warningDate: warning?.date ?? null,
    floodDate: flood?.date ?? null,
    leadDays: warning && flood ? flood.i - warning.i : null,
    coinsLeft: state.coins,
    actions: state.events.filter((e) => e.type === 'action' || (e.type === 'harvest' && /early/.test(e.text))).length,
  };
}

function ev(s, type, text) {
  return { i: s.i, date: s.date, type, text };
}
