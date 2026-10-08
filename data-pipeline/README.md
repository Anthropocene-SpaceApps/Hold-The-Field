# Data pipeline

Turns NASA POWER daily data into the JSON files in `/data` that the game loads.
No API calls happen in the browser; the game works offline.

```bash
python3 data-pipeline/fetch_power.py --region haor --start 20170101 --end 20170430 --out data/haor-2017.json
```

- Needs only Python 3.8+ (standard library) and internet access to `power.larc.nasa.gov`.
- Regions and coordinates live in `regions.json`.
- After fetching, open the game: the red SAMPLE badge disappears when real data is loaded.
- Commit the generated JSON so the deployed site uses it.
