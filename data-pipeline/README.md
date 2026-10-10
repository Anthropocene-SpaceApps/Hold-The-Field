# Data pipeline

Turns NASA POWER daily data into the season JSON files the game loads.
The game never calls the API while you play; it works offline.

Two ways to fetch, with identical output:

- **In the game's launcher** (easiest): open **NASA Data** and press **Update NASA data**. The files go to `~/.hold-the-field/data/` and take priority over the bundled sample.
- **From the command line** (this folder), to refresh the file that ships in the repo:

```bash
python3 data-pipeline/fetch_power.py --region haor --start 20161201 --end 20170430 --out data/haor-2017.json
```

- Needs only Python 3.8+ (standard library) and internet access to `power.larc.nasa.gov`.
- Regions and coordinates live in `regions.json` (the launcher bundles a copy in `game/src/main/resources/`; keep them in step).
- After fetching, the SAMPLE label in the game disappears (`"sample": false`).
- Commit the generated `data/haor-2017.json` so everyone, and CI, uses the real season.

The drought scenario's file works the same way:

```bash
python3 data-pipeline/fetch_power.py --region barind --start 20100625 --end 20101215 --out data/barind-2010.json
```

`data/barind-2010.json` is real NASA POWER data for the calibrated historical scenario. The 2010 window was selected
after scanning 2005-2025: it had the lowest growing-window soil index and the clearest dry-season signal. Keep the
scenario file checked in so the game remains deterministic and works offline.
