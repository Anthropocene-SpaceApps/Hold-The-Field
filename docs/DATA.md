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

Season: 1 Dec 2016 – 30 Apr 2017 (boro rice; the start leaves room to choose the transplanting date). Missing values (POWER fill value −999) are replaced and counted in `missing_values_filled`.

### Where the files live

- `data/haor-2017.json` is the single source of truth in the repo. The Java build bundles it into the jar.
- A file downloaded by the launcher is written to `~/.hold-the-field/data/` and **takes priority** over the bundled one.
- `"sample": true` marks synthetic placeholder data. The bundled file is currently synthetic (including the December 2016 days added for the planting-date choice) and the game labels it everywhere until it is replaced.

## The model (simplified on purpose)

All numbers are in `game/src/main/java/org/anthropocene/htf/sim/Config.java`. The browser prototype has its own copy in `src/sim/config.js` (older: fixed planting date, yield = maturity).

1. **Water level** (metres above the field), each day:
   `level = max(0, level × drain + a × rainUp[t − lagDays] + b × rainFarm[t] − baseLoss)`
2. **Flood** when `level > bund`. The embankment starts at 0.6 m and can be raised twice by 0.15 m. A crop under water for 4 days is lost.
3. **Scout warning** from the 3-day upstream rain total: *Watch* ≥ 120 mm, *Flood warning* ≥ 250 mm.
4. **Crop** matures linearly from the **player-chosen transplanting date** over `fieldDays` (90 days short-duration, 115 days long-duration). Harvest is allowed from 80% maturity.
5. **Yield** = maturity at harvest × the variety's `potential` (short 85%, long 100%). These potentials are game parameters, not agronomic advice; Yaminur should replace them with sourced figures.
6. **Planting window analysis** (the debrief): `Engine.planningWindow` replays every variety × transplanting date on the same weather, once with no action and no warning, once acting on the scout.

### Calibration (still to do)

`a`, `b`, `drain`, `baseLoss`, `lagDays` and the bund heights should be tuned so that the **real 2017 rainfall floods the field near the documented flood date** (source: _add from research sheet_). This needs the real data file first. The prototype's `node scripts/calibrate.mjs data/haor-2017.json --target <date>` prints the `a` / `drain` pair that floods an unprotected crop on that date; paste the winner into `Config.java` (and keep the web copy in sync if it is still used).

The Java test `bundledSeasonFileLoads` checks the story (Rahim loses the crop; acting on the scout saves some) automatically once `data/haor-2017.json` has `"sample": false`.

### Known simplifications

- One bucket for the whole field; no river routing, topography or embankment failure.
- Upstream rain from a single point stands in for the whole catchment.
- Linear crop growth; no fertiliser, pests or temperature effects.
- The 3D world is a stylised haor; its geometry is not surveyed terrain.

## Sources for crop and flood facts

_Yaminur: add every source here (BRRI variety durations and yield, flood date, submergence tolerance, embankment heights)._
