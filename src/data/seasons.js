// Season catalog and loading. Every season is NASA POWER daily point data (see docs/DATA.md).
// Order of preference: the committed data/<id>.json if it is real data; otherwise a copy fetched live from the
// NASA POWER API in the browser (cached on this device); otherwise the committed, clearly labelled SAMPLE file.
import { BRAND } from '../brand.js';

export const CATALOG = [
  {
    id: 'haor-2017', region: 'haor', hazard: 'flood', start: '20161201', end: '20170430',
    name: 'Sunamganj Haor, Boro 2017', tag: 'Flash flood',
    blurb: 'Replay the season that ended in a flash flood. NASA POWER daily rain, temperature and soil wetness for the farm and the Meghalaya hills upstream.',
    farm: { lat: 25.07, lon: 91.40 }, upstream: { lat: 25.27, lon: 91.73 },
  },
  {
    id: 'barind-2022', region: 'barind', hazard: 'drought', start: '20220625', end: '20221215',
    name: 'Barind Tract, Aman 2022', tag: 'Drought',
    blurb: 'The monsoon stops while the rice is flowering. NASA POWER rain, soil wetness and heat show the dry spell coming; irrigate from the village tank before the crop suffers.',
    farm: { lat: 24.60, lon: 88.50 }, upstream: { lat: 24.60, lon: 88.50 },
  },
  {
    id: 'coast', region: 'coast', hazard: 'salinity', locked: true,
    name: 'Satkhira Coast', tag: 'Salt surge · coming in November',
  },
];

const POWER = 'https://power.larc.nasa.gov/api/temporal/daily/point';
const PARAMS = ['PRECTOTCORR', 'T2M_MAX', 'GWETROOT'];
const cacheKey = (id) => `${BRAND.storageKey}-power-${id}`;
const loaded = new Map();      // id -> season
const listeners = new Set();

export const onSeasonUpdate = (fn) => listeners.add(fn);
export const entry = (id) => CATALOG.find((e) => e.id === id);
export const cached = (id) => loaded.get(id);

/** Load a season: real committed file > live NASA copy cached on this device > sample. */
export async function loadSeason(id) {
  if (loaded.has(id)) return loaded.get(id);
  const res = await fetch(`data/${id}.json`);
  if (!res.ok) throw new Error(`No season data for '${id}'`);
  let season = normalise(await res.json(), id);
  if (season.sample) {
    const live = readCache(id);
    if (live) season = live;
  }
  loaded.set(id, season);
  return season;
}

/** Fetch the season straight from NASA POWER in the browser. Resolves to the season, or null if it fails. */
export async function fetchFromPower(id, { timeoutMs = 20000 } = {}) {
  const e = entry(id);
  if (!e || e.locked) return null;
  const ctl = new AbortController();
  const timer = setTimeout(() => ctl.abort(), timeoutMs);
  try {
    const get = async (pt) => {
      const q = new URLSearchParams({ parameters: PARAMS.join(','), community: 'AG', latitude: pt.lat, longitude: pt.lon, start: e.start, end: e.end, format: 'JSON' });
      const r = await fetch(`${POWER}?${q}`, { signal: ctl.signal });
      if (!r.ok) throw new Error(`POWER ${r.status}`);
      const p = (await r.json())?.properties?.parameter;
      if (!p || PARAMS.some((k) => !p[k])) throw new Error('POWER returned no data');
      return p;
    };
    const farm = await get(e.farm);
    const same = e.farm.lat === e.upstream.lat && e.farm.lon === e.upstream.lon;
    const up = same ? farm : await get(e.upstream);
    const clean = (v, def = 0) => (v == null || v <= -998 ? def : Number(v));
    let gaps = 0;
    const days = Object.keys(farm.PRECTOTCORR).sort().map((k) => {
      const raw = [farm.PRECTOTCORR[k], up.PRECTOTCORR[k], farm.T2M_MAX[k], farm.GWETROOT[k]];
      gaps += raw.filter((v) => v == null || v <= -998).length;
      return {
        date: `${k.slice(0, 4)}-${k.slice(4, 6)}-${k.slice(6)}`,
        rainUp: +clean(raw[1]).toFixed(1),
        rainFarm: +clean(raw[0]).toFixed(1),
        tmax: +clean(raw[2], 30).toFixed(1),
        soil: +clean(raw[3], 0.5).toFixed(3),
      };
    });
    const season = normalise({
      id, region: e.region, hazard: e.hazard, season: `${e.start}-${e.end}`, sample: false, live: true,
      source: 'NASA POWER Daily API, community AG', fetched: new Date().toISOString(),
      points: { farm: e.farm, upstream: e.upstream }, missing_values_filled: gaps, days,
    }, id);
    try { localStorage.setItem(cacheKey(id), JSON.stringify(season)); } catch { /* storage full or blocked */ }
    loaded.set(id, season);
    for (const fn of listeners) fn(id, season);
    return season;
  } catch {
    return null;
  } finally {
    clearTimeout(timer);
  }
}

/** Forget the live copy and go back to the committed file. */
export function clearLive(id) {
  try { localStorage.removeItem(cacheKey(id)); } catch { /* ignore */ }
  loaded.delete(id);
}

function readCache(id) {
  try {
    const s = JSON.parse(localStorage.getItem(cacheKey(id)) || 'null');
    return s && Array.isArray(s.days) && s.days.length > 30 ? normalise(s, id) : null;
  } catch { return null; }
}

function normalise(s, id) {
  if (!s || !Array.isArray(s.days) || !s.days.length) throw new Error(`Season '${id}' has no days`);
  if (!s.points?.farm || !s.points?.upstream) throw new Error(`Season '${id}' is missing farm/upstream points`);
  const e = entry(id);
  return { ...s, id, hazard: s.hazard || e?.hazard || 'flood', region: s.region || e?.region };
}

/** 'live' (fetched in this browser), 'nasa' (real file in the repo) or 'sample'. */
export const provenance = (s) => (s.sample ? 'sample' : s.live ? 'live' : 'nasa');
