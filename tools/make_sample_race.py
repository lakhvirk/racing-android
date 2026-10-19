#!/usr/bin/env python3
"""Builds app/src/main/assets/sample_race.json and tools/sample_drive.gpx from an OSRM route.

Usage: python3 -I tools/make_sample_race.py osrm.json
where osrm.json comes from:
  curl "https://router.project-osrm.org/route/v1/driving/76.9395,30.8398;76.9650,30.8990?overview=full&geometries=geojson"
"""
import json
import math
import sys
from datetime import datetime, timedelta, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


def dist(a, b):
    la1, lo1, la2, lo2 = map(math.radians, (a[0], a[1], b[0], b[1]))
    h = math.sin((la2 - la1) / 2) ** 2 + math.cos(la1) * math.cos(la2) * math.sin((lo2 - lo1) / 2) ** 2
    return 2 * 6371008.8 * math.asin(math.sqrt(h))


def main(osrm_path):
    coords = json.load(open(osrm_path))["routes"][0]["geometry"]["coordinates"]
    route = [[round(lat, 6), round(lon, 6)] for lon, lat in coords]
    cum = [0.0]
    for i in range(1, len(route)):
        cum.append(cum[-1] + dist(route[i - 1], route[i]))
    total = cum[-1]

    def at(frac):
        target = total * frac
        i = next(i for i, c in enumerate(cum) if c >= target)
        return route[i]

    def offset(p, north_m, east_m):
        return [round(p[0] + north_m / 111195.0, 6),
                round(p[1] + east_m / (111195.0 * math.cos(math.radians(p[0]))), 6)]

    checkpoints = []
    for n, frac in enumerate([0.25, 0.5, 0.75], start=1):
        lat, lon = at(frac)
        checkpoints.append({"id": f"cp{n}", "name": f"CP{n}", "lat": lat, "lon": lon, "radiusM": 40, "order": n})
    end = route[-1]
    checkpoints.append({"id": "finish", "name": "Finish", "lat": end[0], "lon": end[1], "radiusM": 40, "order": 4})

    poi_specs = [
        (0.02, "repair", "Service Park A", "Full service, tyres, 08:00-20:00"),
        (0.40, "repair", "Roadside Mechanic", "Basic repairs, welding"),
        (0.45, "fuel", "Fuel Station", "Petrol and diesel"),
        (0.62, "medical", "Medical Point", "Rally medical crew"),
        (0.80, "repair", "Service Park B", "Tyres and suspension"),
        (0.90, "shop", "Spare Parts Shop", "Filters, belts, fluids"),
    ]
    pois = []
    for n, (frac, kind, name, note) in enumerate(poi_specs, start=1):
        lat, lon = offset(at(frac), 40, 40)
        pois.append({"id": f"poi{n}", "type": kind, "name": name, "lat": lat, "lon": lon, "note": note})

    lat, lon = at(0.33)
    race = {
        "id": "sample-kalka-kasauli",
        "name": "Kalka → Kasauli Hill Stage (sample)",
        "description": f"Demo stage of {total / 1000:.1f} km with hairpins, 4 checkpoints and service points.",
        "route": route,
        "checkpoints": checkpoints,
        "pois": pois,
        "instructions": [{"lat": lat, "lon": lon, "text": "Caution: narrow section, keep right"}],
    }
    (ROOT / "app/src/main/assets").mkdir(parents=True, exist_ok=True)
    (ROOT / "app/src/main/assets/sample_race.json").write_text(json.dumps(race, ensure_ascii=False))

    # GPX for emulator playback at a steady 50 km/h.
    speed = 50 / 3.6
    t0 = datetime(2026, 1, 1, tzinfo=timezone.utc)
    pts = "\n".join(
        f'<trkpt lat="{p[0]}" lon="{p[1]}"><time>{(t0 + timedelta(seconds=c / speed)).strftime("%Y-%m-%dT%H:%M:%SZ")}</time></trkpt>'
        for p, c in zip(route, cum)
    )
    (ROOT / "tools/sample_drive.gpx").write_text(
        '<?xml version="1.0" encoding="UTF-8"?>\n'
        '<gpx version="1.1" creator="racing" xmlns="http://www.topografix.com/GPX/1/1">\n'
        f"<trk><name>Sample drive</name><trkseg>\n{pts}\n</trkseg></trk></gpx>\n"
    )
    print(f"route {total:.0f} m, {len(route)} points")


if __name__ == "__main__":
    main(sys.argv[1])
