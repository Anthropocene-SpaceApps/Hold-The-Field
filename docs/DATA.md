# Data and model

## Source

**NASA POWER Daily API**, point data, community `AG` (https://power.larc.nasa.gov).
Fetched by `data-pipeline/fetch_power.py` and stored in `data/*.json`. The game never calls the API live.

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

## The model (simplified on purpose)

All numbers are in `src/sim/config.js`.

1. **Water level** (metres above the field), each day:
   `level = max(0, level × drain + a × rainUp[t − lagDays] + b × rainFarm[t] − baseLoss)`
2. **Flood** when `level > bund`. A crop under water for `daysUnderwaterToKill` days is lost.
3. **Scout warning** from the 3-day upstream rain total: *Watch* ≥ `watchMm`, *Flood warning* ≥ `warningMm`.
4. **Crop** matures linearly from the transplant date over `fieldDays` (long vs short-duration variety). Harvest allowed from 80% maturity; yield = maturity.

### Calibration

`a`, `b`, `drain`, `baseLoss`, `lagDays` and the bund heights are tuned so that the **real 2017 rainfall floods the field near the documented flood date** (source: _add from research sheet_). This is a learning game calibrated to one real event, not a forecast model.

To calibrate: run `node scripts/calibrate.mjs data/haor-2017.json --target <documented flood date>`. It prints how the current config plays and the `a` / `drain` pair that floods an unprotected crop on that date. Paste the winner into `src/sim/config.js`. The test `the real season file keeps the game story intact` only runs once `data/haor-2017.json` is real (`"sample": false`).

### Known simplifications

- One bucket for the whole field; no river routing, topography or embankment failure.
- Upstream rain from a single point stands in for the whole catchment.
- Linear crop growth; no fertiliser, pests or temperature effects (yet).

## Sources for crop and flood facts

_Yaminur: add every source here (BRRI variety durations, flood date, submergence tolerance)._
