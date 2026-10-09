#!/usr/bin/env python3
"""Write the SYNTHETIC placeholder season for the Barind drought scenario (data/barind-2022.json).

It is not measured data. It exists so the game always starts; it is labelled SAMPLE everywhere. The shape is a normal
monsoon followed by a three-week dry spell from 20 September, when Aman rice is flowering. Replace it with the real
season: launcher -> NASA Data -> Update NASA data, or

    python3 data-pipeline/fetch_power.py --region barind --start 20220625 --end 20221215 --out data/barind-2022.json
"""
import json, random
from datetime import date, timedelta
from pathlib import Path

rng = random.Random(2022)
start, end = date(2022, 6, 25), date(2022, 12, 15)


def rain_for(d):
    if date(2022, 9, 20) <= d <= date(2022, 10, 12):
        if d == date(2022, 10, 2):
            return 9.0
        return round(rng.expovariate(1 / 2.0), 1) if rng.random() < 0.06 else 0.0
    if d < date(2022, 9, 1):
        p, mean = 0.55, 14.0
    elif d < date(2022, 9, 20):
        p, mean = 0.35, 9.0
    elif d < date(2022, 11, 6):
        p, mean = 0.30, 10.0
    else:
        p, mean = 0.06, 5.0
    if rng.random() >= p:
        return 0.0
    r = rng.expovariate(1 / mean)
    if rng.random() < 0.06:
        r += rng.uniform(25, 50)
    return round(min(r, 95.0), 1)


def tmax_for(d, rain):
    if d < date(2022, 9, 1):
        base = 33.0
    elif d < date(2022, 9, 20):
        base = 34.0
    elif d <= date(2022, 10, 12):
        base = 36.8
    elif d < date(2022, 11, 6):
        base = 32.0
    elif d < date(2022, 12, 1):
        base = 28.5
    else:
        base = 25.5
    return round(base + rng.uniform(-1.2, 1.4) - min(2.0, rain / 20.0), 1)


days, soil, d = [], 0.62, start
while d <= end:
    r = rain_for(d)
    soil = min(0.93, soil * 0.96 + 0.0125 * r + 0.002)
    days.append({"date": d.isoformat(), "rainUp": r, "rainFarm": r, "tmax": tmax_for(d, r), "soil": round(soil, 3)})
    d += timedelta(days=1)

out = {
    "region": "barind",
    "hazard": "drought",
    "season": "20220625-20221215",
    "sample": True,
    "note": "SYNTHETIC PLACEHOLDER. Not real data. Replace by running the launcher's Update NASA data or data-pipeline/fetch_power.py.",
    "source": "NASA POWER Daily API, community AG (placeholder until fetched)",
    "points": {"farm": {"lat": 24.60, "lon": 88.50}, "upstream": {"lat": 24.60, "lon": 88.50}},
    "units": {"rainUp": "mm/day", "rainFarm": "mm/day", "tmax": "degC", "soil": "0-1 (GWETROOT)"},
    "days": days,
}
Path(__file__).resolve().parents[1].joinpath("data", "barind-2022.json").write_text(json.dumps(out, indent=1))
print(f"Wrote data/barind-2022.json: {len(days)} days")
