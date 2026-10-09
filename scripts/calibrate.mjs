// Calibration report for a flood season file.
//   node scripts/calibrate.mjs [data/haor-2017.json] [--target 2017-04-01]
// Prints how the current numbers play on the data. With --target, also searches (a, drain) so that the farmer
// with no warning loses the crop near the documented flood date while acting on the scout still saves some.
// Paste the winner into BOTH src/sim/config.js (floodConfig defaults) and game/.../sim/Config.java (DEFAULT).
import { readFileSync } from 'node:fs';
import { FLOOD, floodConfig } from '../src/sim/config.js';
import { autoplay, dayNumber } from '../src/sim/engine.js';

const args = process.argv.slice(2);
const file = args.find((a) => a.endsWith('.json')) ?? 'data/haor-2017.json';
const target = args.includes('--target') ? args[args.indexOf('--target') + 1] : null;
const data = JSON.parse(readFileSync(file, 'utf8'));
if (data.sample) console.warn('WARNING: this is SAMPLE data. Calibrating on it is meaningless.\n');

function report(cfg) {
  const out = {};
  for (const [name, policy, variety] of [['rahim', 'rahim', 'long'], ['scout', 'scout', 'short']]) {
    const s = autoplay(data, cfg, policy, variety);
    out[name] = {
      yield: +s.yieldPct.toFixed(2),
      flood: s.events.find((e) => e.type === 'flood')?.date ?? null,
      warning: s.events.find((e) => e.type === 'warning')?.date ?? null,
    };
  }
  return out;
}

const peak = data.days.reduce((m, d) => (d.rainUp > m.rainUp ? d : m), data.days[0]);
console.log(`Wettest upstream day: ${peak.date} (${peak.rainUp} mm). Scout thresholds: watch ${FLOOD.watchMm} mm, warning ${FLOOD.warningMm} mm over 3 days.`);
console.log(`Current numbers (a=${FLOOD.a}, drain=${FLOOD.drain}):`, JSON.stringify(report(FLOOD)));
if (target) {
  let best = null;
  for (let a = 0.0004; a <= 0.004; a += 0.0001) for (let drain = 0.6; drain <= 0.92; drain += 0.02) {
    const r = report(floodConfig(+a.toFixed(4), +drain.toFixed(2)));
    if (!r.rahim.flood || r.rahim.yield !== 0 || r.scout.yield <= 0) continue;
    const err = Math.abs(dayNumber(r.rahim.flood) - dayNumber(target));
    if (!best || err < best.err) best = { err, a: +a.toFixed(4), drain: +drain.toFixed(2), r };
  }
  console.log(best ? `Best fit (flood ${best.err} day(s) from ${target}):\n${JSON.stringify(best, null, 1)}` : 'No (a, drain) pair floods and kills the unprotected crop while the scout saves some. Check the scout thresholds or the date range.');
}
