# CLAUDE.md - Android App

Native Android client for the varun.surf backend. This directory is a **separate Gradle
build** from the Spring Boot backend in the repository root: run every Gradle command
from `android/`, never from the root.

- Product and technical spec: [SPEC.md](SPEC.md) (section numbers below refer to it)
- Developer notes, module map, deep links, deviations from the spec: [README.md](README.md)
- Backend context: [../CLAUDE.md](../CLAUDE.md)

## Tech Stack

- Kotlin 2.4, AGP 9, Gradle with configuration cache, JDK 25 daemon toolchain, JVM target 17
- Jetpack Compose (Material 3, adaptive navigation suite), Navigation 3
- Hilt (KSP), Room, DataStore, WorkManager, Glance widgets
- Retrofit 3 + OkHttp 5 + kotlinx.serialization, Coil 3, MapLibre Native, Timber
- Tests: JUnit 4, Truth, Turbine, MockK, Robolectric, MockWebServer
- Application id `surf.varun.app` (permanent once on Play), minSdk 28, targetSdk 36, compileSdk 37
- All versions live in `gradle/libs.versions.toml`; never hardcode a version in a module. The DEPS workflow bumps them daily (stable releases within the current major, via the `com.github.ben-manes.versions` report and `../.github/scripts/update-android-catalog.py`)

## Build & Test

```bash
./gradlew assembleDebug                 # debug APK against https://varun.surf
./gradlew test                          # all unit tests (JVM + Robolectric)
./gradlew :core:domain:test             # one module
./gradlew lintDebug                     # Android lint
./gradlew assembleRelease               # R8-minified release (unsigned without signing env vars)
./gradlew installDebug -Pvarun.baseUrl=http://10.0.2.2:8080   # against a local ./gradlew bootRun
./gradlew :app:generateBaselineProfile  # needs a device/emulator
```

CI (`../.github/workflows/android.yml`) runs `lintDebug`, `test` and `assembleRelease` on
changes under `android/**` (a push or PR touching only `.md` files there is skipped). Run the same three before calling a change done. An
`android-v<x.y.z>` tag builds a signed AAB and uploads it to the Play internal track.

## Module Structure

```
app/              Application, MainActivity, per-tab Navigation 3 back stacks, deep links
core/model        pure Kotlin models (no Android)
core/domain       pure Kotlin rules ported from the web (no Android)
core/network      Retrofit API, DTOs + mappers, SESSION cookie handling, retries
core/database     Room (schema exported to core/database/schemas)
core/datastore    settings and calculator inputs
core/data         offline-first repositories, on-demand (paid) operations, connectivity
core/designsystem theme, wind colour scale, WindArrow, WindNumber, shared components
core/ui           SpotCard, formatting, banners, intents
core/navigation   route keys and the Navigator
core/testing      captured production fixtures and in-memory fakes
feature/*         spots, spot, map, search, calculator, alerts, settings, about
widget/           Glance widgets + refresh worker
benchmark/        baseline profile generator and macrobenchmarks
build-logic/      convention plugins
```

Module conventions are enforced by the plugins in `build-logic/convention`:

- `varun.jvm.library` - pure Kotlin (`core:model`, `core:domain`)
- `varun.android.library`, `varun.android.compose`, `varun.hilt`
- `varun.android.feature` - a feature module; already wires in `core:model`, `domain`,
  `data`, `designsystem`, `ui`, `navigation`, lifecycle/Hilt/Compose, `core:testing` and
  Turbine. A new feature module should only need this plugin, a `namespace`, and its
  own extra libraries
- **Features never depend on each other.** Cross-feature navigation goes through route
  keys in `core:navigation`; shared UI goes to `core:ui` or `core:designsystem`
- New modules must be added to `settings.gradle.kts`; reference them through type-safe
  accessors (`projects.core.data`)

## Architecture

- **UI**: stateless composables + one `ViewModel` per screen exposing
  `StateFlow<UiState>` (immutable data classes), collected with
  `collectAsStateWithLifecycle`. One-shot effects via a channel/flow. Use
  `kotlinx.collections.immutable` for lists in UI state (stable, jank-free lists)
- **Domain**: plain functions in `core:domain`; no use-case boilerplate for 1:1
  repository passthroughs
- **Data**: Room is the single source of truth. Repositories expose `Flow`s from Room,
  network refreshes write into Room, network calls are `suspend`. The UI is never blank
  when a cached copy exists
- Polling (60 s on spots/spot/map, 5 s on the sweep-progress banner) runs only while the
  screen is visible (`repeatOnLifecycle(STARTED)`); see spec 6.1 for the refresh policy

## Backend Contract

The app consumes the public API only (spec 3): `/api/v1/session`, `/spots`,
`/spots/{id}[/{model}]`, `/forecast/{id}`, `/wind`, `/status`, `/status/forecast`,
`/sponsors`, and the two paid POSTs (`/spots/{id}/analysis`, `/spots/{id}/icm`).

- **Everything is optional on the client.** DTOs (`core/network/dto/Dtos.kt`) give every
  field a default and are decoded with `NetworkJson` (`ignoreUnknownKeys`,
  `explicitNulls = false`, `coerceInputValues`). The backend ships independently of app
  releases - a renamed backend field silently becomes absent here
- Empty-string URLs mean "absent". `spotPhotoUrl` is relative to the base URL
- Hourly `Forecast.date` is a zone-less wall-clock time in the **spot's** time zone
  (`SpotTimeZones`); `CurrentConditions.date` is `dd/MM/yy HH:mm:ss` station-local
- Daily labels ("Today", "Tomorrow", weekdays) are English tokens from the server, mapped
  to localized labels on the client
- Every request sends `X-Client: varun-android/<version>` (`ClientHeadersInterceptor`)
- Session: an OkHttp cookie jar persisted in DataStore; the cookie is renewed ahead of
  expiry, a 401 triggers one single-flight `/api/v1/session` and one retry. Never parse
  or verify the HMAC on the client
- GETs retry with backoff on 5xx/timeouts (max 3). The paid POSTs use a separate 90 s
  client and are **never retried automatically**
- Base URL comes from `-Pvarun.baseUrl` (`BuildConfig.BASE_URL` in `core:network`)

## Paid Operations (AI analysis, ICM)

Each press can cost an LLM call (spec 6.5):

- Always behind a confirmation dialog, with the web's wording in both languages
- One request per spot at a time; button disabled while in flight
- Run in the application-scoped coroutine (`@ApplicationScope`) so they survive
  navigation and configuration changes; the result lands in the spot cache
- **Never** triggered from widgets, workers, alerts or any automatic path

## Background Work

- WorkManager only, `NetworkType.CONNECTED`, no foreground service, no exact alarms
- `AlertsWorker`: live checks use the cached `/api/v1/spots` list, forecast checks use
  `/api/v1/forecast/{wgId}`. Never call `/api/v1/spots/{wgId}` from the background - it
  is heavy and triggers server-side Windguru model discovery
- At most one `/api/v1/spots` request per 15 min from all background work combined
  (shared freshness check in the repository). The backend sits behind nginx rate limits
  and Windguru has blocked the production IP before - be a good citizen

## Web Parity (keep in sync)

`core:domain` ports rules from the web frontend and must match it. When one side
changes, change the other:

| Kotlin (`core/domain`) | Web (`../src/frontend/js/`) |
|---|---|
| `WindClass.kt` | `common/weather.js` (wind classes) |
| `Conditions.kt` | `common/weather.js` (60 min staleness), `page/index.js` (`firingScore`) |
| `KiteCalculator.kt` | `common/calculator.js` (golden tests generated from the JS) |
| `ForecastDays.kt` | windy days filter on the spot page |
| `WindField.kt` | `common/map.js` (interpolation, particle constants) |
| `DateParsing.kt` | `common/date.js` |

The design system palette follows the web's theme.

## Testing

- `core:domain`: exhaustive JVM unit tests (wind classes, calculator golden cases,
  alert direction sectors wrapping 337.5°→22.5°, time zones and DST edges)
- `core:network`: MockWebServer with **real production payloads** from
  `core/testing/src/main/resources/fixtures` (read through `Fixtures`); cover
  missing/extra fields, empty strings and the 401 → renew → retry flow
- `core:data`: fake API + in-memory Room
- ViewModels: Turbine state tests; Android-dependent tests use Robolectric
- When the backend response shape changes, refresh the matching fixture rather than
  hand-editing it into something the server never sent
- Coverage target: ≥ 80% for `core:*`

## Conventions

- Kotlin official code style; match the surrounding code's naming and comment density
- Every user-visible string goes into `res/values/strings.xml` **and**
  `res/values-pl/strings.xml` of the module that uses it. No hardcoded UI text
- Accessibility: touch targets ≥ 48 dp, content descriptions on wind arrows, wind
  classes never conveyed by colour alone, layouts that survive 200% font scale
- Edge-to-edge everywhere; navigation rail from 600 dp. On a window under 480 dp tall (a phone
  held sideways - `isCompactHeight()` in `core:ui`, which screens use to adapt too) the navigation
  floats over the start edge instead: screens keep the collapsed button's strip clear and the
  opened card floats over them, except entries marked `FULL_BLEED` (map, spots, spot), which run
  under it: they move their top bar in by `LocalFloatingNavigationGutter` (the map reads
  `LocalFloatingNavigationInset` instead) and keep the rest clear of the side safe area themselves
- Room: the schema is exported (`exportSchema = true`). Any entity change bumps
  `VarunDatabase.version`, adds a migration and commits the new
  `core/database/schemas/.../<version>.json`
- Release builds are R8-minified: new serialized/reflective classes may need rules in
  `proguard-rules.pro`; verify with `./gradlew assembleRelease`
- Map: MapLibre Native in an `AndroidView` (not `maplibre-compose`); basemaps are keyless
  only (Esri World Imagery, OpenFreeMap). Charts are Compose Canvas (no chart library)
- Documented deviations from the spec are listed in README.md - update that list when
  deviating further
- Commit messages must NOT include "Co-Authored-By" or any AI attribution lines

## Not Built Yet

- v1.1 items from the spec: compass-relative wind, Quick Settings tile, webcam previews,
  favourites import from the web, offline map regions
- Roborazzi screenshots, Maestro flows, Kover coverage, list-detail pane on large screens
