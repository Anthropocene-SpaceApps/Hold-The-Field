// Pure simulation. No DOM, no randomness: same data + same actions = same result.
// Rendering and UI only read the state this module returns.

const DAY_MS = 86400000;
const toDay = (iso) => Math.round(Date.parse(iso + 'T00:00:00Z') / DAY_MS);

/** Scout status for day i: 'calm' | 'watch' | 'warning' from the 3-day upstream rain sum. */
export function scoutStatus(days, i, cfg) {
  const sum = threeDayUpstream(days, i);
  if (sum >= cfg.warningMm) return 'warning';
  if (sum >= cfg.watchMm) return 'watch';
  return 'calm';
}

export function threeDayUpstream(days, i) {
  let s = 0;
  for (let k = Math.max(0, i - 2); k <= i; k++) s += days[k].rainUp;
  return s;
}

export function createState(data, cfg, variety = 'long') {
  return {
    i: 0,                        // index into data.days (the day just simulated)
    date: data.days[0].date,
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
    status: 'calm',
    finished: false,
    events: [],                  // [{i, date, type, text}]
    history: [{ level: 0, bund: cfg.bundStart }], // per simulated day, for the season chart
  };
}

export function maturityOn(dayIso, variety, cfg) {
  const d = toDay(dayIso) - toDay(cfg.transplantDate);
  return Math.max(0, Math.min(1, d / cfg.varieties[variety].fieldDays));
}

/** Advance one day. Returns a new state. */
export function step(state, data, cfg) {
  if (state.finished) return state;
  const s = { ...state, events: state.events.slice(), history: state.history.slice() };
  const i = s.i + 1;
  if (i >= data.days.length) {
    s.finished = true;
    if (s.alive && !s.harvested) {
      s.harvested = true;
      s.yieldPct = s.maturity;
      s.events.push(ev(s, 'harvest', 'Season ended: crop harvested'));
    }
    return s;
  }
  const day = data.days[i];
  const lagged = data.days[Math.max(0, i - cfg.lagDays)];
  s.i = i;
  s.date = day.date;
  s.level = Math.max(0, s.level * cfg.drain + cfg.a * lagged.rainUp + cfg.b * day.rainFarm - cfg.baseLoss);
  s.history.push({ level: s.level, bund: s.bund });
  s.status = scoutStatus(data.days, i, cfg);
  const rank = { calm: 0, watch: 1, warning: 2 };
  if (rank[s.status] > rank[state.status]) {
    s.events.push(ev(s, s.status, s.status === 'warning' ? 'Scout: FLOOD WARNING, heavy rain upstream' : 'Scout: watch, rain building upstream'));
  }

  const wasFlooded = s.flooded;
  s.flooded = s.level > s.bund;
  if (s.flooded && !wasFlooded) s.events.push(ev(s, 'flood', 'Flash flood: water over the bund'));

  if (!s.harvested && s.alive) {
    s.maturity = maturityOn(day.date, s.variety, cfg);
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
      s.yieldPct = 1;
      s.events.push(ev(s, 'harvest', 'Full harvest'));
    }
  }
  if (i === data.days.length - 1) return step(s, data, cfg);
  return s;
}

/** Player actions. Returns { state, ok, reason }. */
export function act(state, action, cfg) {
  const s = { ...state, events: state.events.slice(), history: state.history.slice() };
  switch (action.type) {
    case 'raiseBund':
      if (s.bundRaises >= cfg.maxBundRaises) return { state, ok: false, reason: 'Bund is already at maximum height' };
      if (s.coins < cfg.bundRaiseCost) return { state, ok: false, reason: 'Not enough coins' };
      s.coins -= cfg.bundRaiseCost;
      s.bund += cfg.bundRaise;
      s.bundRaises += 1;
      s.history = s.history.slice(0, -1).concat({ ...s.history.at(-1), bund: s.bund });
      s.events.push(ev(s, 'action', `Raised bund to ${s.bund.toFixed(2)} m`));
      return { state: s, ok: true };
    case 'harvest':
      if (s.harvested || !s.alive) return { state, ok: false, reason: 'Nothing to harvest' };
      if (s.maturity < cfg.minHarvestMaturity) return { state, ok: false, reason: `Rice is only ${Math.round(s.maturity * 100)}% mature (needs ${cfg.minHarvestMaturity * 100}%)` };
      s.harvested = true;
      s.yieldPct = s.maturity;
      s.events.push(ev(s, 'harvest', `Harvested early at ${Math.round(s.maturity * 100)}%`));
      return { state: s, ok: true };
    default:
      return { state, ok: false, reason: 'Unknown action' };
  }
}

/** Run a whole season with a fixed policy. 'rahim' = no actions; 'scout' = act on warnings. */
export function autoplay(data, cfg, policy = 'rahim', variety = policy === 'rahim' ? 'long' : 'short') {
  let s = createState(data, cfg, variety);
  while (!s.finished) {
    s = step(s, data, cfg);
    if (policy === 'scout' && !s.harvested && s.alive) {
      if (s.status === 'warning') {
        const h = act(s, { type: 'harvest' }, cfg);
        if (h.ok) { s = h.state; continue; }
      }
      if (s.status !== 'calm') {
        const r = act(s, { type: 'raiseBund' }, cfg);
        if (r.ok) s = r.state;
      }
    }
  }
  return s;
}

/** Season recap for the end screen: key days and how much warning the scout gave. */
export function summarize(state) {
  const first = (type) => state.events.find((e) => e.type === type);
  const warning = first('warning'), flood = first('flood');
  return {
    yieldPct: state.yieldPct,
    lost: !state.alive,
    warningDate: warning?.date ?? null,
    floodDate: flood?.date ?? null,
    leadDays: warning && flood ? flood.i - warning.i : null, // days between the scout's warning and the water
    coinsLeft: state.coins,
    actions: state.events.filter((e) => e.type === 'action' || (e.type === 'harvest' && /early/.test(e.text))).length,
  };
}

function ev(s, type, text) {
  return { i: s.i, date: s.date, type, text };
}
