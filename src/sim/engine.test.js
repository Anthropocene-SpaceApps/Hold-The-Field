// Same cases as game/src/test/java/.../EngineTest.java and DroughtTest.java, so the web and desktop builds agree.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { FLOOD as CFG, DROUGHT, configForSeason } from './config.js';
import {
  createState, step, act, autoplay, scoutStatus, maturityOn, summarize, planningWindow,
} from './engine.js';

// Small hand-made dataset so tests never depend on the real JSON.
function makeData(rainUpByDay = {}, n = 120) {
  const days = [];
  for (let d = 0; d < n; d++) {
    const date = new Date(Date.UTC(2017, 0, 1) + d * 86400000).toISOString().slice(0, 10);
    days.push({ date, rainUp: rainUpByDay[d] ?? 0, rainFarm: 0, tmax: 30, soil: 0.6 });
  }
  return { days, points: { farm: { lat: 0, lon: 0 }, upstream: { lat: 0, lon: 0 } } };
}
const storm = (from, to, mm) => Object.fromEntries(Array.from({ length: to - from + 1 }, (_, k) => [from + k, mm]));
const firstOf = (s, type) => s.events.find((e) => e.type === type);
const load = (id) => JSON.parse(readFileSync(new URL(`../../data/${id}.json`, import.meta.url), 'utf8'));

// ---------------------------------------------------------------- flood (EngineTest.java)

test('dry season: crop reaches a full harvest, no flood', () => {
  const f = autoplay(makeData(), CFG, 'rahim', 'short');
  assert.equal(f.alive, true);
  assert.equal(f.yieldPct, CFG.varieties.short.potential, "a full harvest yields the variety's potential");
  assert.ok(!f.events.some((e) => e.type === 'flood'));
});

test('a big upstream storm kills an unprotected crop', () => {
  const f = autoplay(makeData(storm(80, 88, 220)), CFG, 'rahim', 'long');
  assert.ok(f.events.some((e) => e.type === 'flood'));
  assert.equal(f.alive, false);
  assert.equal(f.yieldPct, 0);
});

test('the scout warning fires before the flood', () => {
  const f = autoplay(makeData(storm(80, 88, 220)), CFG, 'rahim', 'long');
  assert.ok(firstOf(f, 'warning').i < firstOf(f, 'flood').i);
});

test('scout status thresholds', () => {
  const d = makeData({ 10: 40, 11: 40, 12: 40, 20: 60, 21: 60, 22: 60 }).days;
  assert.equal(scoutStatus(d, 5, CFG), 'calm');
  assert.equal(scoutStatus(d, 12, CFG), 'watch');
  assert.equal(scoutStatus(d, 22, CFG), 'warning');
});

test('cannot harvest before minimum maturity', () => {
  const data = makeData();
  const s = step(createState(data, CFG, 'long'), data, CFG);
  assert.equal(act(s, 'harvest', CFG).ok, false);
});

test('raising the bund costs coins and is capped', () => {
  let s = createState(makeData(), CFG, 'long');
  for (let k = 0; k < CFG.maxBundRaises; k++) s = act(s, 'raiseBund', CFG).state;
  assert.equal(s.coins, CFG.startCoins - CFG.maxBundRaises * CFG.bundRaiseCost);
  assert.equal(act(s, 'raiseBund', CFG).ok, false);
});

test('the simulation is deterministic', () => {
  const data = makeData(storm(70, 75, 150));
  const a = autoplay(data, CFG, 'scout'), b = autoplay(data, CFG, 'scout');
  assert.deepEqual(a.events, b.events);
  assert.equal(a.yieldPct, b.yieldPct);
  assert.deepEqual(a.history, b.history);
});

test('maturity grows linearly from the transplant date', () => {
  assert.equal(maturityOn(CFG.defaultTransplant, 'long', CFG.defaultTransplant, CFG), 0);
  assert.equal(maturityOn('2030-01-01', 'long', CFG.defaultTransplant, CFG), 1);
});

test('history has one point per day and tracks bund raises', () => {
  const data = makeData();
  let s = step(createState(data, CFG, 'long'), data, CFG);
  s = act(s, 'raiseBund', CFG).state;
  assert.equal(s.history.length, 2);
  assert.ok(Math.abs(s.history[1].bund - (CFG.bundStart + CFG.bundRaise)) < 1e-9);
});

test('summarize reports the scout lead time', () => {
  const sum = summarize(autoplay(makeData(storm(80, 88, 220)), CFG, 'rahim', 'long'));
  assert.ok(sum.leadDays > 0);
  assert.equal(sum.lost, true);
  assert.equal(summarize(autoplay(makeData(), CFG, 'rahim', 'short')).leadDays, null);
});

test('acting on the scout beats ignoring it in the same storm', () => {
  const data = makeData(storm(80, 88, 220));
  assert.ok(autoplay(data, CFG, 'scout').yieldPct > autoplay(data, CFG, 'rahim').yieldPct);
});

test('short variety yields less but ripens sooner', () => {
  const data = makeData();
  const shortRice = autoplay(data, CFG, 'rahim', 'short'), longRice = autoplay(data, CFG, 'rahim', 'long');
  assert.equal(shortRice.yieldPct, CFG.varieties.short.potential);
  assert.equal(longRice.yieldPct, CFG.varieties.long.potential);
  assert.ok(firstOf(shortRice, 'harvest').i < firstOf(longRice, 'harvest').i);
});

test('the transplant date moves the whole schedule', () => {
  const data = makeData();
  const early = createState(data, CFG, 'long', '2017-01-02');
  const late = createState(data, CFG, 'long', '2017-01-20');
  assert.equal(early.transplant, '2017-01-02');
  assert.equal(early.startIndex, 1);
  assert.equal(late.startIndex, 19);
  assert.equal(late.maturity, 0, 'nothing has grown on the transplant day');
  assert.ok(step(late, data, CFG).maturity > 0);
  const e = autoplay(data, CFG, 'rahim', 'short', '2017-01-02'), l = autoplay(data, CFG, 'rahim', 'short', '2017-01-20');
  assert.ok(firstOf(e, 'harvest').i < firstOf(l, 'harvest').i);
});

test('planning window rewards early short rice in a late flood; the scout never does worse', () => {
  const data = makeData(storm(95, 108, 230));
  const noWarning = planningWindow(data, CFG, 'rahim');
  assert.ok(noWarning.length >= 2);
  const shorts = noWarning.filter((p) => p.variety === 'short').sort((a, b) => a.transplant.localeCompare(b.transplant));
  const longs = noWarning.filter((p) => p.variety === 'long').sort((a, b) => a.transplant.localeCompare(b.transplant));
  assert.ok(shorts[0].yieldPct >= longs.at(-1).yieldPct);
  const scout = planningWindow(data, CFG, 'scout');
  noWarning.forEach((p, i) => assert.ok(scout[i].yieldPct >= p.yieldPct - 1e-9, 'acting on the scout never does worse'));
});

test('history starts at the transplant day', () => {
  const data = makeData();
  const s = step(createState(data, CFG, 'long', '2017-01-10'), data, CFG);
  assert.equal(s.history.length, 2);
  assert.equal(s.i, s.startIndex + 1);
});

test('the bundled haor season loads and plays', () => {
  const s = load('haor-2017');
  assert.ok(s.days.length >= 100);
  assert.ok(s.points.farm && s.points.upstream);
  assert.equal(configForSeason(s), CFG);
  if (!s.sample) {
    assert.equal(autoplay(s, CFG, 'rahim', 'long').alive, false, 'Rahim loses the crop in the real 2017 flood');
    assert.ok(autoplay(s, CFG, 'scout').yieldPct > 0, 'the scout-assisted player saves some harvest');
  }
});

// ---------------------------------------------------------------- drought (DroughtTest.java)

const barind = () => load('barind-2022');

test('the bundled Barind season is a drought scenario with one point', () => {
  const s = barind();
  assert.equal(configForSeason(s), DROUGHT);
  assert.deepEqual(s.points.farm, s.points.upstream, 'drought is driven locally: one point');
});

test('drought: the scout warns before the crop feels the dry soil', () => {
  const sum = summarize(autoplay(barind(), DROUGHT, 'rahim', 'long', '2022-07-25'));
  assert.ok(sum.warningDate, 'a warning is issued');
  assert.ok(sum.floodDate, 'the crop eventually feels the dry soil');
  assert.ok(sum.leadDays >= 3, `lead time was ${sum.leadDays}`);
});

test('drought: acting on the scout saves most of the crop', () => {
  const rahim = autoplay(barind(), DROUGHT, 'rahim', 'long', '2022-07-25');
  const scout = autoplay(barind(), DROUGHT, 'scout', 'long', '2022-07-25');
  assert.ok(scout.yieldPct > rahim.yieldPct + 0.2, `scout ${scout.yieldPct} vs rahim ${rahim.yieldPct}`);
  assert.ok(scout.irrigations > 0);
  assert.ok(scout.tank >= 0 && scout.coins >= 0);
});

test('drought: tolerant rice is safer without a warning', () => {
  const tolerant = autoplay(barind(), DROUGHT, 'rahim', 'short', '2022-07-25').yieldPct;
  const standard = autoplay(barind(), DROUGHT, 'rahim', 'long', '2022-07-25').yieldPct;
  assert.ok(tolerant > standard, `tolerant ${tolerant} vs standard ${standard}`);
});

test('drought: the planning window covers every variety and date', () => {
  const plans = planningWindow(barind(), DROUGHT, 'scout');
  assert.equal(plans.length, 6);
  for (const p of plans) assert.ok(p.yieldPct >= 0 && p.yieldPct <= 1);
});

test('irrigation needs water and money', () => {
  let s = createState(barind(), DROUGHT, 'long', '2022-07-25');
  let done = 0, r;
  while ((r = act(s, 'irrigate', DROUGHT)).ok) { s = r.state; done++; assert.ok(done < 20); }
  assert.equal(done, DROUGHT.tankStart, 'the tank limits irrigation');
  assert.equal(r.reason, 'The water tank is empty');
  assert.equal(s.coins, DROUGHT.startCoins - done * DROUGHT.irrigationCost);
});

test('each hazard keeps its own actions', () => {
  assert.equal(act(createState(barind(), DROUGHT, 'long'), 'raiseBund', DROUGHT).ok, false);
  assert.equal(act(createState(load('haor-2017'), CFG, 'long'), 'irrigate', CFG).ok, false);
});

test('drought: the scout does not repeat the same alert every other day', () => {
  const s = autoplay(barind(), DROUGHT, 'rahim', 'long', '2022-07-25');
  const warnings = s.events.filter((e) => e.type === 'warning');
  assert.ok(warnings.length <= 6);
  for (let i = 1; i < s.events.length; i++) {
    const p = s.events[i - 1], q = s.events[i];
    if (p.type === 'warning' && q.type === 'warning') assert.ok(q.i - p.i > 12);
  }
});
