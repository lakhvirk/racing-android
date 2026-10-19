# Racing

An offline rally navigation app for Android (Kotlin and Jetpack Compose).

At the start line, while you still have internet, import the race and download its map. After that the whole stage runs with no connection:

- An offline map (MapLibre with OpenFreeMap tiles) showing the route, numbered checkpoints and service points
- A live GPS speedometer
- Turn-by-turn instructions such as "Turn left in 200 m", with a "then …" preview of the following turn
- Automatic checkpoint check-in, taken in order and saved across restarts
- Off-route warning with the distance and direction back to the track
- A list of the nearest repair, fuel, medical and shop points

Turn instructions are worked out on the device from the route itself, so no routing server is needed. Organisers can add their own roadbook notes, which replace any computed instruction within 40 m of them.

## Race files

Import from a URL or a file, in either format below.

**JSON**

```json
{
  "id": "stage-1",
  "name": "Stage 1",
  "description": "optional",
  "route": [[30.84, 76.94], [30.85, 76.94]],
  "checkpoints": [{"id": "cp1", "name": "CP1", "lat": 30.85, "lon": 76.94, "radiusM": 40, "order": 1}],
  "pois": [{"id": "p1", "type": "repair", "name": "Service Park", "lat": 30.84, "lon": 76.94, "note": "Tyres"}],
  "instructions": [{"lat": 30.845, "lon": 76.94, "text": "Caution: water crossing"}]
}
```

POI `type` must be one of `repair`, `fuel`, `medical`, `shop` or `other`.

**GPX**

- The track comes from `<trk>` (or `<rte>`).
- Waypoints with `<type>checkpoint</type>` become checkpoints, in file order.
- Every other waypoint becomes a POI. Its kind is guessed from `<type>`, for example "Fuel" or "Repair".

## Offline map

Each race downloads two map regions:

- An overview of the whole area at zoom 5–12
- A 2 km-wide corridor along the route, plus the area around every checkpoint and POI, at zoom 13–16

The 25 km sample stage comes to about 37 MB.

The map style URL lives in `map/MapConfig.kt`.

## Build and test

```sh
./gradlew testDebugUnitTest   # navigation engine, parsers, full sample-stage drive
./gradlew installDebug
```

To simulate a drive on the emulator:

1. Add the sample race and download its map.
2. Turn on airplane mode.
3. Start the race.
4. In the emulator, go to Extended controls → Location → Routes and load `tools/sample_drive.gpx`.

`tools/make_sample_race.py` regenerates the sample race from an OSRM route.

## Layout

| Package | Contents |
|---|---|
| `nav/` | Pure-Kotlin navigation engine: geometry, route matching, maneuver extraction, check-ins, speed |
| `data/` | Race model, JSON/GPX parser, Room database, repository, offline map downloader |
| `location/` | Foreground GPS service (platform `LocationManager`, so it works without network or Play Services) |
| `map/` | MapLibre composable with route, checkpoint, POI and position layers |
| `ui/` | Compose screens: race list, prepare, drive, service points |
