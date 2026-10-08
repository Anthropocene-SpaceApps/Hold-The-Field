#!/usr/bin/env python3
"""Fetch NASA POWER daily point data and write a season file the game can load.

Standard library only. Example:
    python3 data-pipeline/fetch_power.py --region haor --start 20170101 --end 20170430 --out data/haor-2017.json

Source: NASA POWER Daily API (https://power.larc.nasa.gov), community AG.
"""
import argparse, json, sys, urllib.parse, urllib.request
from pathlib import Path

API = "https://power.larc.nasa.gov/api/temporal/daily/point"
PARAMS = ["PRECTOTCORR", "T2M_MAX", "GWETROOT"]
FILL = -999.0
HERE = Path(__file__).resolve().parent


def fetch(lat, lon, start, end):
    q = urllib.parse.urlencode({
        "parameters": ",".join(PARAMS), "community": "AG",
        "latitude": lat, "longitude": lon, "start": start, "end": end, "format": "JSON",
    })
    url = f"{API}?{q}"
    print(f"GET {url}", file=sys.stderr)
    with urllib.request.urlopen(url, timeout=90) as r:
        body = json.load(r)
    p = body.get("properties", {}).get("parameter")
    if not p:
        sys.exit(f"Unexpected response (check parameter names): {json.dumps(body)[:500]}")
    missing = [k for k in PARAMS if k not in p]
    if missing:
        sys.exit(f"POWER did not return {missing}. Check the parameter names on the POWER docs.")
    return p


def clean(v, default=0.0):
    return default if v is None or v <= FILL + 1 else float(v)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--region", default="haor")
    ap.add_argument("--start", default="20170101")
    ap.add_argument("--end", default="20170430")
    ap.add_argument("--out", default="data/haor-2017.json")
    a = ap.parse_args()

    regions = json.loads((HERE / "regions.json").read_text())
    reg = regions[a.region]
    farm = fetch(reg["farm"]["lat"], reg["farm"]["lon"], a.start, a.end)
    up = farm if reg["upstream"] == reg["farm"] else fetch(reg["upstream"]["lat"], reg["upstream"]["lon"], a.start, a.end)

    days, gaps = [], 0
    for key in sorted(farm["PRECTOTCORR"].keys()):
        raw = [farm["PRECTOTCORR"][key], up["PRECTOTCORR"].get(key), farm["T2M_MAX"][key], farm["GWETROOT"][key]]
        gaps += sum(1 for v in raw if v is None or v <= FILL + 1)
        days.append({
            "date": f"{key[:4]}-{key[4:6]}-{key[6:]}",
            "rainUp": round(clean(raw[1]), 1),
            "rainFarm": round(clean(raw[0]), 1),
            "tmax": round(clean(raw[2], 30.0), 1),
            "soil": round(clean(raw[3], 0.5), 3),
        })

    out = {
        "region": f"{a.region}",
        "season": f"{a.start}-{a.end}",
        "sample": False,
        "source": "NASA POWER Daily API, community AG",
        "fetched_parameters": PARAMS,
        "points": {"farm": reg["farm"], "upstream": {k: reg["upstream"][k] for k in ("lat", "lon")}},
        "units": {"rainUp": "mm/day", "rainFarm": "mm/day", "tmax": "degC", "soil": "0-1 (GWETROOT)"},
        "missing_values_filled": gaps,
        "days": days,
    }
    Path(a.out).write_text(json.dumps(out, indent=1))
    print(f"Wrote {a.out}: {len(days)} days, {gaps} missing values filled", file=sys.stderr)


if __name__ == "__main__":
    main()
