// Calibration report for a season file. Usage: node scripts/calibrate.mjs [data/haor-2017.json] [--target 2017-04-06]
// Prints how the current CONFIG plays on the data. With --target, also searches (a, drain)
// so the unprotected flood lands on the documented flood date. Paste the winner into src/sim/config.js.
import { readFileSync } from 'node:fs';
import { CONFIG } from '../src/sim/config.js';
import { autoplay } from '../src/sim/engine.js';

const args = process.argv.slice(2);
const file = args.find((a) => a.endsWith('.json')) ?? 'data/haor-2017.json';
const target = args.includes('--target') ? args[args.indexOf('--target') + 1] : null;
const data = JSON.parse(readFileSync(file, 'utf8'));
if (data.sample) console.warn('WARNING: this is SAMPLE data. Calibrating on it is meaningless.\n');

function report(cfg) {
  const out = {};
  for (const [name, policy, variety] of [['rahim', 'rahim', 'long'], ['scout', 'scout', 'short']]) {
    const s = autoplay(data, cfg, policy, variety);
    out[name] = { yield: +s.yieldPct.toFixed(2), flood: s.events.find((e) => e.type === 'flood')?.date ?? null,
      warning: s.events.find((e) => e.type === 'warning')?.date ?? null };
  }
  return out;
}

console.log('Current CONFIG:', JSON.stringify(report(CONFIG)));
if (target) {
  const day = (iso) => Date.parse(iso) / 86400000;
  let best = null;
  for (let a = 0.0004; a <= 0.003; a += 0.0001) for (let drain = 0.6; drain <= 0.9; drain += 0.05) {
    const cfg = { ...CONFIG, a, drain };
    const r = report(cfg);
    if (!r.rahim.flood || r.rahim.yield !== 0) continue;
    const err = Math.abs(day(r.rahim.flood) - day(target));
    if (!best || err < best.err) best = { err, a: +a.toFixed(4), drain: +drain.toFixed(2), r };
  }
  console.log(best ? `Best fit (flood ${best.err} day(s) from ${target}):\n${JSON.stringify(best, null, 1)}` : 'No (a, drain) pair floods and kills the unprotected crop.');
}
