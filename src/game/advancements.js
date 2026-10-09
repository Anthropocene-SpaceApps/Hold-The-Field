// Advancements, kept in this browser (port of game/Advancements.java).
import { summarize } from '../sim/engine.js';
import { BRAND } from '../brand.js';

export const ALL = [
  { id: 'eyes', title: 'Eyes in the Sky', description: 'See your first Satellite Scout warning' },
  { id: 'wall', title: 'Raise the Wall', description: 'Raise the bund with mud bricks' },
  { id: 'ahead', title: 'Ahead of the Water', description: 'Bring in the harvest before the flood arrives' },
  { id: 'dry', title: 'Dry Feet', description: 'Survive a flash flood with your crop standing' },
  { id: 'revenge', title: "Better Than Rahim's Way", description: 'Save more rice than the farmer who gets no warning' },
  { id: 'lesson', title: 'A Hard Lesson', description: 'Lose a crop to the flood or the drought' },
  { id: 'thirst', title: 'Quench the Field', description: 'Irrigate your rice from the village tank' },
  { id: 'drops', title: 'Every Drop Counts', description: 'Bring the rice through a dry spell and keep most of the harvest' },
  { id: 'full', title: 'Golden Season', description: 'Harvest every last grain' },
];

const KEY = `${BRAND.storageKey}-advancements`;
let unlocked = new Set();
try { unlocked = new Set(JSON.parse(localStorage.getItem(KEY) || '[]')); } catch { /* storage unavailable */ }

export const has = (id) => unlocked.has(id);
export const count = () => unlocked.size;

function grant(id) {
  if (unlocked.has(id)) return null;
  unlocked.add(id);
  try { localStorage.setItem(KEY, JSON.stringify([...unlocked])); } catch { /* ignore */ }
  return ALL.find((a) => a.id === id) ?? null;
}

/** Advancements earned at the moment of a sim event. */
export function onEvent(type, s, drought) {
  const got = [];
  const add = (a) => a && got.push(a);
  if (type === 'warning') add(grant('eyes'));
  else if (type === 'action') add(grant(drought ? 'thirst' : 'wall'));
  else if (type === 'loss') add(grant('lesson'));
  else if (type === 'harvest') {
    const floodedYet = s.events.some((e) => e.type === 'flood');
    const warned = s.events.some((e) => e.type === 'warning');
    if (!drought && !floodedYet && warned && s.yieldPct > 0) add(grant('ahead'));
    if (s.yieldPct >= 0.999) add(grant('full'));
  }
  return got;
}

/** Advancements decided only once the run is over. */
export function onEnd(s, baseline, scoutMode, drought) {
  const got = [];
  const add = (a) => a && got.push(a);
  const sum = summarize(s);
  if (drought) { if (s.alive && s.stressDays > 0 && s.yieldPct >= 0.6) add(grant('drops')); }
  else if (s.alive && sum.floodDate) add(grant('dry'));
  if (scoutMode && s.yieldPct > baseline.yieldPct) add(grant('revenge'));
  return got;
}
