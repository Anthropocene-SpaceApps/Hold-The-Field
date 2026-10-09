# Data and model

## Source

**NASA POWER Daily API**, point data, community `AG` (https://power.larc.nasa.gov).
Downloaded by the launcher (**NASA Data** tab, `game/.../launcher/DataUpdater.java`) or by `data-pipeline/fetch_power.py`,
and stored as one JSON file per season. The game never calls the API while you play.

| Game field | POWER parameter | Unit |
|---|---|---|
| `rainUp` | `PRECTOTCORR` at the upstream point | mm/day |
| `rainFarm` | `PRECTOTCORR` at the farm point | mm/day |
| `tmax` | `T2M_MAX` at the farm point | °C |
| `soil` | `GWETROOT` at the farm point | 0–1 |

| Point | Lat | Lon | Why |
|---|---|---|---|
| Farm, Sunamganj haor | 25.07 | 91.40 | The field in the story |
| Upstream, Cherrapunji / Sohra | 25.27 | 91.73 | Haor flash floods are driven by heavy rain in the Meghalaya hills |
| Farm, Barind Tract (Rajshahi division) | 24.60 | 88.50 | The field in the drought scenario. Drought is driven locally, so the upstream point is the same point |

Seasons:

| File | Dates | Scenario |
|---|---|---|
| `data/haor-2017.json` | 1 Dec 2016 – 30 Apr 2017 | Sunamganj haor, boro rice, flash flood (the start leaves room to choose the transplanting date) |
| `data/barind-2022.json` | 25 Jun – 15 Dec 2022 | Barind Tract, Aman rice, drought (the dates cover transplanting in July and August and harvest by December) |

Missing values (POWER fill value −999) are replaced and counted in `missing_values_filled`.

### Where the files live

- `data/*.json` is the single source of truth in the repo. The Java build bundles it into the jar.
- A file downloaded by the launcher is written to `~/.agrocene/data/` and **takes priority** over the bundled one.
- `"sample": true` marks synthetic placeholder data. Both bundled files are currently synthetic and the game labels them everywhere until they are replaced. `data/barind-2022.json` is written by `data-pipeline/make_sample_barind.py` (a normal monsoon, then a three-week dry spell from 20 September, when Aman rice is flowering). **It does not describe what happened in 2022**; once the real NASA data is downloaded, the story is whatever the real weather was, so check the debrief and adjust the date range if that season was not dry.

## The flood model (haor), simplified on purpose

All numbers are in `game/src/main/java/org/anthropocene/htf/sim/Config.java`. The web build uses an exact port in `src/sim/config.js` and `src/sim/engine.js`; both builds run the same test cases (`node --test` and `mvn test`) and give identical results, so change a number in both.

1. **Water level** (metres above the field), each day:
   `level = max(0, level × drain + a × rainUp[t − lagDays] + b × rainFarm[t] − baseLoss)`
2. **Flood** when `level > bund`. The embankment starts at 0.6 m and can be raised twice by 0.15 m. A crop under water for 4 days is lost.
3. **Scout warning** from the 3-day upstream rain total: *Watch* ≥ 120 mm, *Flood warning* ≥ 250 mm.
4. **Crop** matures linearly from the **player-chosen transplanting date** over `fieldDays` (90 days short-duration, 115 days long-duration). Harvest is allowed from 80% maturity.
5. **Yield** = maturity at harvest × the variety's `potential` (short 85%, long 100%). These potentials are game parameters, not agronomic advice; Yaminur should replace them with sourced figures.
6. **Planting window analysis** (the debrief): `Engine.planningWindow` replays every variety × transplanting date on the same weather, once with no action and no warning, once acting on the scout.

## The drought model (Barind), simplified on purpose

All numbers are in `Config.java` (`Config.DROUGHT`). The scenario is chosen by the season file (`"hazard": "drought"` or a region starting with `barind`).

1. **Soil moisture the crop feels** = NASA root-zone wetness (`GWETROOT`) + irrigation boost, clamped to 0–1. One irrigation adds 0.28, capped at 0.45 in total, and fades by 30% a day.
2. **Stress** on a day when that moisture is below the variety's stress line (0.50 standard, 0.44 drought-tolerant): `load += sensitivity × heat × deficit × 0.11`, where `deficit = (line − moisture) / line`, sensitivity is 1.0 early, 1.3 from 35% maturity, 2.0 from 55% to 90% (flowering and grain filling) and 0.3 after, and heat is 1.5 when the day is above 37 °C.
3. **Crop failure** when the load reaches 1. Otherwise the harvest is `maturity × variety potential × (1 − load)`.
4. **Scout warning** from the rain of the last 7 days and today's soil wetness: *Watch* under 20 mm and soil < 0.76, *Warning* under 8 mm and soil < 0.68. Alerts are not repeated within 12 days.
5. **Irrigation** costs Tk 12 and one load from the village tank (starts with 4, holds 6, refills by 0.05 per mm of rain).
6. **Crop** matures linearly from the chosen transplanting date: 120 days for standard Aman, 100 for drought-tolerant. Potentials: 100% and 88% (game parameters).
7. **Planting window analysis** is the same replay as in the flood scenario (`Engine.planningWindow`).

With the bundled sample data: no action loses 48% of the standard crop (52% kept) and 29% of the tolerant crop; irrigating on the scout's word keeps 91% of the standard crop. These numbers move with the real data. The tests in `DroughtTest` check the *shape* of the story (the scout warns at least 3 days ahead, acting on it beats not acting by more than 20 points, tolerant rice is safer without a warning), not the exact figures.

### Calibration (still to do)

`a`, `b`, `drain`, `baseLoss`, `lagDays` and the bund heights should be tuned so that the **real 2017 rainfall floods the field near the documented flood date** (source: _add from research sheet_). This needs the real data file first. `node scripts/calibrate.mjs data/haor-2017.json --target <date>` prints the `a` / `drain` pair that floods an unprotected crop on that date while the scout still saves some; paste the winner into `Config.java` and `src/sim/config.js`. The `nasa-data` GitHub Action runs this report for you after fetching.

For the drought scenario, calibrate the stress lines and `stressRate` the same way: with the real file, find when the soil first crossed the lines and make sure the warning leads it by several days.

The Java test `bundledSeasonFileLoads` checks the story (Rahim loses the crop; acting on the scout saves some) automatically once `data/haor-2017.json` has `"sample": false`.

### Known simplifications

- One bucket for the whole field; no river routing, topography or embankment failure.
- Upstream rain from a single point stands in for the whole catchment.
- Linear crop growth; no fertiliser, pests or temperature effects.
- The 3D world is a stylised haor; its geometry is not surveyed terrain. In the Barind scenario the same scene is flattened and dried (red soil, dust haze, baked mud, wilting rice), which is a stand-in for the high, dry Barind plain.
- Drought: no groundwater, canals, pumps or fuel costs; one soil layer; the stress line is a single number per variety.

## Sources for crop and flood facts

_Yaminur: add every source here (BRRI variety durations and yield, flood date, submergence tolerance, embankment heights)._
