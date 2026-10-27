# varun.surf for Android

Native Android client for the varun.surf backend, built from [SPEC.md](SPEC.md).
Google Play release plan and checklist: [PUBLISHING.md](PUBLISHING.md).
Kotlin, Jetpack Compose (Material 3), Navigation 3, Hilt, Room, DataStore,
Retrofit/OkHttp, Coil, MapLibre Native, WorkManager and Glance.

- Application id `surf.varun.app`, minSdk 28, targetSdk 36, compileSdk 37
- Separate Gradle build from the backend: run everything from this directory

## Build, test, run

```bash
./gradlew assembleDebug            # debug APK against https://varun.surf
./gradlew test                     # unit tests (JVM + Robolectric)
./gradlew lintDebug                # Android lint
./gradlew assembleRelease          # R8-minified release (unsigned without signing env vars)
./gradlew installDebug -Pvarun.baseUrl=http://10.0.2.2:8080   # against a local ./gradlew bootRun
./gradlew :app:generateBaselineProfile                          # needs a device/emulator
```

Optional inputs:

| Property / env | Used for |
|---|---|
| `-Pvarun.baseUrl` | backend URL (default `https://varun.surf`) |
| `-Pvarun.versionName`, `VERSION_CODE` | release versioning (CI sets them) |
| `VARUN_KEYSTORE_FILE`, `VARUN_KEYSTORE_PASSWORD`, `VARUN_KEY_ALIAS`, `VARUN_KEY_PASSWORD` | release signing |

## Modules

```
app/                 Application, MainActivity, per-tab Navigation 3 back stacks, deep links, shortcuts
core/model           pure Kotlin models
core/domain          pure Kotlin rules ported from the web: wind classes, firing score, staleness (60 min),
                     calculator (golden-tested against calculator.js), windy days, alert evaluation,
                     wind field maths (map.js), spot time zones, search folding
core/network         Retrofit API, DTOs, SESSION cookie handling (renew ahead of expiry, 401 -> renew -> retry once,
                     single-flight), GET retries, 90 s no-retry client for the paid POSTs
core/database        Room (spots, live, history, daily/hourly forecasts, AI, models, favourites, alerts, recents)
core/datastore       settings and calculator inputs
core/data            offline-first repositories (Room is the source of truth), on-demand (paid) operations,
                     wind timeline cache, connectivity, coarse location
core/designsystem    theme from the web palette, wind colour scale, WindArrow, WindNumber, shared components
core/ui              SpotCard, formatting, banners, intents (Custom Tabs, geo:, share)
core/navigation      route keys and the Navigator; features never depend on each other
core/testing         captured production payloads and in-memory fakes
feature/spots        Spots + Favourites tabs (filters, country sheet, sort, drag reorder)
feature/spot         spot detail (live + 12 h history chart/table, hourly table/chart, model sheet with a loading state, AI with confirmation)
feature/map          MapLibre map, clustering, wind field overlay with particles, timeline
feature/search       local instant search with diacritics folding
feature/calculator   kite & board calculator
feature/alerts       wind alerts, AlertsWorker, notifications
feature/settings     theme, language, units, map, location, cache
feature/about        service status, data sources, sponsors, privacy, licences
widget/              Glance spot and favourites widgets + refresh worker
benchmark/           baseline profile generator and startup/scroll macrobenchmarks
```

## Deep links

- `https://varun.surf/spot/{id}`, `/country/{name}`, `/map` (verified App Links through
  `/.well-known/assetlinks.json`, source in `../src/frontend/assets/assetlinks.json`, spec 12.1)
- `varun://spot/{id}?focus=<spot-local ISO time>`, `varun://map`, `varun://search` (widgets,
  notifications, shortcuts)

## Deviations from SPEC.md

- **Maps**: MapLibre Native SDK in an `AndroidView` instead of `maplibre-compose` - the wind field
  overlay needs the raw projection every frame.
- **Globe view**: built after all (the spec dropped it). A globe button on the map swaps MapLibre
  for a Compose Canvas port of the web's `common/globe.js` (`feature/map/Globe*.kt`): orthographic
  projection, land clipped at the horizon, wind wash and particles, clusters, drag/pinch/fling and
  an idle spin (layers menu; off under battery saver and reduced motion). MapLibre Native has no
  globe projection. Coastlines are world-atlas 1:110m and 1:50m (the outlines the web loads),
  shipped in `assets/globe/` - regenerate with `feature/map/tools/globe_outlines.py`. The choice is
  kept in settings (`mapGlobe`, `mapGlobeSpin`).
- **Basemaps**: only free, keyless sources - Esri World Imagery satellite (the default, same tiles as
  the web map) and the OpenFreeMap "liberty" map; no MapTiler key and no dark basemap (spec 5.4/Q4).
- **Charts**: drawn with Compose Canvas instead of Vico.
- **Time zones** (Q1): taken from a country -> zone table split by coordinates for multi-zone
  countries (`SpotTimeZones`) instead of a bundled tz-boundary dataset.
- **Play upload**: the `r0adkll/upload-google-play` action instead of Gradle Play Publisher.
- **Tests**: JUnit 4 + Truth + Turbine + Robolectric; no Roborazzi screenshots, Maestro flows or Kover yet.
- **Spot widget**: two layouts instead of three - 2×1 (wind number + arrow) and, from 3×1, everything
  else at once (gusts, temp, age and the 12 h strip), with wind and gusts at one size so it fits a row.
- **Spots list chips**: no "Nearby" (spots within 50 km) or "Windy today" chips - country, sort and
  "Live only" only. Distances on cards and the distance sort stay, once location is on in settings.
- **Large screens**: navigation rail from 600 dp and a map side sheet; no list-detail pane yet.
- **Map controls**: all in one floating card that folds away to a single button (portrait and
  landscape), rather than a column of separate small FABs.
- **Landscape phones** (window under 480 dp tall): instead of the bottom bar, a floating navigation
  card that collapses to a single menu button and floats over every screen (the map, the spots list
  and the spot page run under it, moving only their top bar clear of the button; the others keep
  the button's strip clear); the spots list in two columns with its chips beside the search field;
  a spot page with a one-line bar (no photo or map preview), the forecast controls and day chips on
  the heading's row and the sections below the forecast in two columns; the calculator's result
  beside its inputs; and on the map the controls card in three short columns, the spot in a popup
  floating over the right of the map (the controls step aside while it is open) and the timeline
  hidden until the layers menu shows it (without a drag handle, and without changing the setting
  portrait uses).
- Not built (v1.1 in the spec): compass-relative wind, Quick Settings tile, webcam previews,
  favourites import from the web, offline map regions.
