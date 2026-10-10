#!/usr/bin/env python3
"""Rank years by how dry the Barind growing window was, using NASA POWER daily data.

Save as data-pipeline/scan_barind_years.py (or anywhere) and run from the repo root:
    python data-pipeline/scan_barind_years.py
    python data-pipeline/scan_barind_years.py --from 2005 --to 2025

For each year it looks at the scenario's growing window (default 25 Jul - 22 Nov) and reports:
  min soil   lowest root-zone wetness (GWETROOT, 0-1). Lower = drier.
  <0.55/<0.60  number of days below those soil levels.
  rain mm    total rain on the farm point in the window.
  dry run    longest run of days with under 1 mm of rain.
  hot days   days with tmax >= 37 C (the game's heat threshold).
It only reads data and prints a table. It does not change any project file.
It reuses fetch() from fetch_power.py, so it needs only Python 3.8+ and internet access.
"""
import argparse, json, sys, time
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import fetch_power  # noqa: E402

FILL = fetch_power.FILL


def good(v):
    return v is not None and v > FILL + 1


def analyse(p, year, start_md, end_md):
    lo, hi = f"{year}{start_md}", f"{year}{end_md}"
    keys = sorted(k for k in p["GWETROOT"] if lo <= k <= hi)
    soil = [p["GWETROOT"][k] for k in keys]
    rain = [p["PRECTOTCORR"].get(k) for k in keys]
    tmax = [p["T2M_MAX"].get(k) for k in keys]
    ok_soil = [s for s in soil if good(s)]
    if len(ok_soil) < 0.9 * max(len(keys), 1):
        return None
    run = best = 0
    for r in rain:
        if good(r) and r < 1.0:
            run += 1
            best = max(best, run)
        else:
            run = 0
    return {
        "year": year,
        "days": len(keys),
        "min_soil": min(ok_soil),
        "lt55": sum(1 for s in ok_soil if s < 0.55),
        "lt60": sum(1 for s in ok_soil if s < 0.60),
        "rain": sum(r for r in rain if good(r)),
        "dry_run": best,
        "hot": sum(1 for t in tmax if good(t) and t >= 37.0),
    }


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--region", default="barind")
    ap.add_argument("--from", dest="y0", type=int, default=2005)
    ap.add_argument("--to", dest="y1", type=int, default=2025)
    ap.add_argument("--window-start", default="0725", help="MMDD, default 0725")
    ap.add_argument("--window-end", default="1122", help="MMDD, default 1122")
    a = ap.parse_args()

    reg = json.loads((HERE / "regions.json").read_text())[a.region]["farm"]
    rows = []
    for year in range(a.y0, a.y1 + 1):
        for attempt in (1, 2, 3):
            try:
                p = fetch_power.fetch(reg["lat"], reg["lon"], f"{year}0601", f"{year}1231")
                break
            except (Exception, SystemExit) as e:  # network hiccup, API error, etc.
                print(f"  {year}: attempt {attempt} failed ({e})", file=sys.stderr)
                p = None
                time.sleep(2 * attempt)
        if p is None:
            continue
        row = analyse(p, year, a.window_start, a.window_end)
        if row:
            rows.append(row)
        else:
            print(f"  {year}: too many missing values, skipped", file=sys.stderr)
        time.sleep(0.5)

    if not rows:
        sys.exit("No data collected.")
    rows.sort(key=lambda r: r["min_soil"])
    print(f"\nDriest years first, window {a.window_start}-{a.window_end}, region {a.region}")
    print("year  min soil  <0.55  <0.60  rain mm  dry run  hot days")
    for r in rows:
        print(f"{r['year']}   {r['min_soil']:.3f}    {r['lt55']:>4}   {r['lt60']:>4}   {r['rain']:>6.0f}   {r['dry_run']:>5}    {r['hot']:>4}")


if __name__ == "__main__":
    main()
