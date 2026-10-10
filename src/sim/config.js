// Every tunable number in the simulation, for both scenarios: the haor flash flood (FLOOD) and the
// Barind drought (DROUGHT). This is a 1:1 port of game/src/main/java/org/anthropocene/htf/sim/Config.java:
// change a number in one, change it in the other (tests in both check the same behaviour).

/** potential = share of the full yield this variety can reach (game parameter, not agronomic advice).
 *  stressSoil = root-zone wetness below which the crop is stressed (drought only; lower = more tolerant). */
const variety = (label, fieldDays, potential, note, stressSoil = 0) => ({ label, fieldDays, potential, note, stressSoil });

const COMMON = {
  minHarvestMaturity: 0.8,
  daysUnderwaterToKill: 4,

  // Water bucket (flood): level = max(0, level*drain + a*rainUp[t-lag] + b*rainFarm[t] - baseLoss), metres
  b: 0.0006,
  baseLoss: 0.02,
  lagDays: 2,

  // Defences (flood)
  bundStart: 0.6,
  bundRaise: 0.15,
  bundRaiseCost: 40,
  maxBundRaises: 2,
  startCoins: 100,

  // Satellite scout (flood): 3-day upstream rain sum (mm). Set on the real NASA POWER 2017 season: WATCH for the
  // late-February storm that did not flood, and for 30 Mar; FLOOD WARNING on 31 Mar, the day before the water came.
  watchMm: 100,
  warningMm: 150,

  // Drought: NASA root-zone wetness plus irrigation; stress accumulates on dry days
  irrigationCost: 12,
  irrigationBoost: 0.28,   // wetness added by one irrigation, fading each day
  boostDecay: 0.70,
  boostMax: 0.45,
  tankStart: 4, tankMax: 6, tankPerMm: 0.05,
  heatC: 37,               // hotter days add stress
  stressRate: 0.11,        // crop-stress load per day of full deficit at normal sensitivity
  // scout (drought): rain over the last 7 days and the NASA soil wetness today
  dryWatchMm: 20, dryWarnMm: 8, soilWatch: 0.76, soilWarn: 0.68,
};

export function floodConfig(a = 0.0038, drain = 0.9) {
  return Object.freeze({
    ...COMMON,
    hazard: 'flood',
    a, drain,
    defaultTransplant: '2017-01-05',
    varieties: {
      long: variety('Long-duration boro', 115, 1.00, 'Highest yield, ripens late'),
      short: variety('Short-duration boro', 90, 0.85, 'Lower yield, ripens early'),
    },
    transplantOptions: [['2016-12-20', 'Early (20 Dec)'], ['2017-01-05', 'Usual (5 Jan)'], ['2017-01-25', 'Late (25 Jan)']],
  });
}

export const FLOOD = floodConfig();

export const DROUGHT = Object.freeze({
  ...COMMON,
  hazard: 'drought',
  a: 0, drain: 0,
  defaultTransplant: '2022-07-25',
  varieties: {
    long: variety('Standard Aman', 120, 1.00, 'Highest yield, needs steady water', 0.50),
    short: variety('Drought-tolerant Aman', 100, 0.88, 'Lower yield, copes with dry soil', 0.44),
  },
  transplantOptions: [['2022-07-10', 'Early (10 Jul)'], ['2022-07-25', 'Usual (25 Jul)'], ['2022-08-10', 'Late (10 Aug)']],
});

export const isDrought = (cfg) => cfg.hazard === 'drought';

/** The scenario that goes with a season file. */
export function configForSeason(season) {
  const dry = season && ((season.hazard || '').toLowerCase() === 'drought' || (season.region || '').toLowerCase().startsWith('barind'));
  return dry ? DROUGHT : FLOOD;
}
