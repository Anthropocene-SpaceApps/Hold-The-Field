import { test } from 'node:test';
import assert from 'node:assert/strict';
import { CONFIG } from './config.js';
import { createState, step, act, autoplay, scoutStatus, maturityOn } from './engine.js';

// Small hand-made dataset so tests never depend on the real JSON.
function makeData(rainUpByDay, n = 120) {
  const days = [];
  for (let d = 0; d < n; d++) {
    const date = new Date(Date.UTC(2017, 0, 1) + d * 86400000).toISOString().slice(0, 10);
    days.push({ date, rainUp: rainUpByDay[d] ?? 0, rainFarm: 0, tmax: 30, soil: 0.6 });
  }
  return { days };
}
const storm = (from, to, mm) => Object.fromEntries(Array.from({ length: to - from + 1 }, (_, k) => [from + k, mm]));

test('dry season: crop reaches full harvest, no flood', () => {
  const f = autoplay(makeData({}), CONFIG, 'rahim', 'short');
  assert.equal(f.alive, true);
  assert.equal(f.yieldPct, 1);
  assert.ok(!f.events.some((e) => e.type === 'flood'));
});

test('big upstream storm floods the field and kills an unprotected crop', () => {
  const f = autoplay(makeData(storm(80, 88, 220)), CONFIG, 'rahim', 'long');
  assert.ok(f.events.some((e) => e.type === 'flood'));
  assert.equal(f.alive, false);
  assert.equal(f.yieldPct, 0);
});

test('scout warning fires before the flood', () => {
  const f = autoplay(makeData(storm(80, 88, 220)), CONFIG, 'rahim', 'long');
  const warn = f.events.find((e) => e.type === 'warning');
  const flood = f.events.find((e) => e.type === 'flood');
  assert.ok(warn && flood);
  assert.ok(warn.i < flood.i, `warning day ${warn.i} should be before flood day ${flood.i}`);
});

test('scout status thresholds', () => {
  const d = makeData({ 10: 50, 11: 50, 12: 50, 20: 100, 21: 100, 22: 100 }).days;
  assert.equal(scoutStatus(d, 5, CONFIG), 'calm');
  assert.equal(scoutStatus(d, 12, CONFIG), 'watch');
  assert.equal(scoutStatus(d, 22, CONFIG), 'warning');
});

test('cannot harvest before minimum maturity', () => {
  const data = makeData({});
  let s = createState(data, CONFIG, 'long');
  s = step(s, data, CONFIG);
  const r = act(s, { type: 'harvest' }, CONFIG);
  assert.equal(r.ok, false);
});

test('raising the bund costs coins and is capped', () => {
  const data = makeData({});
  let s = createState(data, CONFIG);
  for (let k = 0; k < CONFIG.maxBundRaises; k++) s = act(s, { type: 'raiseBund' }, CONFIG).state;
  assert.equal(s.coins, CONFIG.startCoins - CONFIG.maxBundRaises * CONFIG.bundRaiseCost);
  assert.equal(act(s, { type: 'raiseBund' }, CONFIG).ok, false);
});

test('simulation is deterministic', () => {
  const data = makeData(storm(70, 75, 150));
  assert.deepEqual(autoplay(data, CONFIG, 'scout'), autoplay(data, CONFIG, 'scout'));
});

test('maturity grows linearly from transplant date', () => {
  assert.equal(maturityOn(CONFIG.transplantDate, 'long', CONFIG), 0);
  assert.equal(maturityOn('2030-01-01', 'long', CONFIG), 1);
});
