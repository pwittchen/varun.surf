# varun.surf — Native Android App Plan

**Status**: proposal / not started
**Written**: 2026-09-22
**Scope**: a native Kotlin Android client for the existing varun.surf backend, with a
mobile-first UI rather than a port of the web frontend.

---

## 1. Goal

Ship a native Android app that answers the three questions the web app answers, but
in a shape a phone is actually good at:

1. **Is it blowing right now, where I care?** — favourites and live stations first.
2. **When will it blow?** — hourly and 5-day forecast for one spot.
3. **Where should I go?** — map and "find me wind above X kt" search.

Two things the web app *cannot* do, and which justify a native app on their own:

- **Home-screen widgets** (Glance) showing live wind at a favourite spot.
- **Wind alerts** — a push/local notification when a favourite spot crosses a
  threshold in the forecast. This is the feature that makes people keep the app.

Non-goals for v1: the tools pages (`/metrics`, `/logs`, `/sources`, `/mcp`), the
embed builder, TV view, drag-and-drop custom ordering, sponsors management.

---

## 2. What the backend already gives us (measured, 2026-09-22)

No backend rewrite is needed. The REST API is already the right shape; the numbers
below are from the live production API and drive several design decisions.

| Endpoint | Shape | Size | Notes |
|---|---|---|---|
| `GET /api/v1/spots` | `Spot[]` | **2.38 MB raw / 537 KB gzip**, 790 spots, 43 countries | `forecastHourly` stripped; 5 daily entries per spot; 15 spots carry live conditions; all 790 carry `coordinates` |
| `GET /api/v1/spots/{wgId}` | `Spot` | ~3–60 KB | adds `forecastHourly` (~179 h), `currentConditionsHistory` (~720 pts = 12 h @ 1 min), `spotPhotoUrl`; triggers async model discovery |
| `GET /api/v1/spots/{wgId}/{model}` | `Spot` | — | `gfs`, `ifs`, `icm`, … from `availableModels` |
| `GET /api/v1/wind?hours=N` | `WindTimeline` | **75 KB** @ 120 h | all spots on one hour grid, parallel int arrays, direction as index into `["N","NE","E","SE","S","SW","W","NW"]` |
| `GET /api/v1/forecast/{wgId}` | `HourlyForecast` | ~1–5 KB | one spot, same grid, all variables |
| `POST /api/v1/spots/{id}/analysis?lang=en\|pl` | `Spot` | — | costs an LLM call; 24 h cache; 503 on failure |
| `POST /api/v1/spots/{id}/icm` | `Spot` | — | vision call; 24 h cache; 503 when no grid point |
| `GET /api/v1/sponsors`, `/status`, `/status/forecast` | — | small | status feeds an About screen + "forecast sweep in progress" state |
| `GET /api/v1/session` | — | 0 | **204 + `Set-Cookie: SESSION=…`**; the client's way in (§2.1) |
| `GET /llms/*.md` | Markdown | — | **cookie-exempt**; useful as a fallback / debugging path |

### 2.1 The one real obstacle: the SESSION cookie

`SessionAuthenticationFilter` refuses every `/api/v1/**` call (except
`/api/v1/health`) without a valid `SESSION` cookie, and only *page* visits get one
issued. A native client must therefore bootstrap by requesting a non-API path
(e.g. `GET https://varun.surf/`, which returns `Set-Cookie: SESSION=…`) and then
replay that cookie. Verified working: a plain `curl -c` against `/` followed by
`curl -b` against `/api/v1/spots` returns 200.

**Two options — the first is now implemented; keep the second as a fallback:**

- **(A, done — 2026-09-22) `GET /api/v1/session`.** Answers **204** and sets the
  same cookie a page visit would. Implemented as an inversion in
  `SessionAuthenticationFilter` (the path is excluded from the API gate and falls
  through to the page-visit cookie branch) plus a two-line `SessionController`.
  A caller already holding a fresh token gets 204 with **no** `Set-Cookie` and
  should keep the one it has. Covered by four tests in
  `SessionAuthenticationFilterTest`.
- **(B, fallback) Bootstrap off `GET /`.** Still works. Implement it as the
  fallback path in the auth interceptor anyway, so the app survives a backend
  rollback or an older deployed version.

Client side either way: an OkHttp `CookieJar` persisted in DataStore, plus an
`Authenticator`/`Interceptor` that, on a 401, bootstraps once and replays the
request (guard with a mutex so a burst of parallel calls triggers one bootstrap,
not twenty).

> Note for the backend: `nginx/nginx.conf` rate limiting is what actually protects
> the API. Before release, check the app's refresh cadence against those limits —
> a 1-minute live refresh across many installs is a different load profile than
> browser tabs.

### 2.2 Data quirks the client must handle

- `currentConditions.date` is **not ISO-8601** — it is station-local text in one of
  four formats (`yyyy-MM-dd HH:mm:ss`, `yyyy-MM-dd HH:mm`, `dd.MM.yyyy HH:mm`,
  `dd/MM/yy HH:mm:ss`). Port `CurrentConditionsStalenessChecker` logic: unparseable
  or ≥24 h old ⇒ show the stale (amber) treatment.
- `forecast[].date` for the daily list is a **label**, not a date: `"Today"`,
  `"Tomorrow"`, `"Day 3"`… Must be localised client-side, not parsed.
- `wgId` is derived from the Windguru URL, or a deterministic hash ≥ 9 000 000 for
  spots without one. Treat as an opaque stable id — it is the join key everywhere
  (`/wind` series, `/forecast/{wgId}`, favourites, widgets).
- `availableModels` is discovered asynchronously after the first single-spot hit;
  the web app polls for it. The app should do the same (bounded: ~5 polls @ 5 s)
  or simply re-fetch once on screen resume.
- `icmUrl`, `windfinderUrl`, `webcamUrl` may be `null` **or** `""` — both mean absent.
- Wind/temp are knots/°C integers in live data, doubles in forecasts.

---

## 3. Tech stack (recommended, with the alternatives that were considered)

| Concern | Choice | Why / alternative |
|---|---|---|
| Language | Kotlin 2.x, JDK 17 toolchain | — |
| UI | **Jetpack Compose + Material 3** (expressive where it fits) | No XML layouts. Views only if a third-party map widget demands it. |
| Min / target SDK | min **26**, target latest stable | 26 covers ~98% of devices and unlocks `java.time` without desugaring; drop to 24 with desugaring only if analytics later argue for it. |
| Architecture | Single-activity, Compose Navigation, MVVM + UDF (`ViewModel` → immutable `UiState` → Compose) | Matches Android team expectations and Now-in-Android conventions. |
| DI | **Hilt** | Koin is fine and lighter, but Hilt's compile-time checks pay off once WorkManager + Glance widgets need injection. |
| Networking | **Retrofit + OkHttp + kotlinx.serialization** | Ktor client is the alternative; Retrofit wins on OkHttp's cookie jar, cache and interceptor ecosystem, which we need for §2.1. |
| Persistence | **Room** (spots, forecasts, wind timeline) + **DataStore** (prefs, cookie) | 2.4 MB of spots must not be re-downloaded per launch; Room also makes the app usable offline on the beach with no signal — a genuine kitesurfing requirement. |
| Images | **Coil 3** | spot photos, sponsor logos. |
| Maps | **MapLibre GL Android** (vector) or **osmdroid** (raster, drop-in for the current OSM/Esri tiles) | See §7. Google Maps SDK is the third option — best clustering and gestures, but ties tiles to a paid key and diverges visually from the web app. |
| Charts | Hand-rolled Compose `Canvas` | The wind chart is a bespoke bar+line composite; a charting library would fight it. Reuse the visual language from `spot.js: renderWindChart`. |
| Background work | **WorkManager** (periodic refresh, alert evaluation) | — |
| Widgets | **Glance** | — |
| Build | Gradle Kotlin DSL + **version catalog** (`libs.versions.toml`), convention plugins if the module count grows | — |
| Testing | JUnit5 + Turbine + MockWebServer (unit), Roborazzi or Paparazzi (screenshot), Compose UI tests + Macrobenchmark (instrumented) | MockWebServer mirrors the backend's own test setup. |
| Static analysis | ktlint + detekt + Android Lint in CI | — |

### Repository decision

**Recommendation: a separate repository, `varun.surf-android`.** The Gradle build
here is a Spring Boot + Bun build; bolting the Android Gradle Plugin onto it makes
both CI jobs slower and more fragile, and the app has an independent release
cadence (Play review) from the server. The API contract is the only coupling, and
that is better expressed as a versioned contract doc than as shared source.

If a monorepo is preferred anyway: put it under `android/` with its own
`settings.gradle.kts` and a separate GitHub Actions workflow, and never let the
root build depend on it.

---

## 4. Module structure

```
varun.surf-android/
├── app/                        # Application, DI graph, navigation host, theme
├── core/
│   ├── designsystem/           # Colors (wind scale), typography, spacing, components
│   ├── model/                  # Domain models: Spot, Forecast, CurrentConditions, …
│   ├── network/                # Retrofit API, DTOs, session interceptor, mappers
│   ├── database/               # Room entities, DAOs, converters
│   ├── datastore/              # Preferences: theme, language, favourites, filters
│   ├── data/                   # Repositories = the offline-first merge point
│   ├── common/                 # Result/error types, dispatchers, clock, formatters
│   └── testing/                # Fakes, fixtures, MockWebServer rules
├── feature/
│   ├── spots/                  # List + filters + search
│   ├── spotdetail/             # Detail: now, hourly, daily, chart, info, links
│   ├── map/                    # Map + clustering + wind field + time slider
│   ├── favorites/
│   ├── settings/               # Theme, language, units, alerts, about
│   └── alerts/                 # Threshold rules + evaluation
├── widget/                     # Glance widgets
└── benchmark/                  # Macrobenchmark + baseline profile
```

Rule: `feature/*` never talks to `core/network` or `core/database` directly — only
through `core/data` repositories.

---

## 5. Mobile UI design (this is the part that must *not* copy the web app)

The web app is a dense desktop grid with a sidebar, a list view with sortable
columns, drag-and-drop ordering, modals and a hero image. Almost none of that
should survive the port.

### Navigation: bottom bar, 4 destinations

```
┌──────────────────────────────────────┐
│  varun.surf            🔍   ⋮        │  ← small top app bar, collapsing
├──────────────────────────────────────┤
│                                      │
│           screen content             │
│                                      │
├──────────────────────────────────────┤
│  ★ Spots   🗺 Map   ⚡ Now   ⚙ More  │
└──────────────────────────────────────┘
```

- **Spots** — the default list. Favourites pinned at the top in their own section,
  then everything else. Filter chips row (Country · Live only · Windy now) replaces
  the desktop dropdown + toggles. Search in the top bar (`SearchBar` M3), not a
  keyboard shortcut.
- **Map** — full-bleed map, the single most phone-native screen. Bottom sheet for
  the selected spot replaces the desktop "side peek".
- **Now** — the 15 spots with live stations, big numbers, pull-to-refresh. This is
  the screen a kiter opens in the car park; it deserves its own tab even though the
  web app has no equivalent.
- **More** — settings, alerts, about, status, links.

### Spot list item

One row, scannable in a glance, no table:

```
┌────────────────────────────────────────────┐
│ ★  Jastarnia                    🟢 17 kt  │
│    Poland · live 2 min ago        ↘ NW 18 │
│    ▁▂▅▇▇▅▂  Today → Fri                   │  ← 5-day sparkline, wind-coloured
└────────────────────────────────────────────┘
```

- Wind colour scale ported verbatim from the web CSS variables so the two products
  read the same: `<12 kt` grey `#64748b`, `12–18` green `#2ee66d`, `18–25` amber
  `#f59e0b`, `>25` red `#f5484a` (dark theme); light theme uses the `#6b7280 /
  #059669 / #ea580c / #dc2626` set. Map-only extra steps: calm `<5` grey, light
  `5–12` blue `#38bdf8`.
- Stale live data (≥24 h) → amber dot + "outdated" label, per §2.2.
- Long-press → favourite / share / open on map.

### Spot detail

A scrolling screen, **not** tabs-within-tabs as on the web:

1. **Header**: photo (`spotPhotoUrl`) with collapsing toolbar, name, country, ★.
2. **Now card**: big wind number, gusts, direction arrow, temp, station time;
   12 h history sparkline from `currentConditionsHistory`. Hidden when the spot has
   no station.
3. **Hourly strip**: horizontally scrolling hours (wind, gusts, arrow, temp, rain),
   day headers pinned. Sourced from `forecastHourly` / `/forecast/{wgId}`.
4. **Wind chart**: Compose `Canvas` — gust area + wind bars + direction arrows +
   temperature line, pinch-to-zoom horizontally.
5. **5-day summary**: one row per day.
6. **Model selector**: a chip row from `availableModels` (GFS / IFS / ICM …),
   not a desktop dropdown.
7. **Spot info**: type, best wind, water temp, experience, launch, hazards, season,
   description — an expandable card, language-aware (`spotInfo` / `spotInfoPL`).
8. **AI analysis / ICM**: generate buttons behind a confirmation dialog (each press
   costs a model call — same bargain as the web app), with the "generated at"
   timestamp rendered in the device's zone.
9. **Links row**: Windguru, Windfinder, ICM, webcam, directions — all
   `CustomTabsIntent` or `ACTION_VIEW`.

### Mobile-native affordances to use deliberately

- Pull-to-refresh everywhere (replaces the web's silent 60 s poll).
- Haptics on favourite toggle and on crossing a filter threshold.
- `ACTION_SEND` share sheet → deep link `https://varun.surf/spot/{id}` (the
  `SeoController` already renders that page, so shares work for non-users).
- App Links: `https://varun.surf/spot/{id}` and `/country/{name}` open the app when
  installed, given an `assetlinks.json` on the server (small backend task).
- Predictive back, edge-to-edge, dynamic colour (opt-out — the wind scale must stay
  canonical).
- Per-app language (`AndroidManifest` `localeConfig`) for the EN/PL switch instead
  of an in-app toggle, with the in-app toggle kept for parity.

---

## 6. Stage plan

Each stage is independently shippable and ends in a testable state. Rough sizing
assumes one developer working part-time; treat them as ordering, not commitments.

### Stage 0 — Contract & prerequisites (backend side, ~half a day)

- [x] Add `GET /api/v1/session` (issues the SESSION cookie, 204). **Done 2026-09-22**
      — `SessionController` + filter change + tests + docs.
- [ ] Serve `/.well-known/assetlinks.json` for App Links (needs the release
      signing cert SHA-256, so can land later — keep it on the list).
- [ ] Confirm gzip is on for `/api/v1/**` at nginx (it is on the wire today) and
      review rate limits against the app's refresh cadence.
- [ ] Optional but valuable: `ETag`/`If-None-Match` on `/api/v1/spots`. At 537 KB
      gzip a 304 is worth real money on mobile data.
- [ ] Optional: `GET /api/v1/spots?fields=summary` returning the list without
      `spotInfo`/`spotInfoPL` (the two largest fields the list screen never reads).
      Would cut the list payload roughly in half.
- [ ] Write `docs/API.md` describing the contract the app depends on, including
      the date-format quirks in §2.2.

### Stage 1 — Scaffold (~2–3 days)

- [ ] `varun.surf-android` repo, AGP + Kotlin + version catalog, module skeleton
      from §4, Hilt wired, Compose BOM.
- [ ] Design system module: wind colour scale (light + dark), typography, spacing,
      `WindText`, `DirectionArrow`, `WindPill`, `StaleBadge` primitives.
- [ ] Network module: Retrofit service for all endpoints in §2, kotlinx DTOs,
      DTO→domain mappers, persistent `CookieJar`, session bootstrap interceptor
      with single-flight 401 recovery, OkHttp logging in debug.
- [ ] MockWebServer-backed tests for the session dance (401 → bootstrap → replay)
      and for every DTO against captured real payloads (check the fixtures in).
- [ ] CI: GitHub Actions — assemble, unit test, ktlint, detekt, lint.
- [ ] A throwaway screen listing spot names, to prove the stack end-to-end.

**Exit**: `./gradlew build` green in CI; the app shows 790 spot names from
production.

### Stage 2 — Spots list + detail, online-only (~1 week)

- [ ] Spots list with sections (favourites, all), search, country filter chip,
      live-only and "windy now" chips.
- [ ] Sorting: alphabetical, "firing now" (port `firingScore` from `index.js`),
      country.
- [ ] Favourites in DataStore, keyed by `wgId` (the web app keys by name — key by
      `wgId` and migrate on import; names change, ids do not).
- [ ] Spot detail per §5, minus chart and AI/ICM: now card, hourly strip, 5-day,
      info, links.
- [ ] Error/empty/loading states, including the "forecast sweep in progress" case
      from `/api/v1/status/forecast`.
- [ ] Localisation: EN + PL string resources; port the daily-label and
      day/month-name translations from `translations.js`.

**Exit**: a usable app for someone with signal.

### Stage 3 — Offline-first (~4–5 days)

- [ ] Room schema: `spots`, `daily_forecast`, `hourly_forecast`, `live_conditions`,
      `live_history`, `wind_timeline`, with `fetchedAt` on each.
- [ ] Repositories emit `Flow` from Room, refresh from network, expose a
      `SyncState` (idle / syncing / error / stale-since).
- [ ] Freshness policy mirroring the backend's own: live 1 min, forecast 3 h,
      spot list 3 h; never refetch the 537 KB list on a screen rotation.
- [ ] WorkManager periodic sync (default 30 min, unmetered-preferred, battery-aware)
      that refreshes favourites' live conditions and the daily forecasts.
- [ ] Explicit "last updated X ago / offline" chip in the top bar.

**Exit**: airplane mode still shows yesterday's forecast, honestly labelled.

### Stage 4 — Map (~1 week)

- [ ] Map screen with OSM + Esri satellite layers (same sources as the web app's
      `TILE_CONFIGS`, attribution included — this is a licensing requirement, not a
      nicety).
- [ ] Marker clustering with wind-coloured bubbles; port the bubble sizing and
      average-wind colouring rules from `map.js`.
- [ ] Spot bottom sheet on tap: live readout, 5-day mini forecast, "Open spot".
- [ ] Forecast time slider over `GET /wind?hours=120` (75 KB — cheap enough to
      fetch on entering the screen and cache for 3 h); markers recolour as it steps.
- [ ] "Near me" — location permission (`ACCESS_COARSE_LOCATION`, requested in
      context with a rationale), sort/filter by distance. **This is a native-only
      feature worth more than the wind field overlay.**
- [ ] Wind field overlay (heatmap/particles) — explicitly deferred; see §8 risks.

**Exit**: the map answers "where is it blowing near me" in two taps.

### Stage 5 — Charts, AI analysis, ICM (~4–5 days)

- [ ] Compose `Canvas` wind chart with gusts, direction and temperature.
- [ ] `POST /analysis` and `POST /icm` behind confirmation dialogs, with in-flight
      state, 503 handling, cached-result detection and generated-at timestamps.
- [ ] ICM appearing in the model chip row once read; model switching via
      `/spots/{id}/{model}`.
- [ ] Kite/board size calculator (port `calculator.js` — pure logic, trivial to
      port, and it is the kind of thing people open offline).

### Stage 6 — Widgets & alerts (~1 week) — *the reason to have an app*

- [ ] Glance widgets: (a) single favourite spot, live wind + 5-day strip;
      (b) favourites list, compact. Sizes: 2×1, 4×1, 4×2. Tap → spot detail.
- [ ] Widget refresh through the same WorkManager sync, not its own poller.
- [ ] Alert rules: per spot, "wind ≥ X kt", optional direction set, optional
      day-hours window, optional lead time ("tell me the evening before").
- [ ] Evaluation in a WorkManager job against the cached hourly forecast; local
      notification with a deep link. **No server push in v1** — local evaluation
      needs no backend work and no user accounts. Revisit FCM only if battery or
      timeliness measurements demand it.
- [ ] Notification channels (per severity), quiet hours, and a hard cap of one
      notification per spot per forecast run so nobody gets spammed.

### Stage 7 — Polish & release (~1 week)

- [ ] Baseline profile + Macrobenchmark (list scroll, map open, cold start).
- [ ] Screenshot tests over the wind scale in both themes and both languages.
- [ ] Accessibility pass: TalkBack labels for wind arrows and colour-coded values
      (colour alone must never carry the wind band — add the number and an arrow),
      touch targets, font scaling to 200%.
- [ ] R8 rules, crash reporting (Play Console vitals is enough to start),
      privacy policy + Play data-safety form (location + no accounts + no ads).
- [ ] App Links verification once the release cert exists; `assetlinks.json` shipped.
- [ ] Internal testing track → closed → production.

### Stage 8 — Later, if the app finds users

- Wear OS tile/complication with live wind at the nearest favourite.
- Android Auto? No — a wind app while driving is a bad idea; a widget covers it.
- Home-screen shortcuts per favourite spot.
- Session/logbook: record a session, tie it to the conditions that day.
- Community reports ("what it's actually like right now") — needs backend + moderation,
  a product decision, not a technical one.
- Offline map tiles for a chosen region.

---

## 7. Map library decision

| Option | For | Against |
|---|---|---|
| **osmdroid** | Drop-in for the exact tile URLs the web app uses; no key; simplest path to visual parity | Raster only; dated gesture feel; clustering is DIY or via `osmdroid-bonuspack`; not Compose-native |
| **MapLibre GL Android** | Vector tiles, smooth zoom, real style control, can render the wind overlay as a proper layer, no vendor lock-in | Needs a vector tile source (MapTiler free tier or self-hosted); Esri satellite still comes in as a raster layer; steeper learning curve |
| **Google Maps Compose** | Best-in-class gestures, `MarkerClustering` out of the box, first-class Compose API | API key + billing; visually diverges from the web app; another Google dependency |

**Recommendation**: start with **osmdroid** in Stage 4 to get the screen shipped
against the tile sources already in use, and treat MapLibre as a Stage 8 upgrade
*if and only if* the wind-field overlay gets built — that overlay is the one
feature that genuinely needs a vector/GL pipeline. Do not start with Google Maps;
switching away later is harder than switching from osmdroid.

---

## 8. Risks and open questions

| Risk | Mitigation |
|---|---|
| **Session cookie contract is undocumented and could change** | Stage 0 endpoint + a contract test in the *backend* repo asserting the app's bootstrap path keeps working |
| **537 KB list fetch on mobile data** | Room caching (Stage 3) + ETag (Stage 0) + optional `?fields=summary`; never fetch it on the Now tab |
| **nginx rate limits vs. many installs** | Measure before release; widen the app's intervals rather than the server's limits |
| **Wind field overlay** (heatmap + particles) is ~1000 lines of canvas maths in `map.js` | Explicitly out of v1. It is the highest-effort, lowest-value item for a phone screen and drains battery. Revisit with MapLibre only. |
| **Only 15 of 790 spots have live stations** | Do not build the UI around live data; make the "Now" tab honest about how few stations exist, and lead with forecasts |
| **AI/ICM buttons cost real money per press** | Same confirmation dialog as the web app, plus client-side knowledge of the 24 h cache so the button reads "Show" rather than "Generate" when a fresh result already exists |
| **Play Store policy: location + notifications** | Request in context, explain in the listing, keep both optional — the app must be fully usable with neither granted |
| **Frontend and app drifting apart** | Port the wind colour scale, thresholds and `firingScore` as a single documented table in `core/designsystem`, and cross-link it from `docs/FRONTEND.md` |

### Open questions for the owner

1. **Separate repo or monorepo?** (Recommendation: separate — §3.)
2. **Package name / Play account** — `surf.varun.android`? Is there an existing
   Play developer account?
3. **Units**: knots only (as the web app), or add m/s and km/h for the German and
   Polish inland lake crowd? (Cheap to add in Stage 2; expensive to retrofit into
   widgets and alerts later.)
4. **iOS later?** If yes, Stage 1 should put the domain + repository layer in KMP
   from day one rather than retrofitting it. This meaningfully changes Stage 1.
5. **Is a backend change acceptable at all** (Stage 0), or must the app work
   against the API exactly as it stands today?

---

## 9. Suggested first commit

```
varun.surf-android/
├── settings.gradle.kts            # includes app, core:*, feature:*
├── gradle/libs.versions.toml      # AGP, Kotlin, Compose BOM, Hilt, Retrofit, Room, Coil
├── app/src/main/kotlin/surf/varun/VarunApplication.kt
├── core/network/…/VarunApi.kt     # the 8 endpoints from §2
├── core/network/…/SessionInterceptor.kt
├── core/model/…/Spot.kt
└── .github/workflows/ci.yml
```

Nothing in that commit needs the backend to change — Stage 0 only removes friction.
