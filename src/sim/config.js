// Every tunable number in the simulation lives here.
// Values marked CALIBRATE must be tuned against real NASA POWER data
// (see docs/DATA.md). Values marked CONFIRM need a source from the research sheet.

export const CONFIG = {
  // --- Crop ---------------------------------------------------------------
  transplantDate: '2017-01-05',          // CONFIRM: typical boro transplanting in the haor
  varieties: {
    long:  { label: 'Long-duration boro',  fieldDays: 115 }, // CONFIRM (~150 days incl. seedbed)
    short: { label: 'Short-duration boro', fieldDays: 90 },  // CONFIRM (~125 days incl. seedbed)
  },
  minHarvestMaturity: 0.8,               // can harvest from 80% maturity; yield = maturity
  daysUnderwaterToKill: 4,               // CONFIRM: days a ripening crop survives fully submerged

  // --- Water (simple bucket model) -------------------------------------------
  // level_t = max(0, level_{t-1} * drain + a * rainUp[t - lagDays] + b * rainFarm[t] - baseLoss)
  // level is metres of water above the field.
  drain: 0.8,                            // CALIBRATE
  a: 0.0011,                             // CALIBRATE: metres per mm of upstream rain
  b: 0.0006,                             // CALIBRATE: metres per mm of rain on the farm
  baseLoss: 0.02,                        // CALIBRATE: metres lost per day to drainage/evaporation
  lagDays: 2,                            // CALIBRATE: days for hill rain to reach the haor

  // --- Defences --------------------------------------------------------------
  bundStart: 0.6,                        // metres
  bundRaise: 0.15,                       // metres per raise (CALIBRATE: max bund should NOT beat the real flood alone)
  bundRaiseCost: 40,                     // coins
  maxBundRaises: 2,
  startCoins: 100,

  // --- Satellite scout ---------------------------------------------------------
  // 3-day sum of upstream rainfall (mm)
  watchMm: 120,                          // CALIBRATE
  warningMm: 250,                        // CALIBRATE
};
