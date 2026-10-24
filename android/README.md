# varun.surf for Android

Native Android client for the varun.surf backend, built from [SPEC.md](SPEC.md).
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
feature/spot         spot detail (live + 12 h history, hourly table/chart, model sheet, AI/ICM with confirmation)
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

- `https://varun.surf/spot/{id}`, `/country/{name}`, `/map` (verified App Links: needs
  `/.well-known/assetlinks.json` on the server with the release certificate, spec 12.1)
- `varun://spot/{id}?focus=<spot-local ISO time>`, `varun://map`, `varun://search` (widgets,
  notifications, shortcuts)

## Deviations from SPEC.md

- **Maps**: MapLibre Native SDK in an `AndroidView` instead of `maplibre-compose` - the wind field
  overlay needs the raw projection every frame.
- **Basemaps**: only free, keyless sources - Esri World Imagery satellite (the default, same tiles as
  the web map) and the OpenFreeMap "liberty" map; no MapTiler key and no dark basemap (spec 5.4/Q4).
- **Charts**: drawn with Compose Canvas instead of Vico.
- **Time zones** (Q1): taken from a country -> zone table split by coordinates for multi-zone
  countries (`SpotTimeZones`) instead of a bundled tz-boundary dataset.
- **Play upload**: the `r0adkll/upload-google-play` action instead of Gradle Play Publisher.
- **Tests**: JUnit 4 + Truth + Turbine + Robolectric; no Roborazzi screenshots, Maestro flows or Kover yet.
- **Large screens**: navigation rail from 600 dp and a map side sheet; no list-detail pane yet.
- Not built (v1.1 in the spec): compass-relative wind, Quick Settings tile, webcam previews,
  favourites import from the web, offline map regions.
