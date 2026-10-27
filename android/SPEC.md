# varun.surf for Android - Product & Technical Specification

Status: draft v1 (2026-10-23)
Scope: a native Android client for the existing varun.surf backend. This document
describes **what** to build and **how**; it contains no implementation.

---

## 1. Goals and non-goals

### Goals

- Give kitesurfers everything varun.surf offers on the web - ~820 spots in 59
  countries, live wind from 14 station integrations, multi-model forecasts, the wind
  map, AI analysis, ICM forecast, kite calculator - in a UI built for a phone held in
  one hand on a beach.
- Answer the two questions a rider actually asks, fast:
  1. **"Is it blowing now at my spots?"** - favourites with live wind, at a glance,
     ideally without opening the app (widget, notification).
  2. **"When and where will it blow?"** - forecast per spot and across spots.
- Feel native: Material 3, edge-to-edge, predictive back, dynamic colour, haptics,
  deep links, widgets, notifications, share sheet.
- Work on a poor beach connection: show cached data instantly, refresh in the
  background, degrade gracefully offline.
- Reuse the existing public API as-is. Every backend change proposed here is
  optional and listed separately (section 12).

### Non-goals

- No business logic duplication: the app never scrapes Windguru or weather
  stations itself, never talks to OpenAI. The backend stays the single source of
  truth.
- No user accounts, no server-side favourites sync in v1 (the web has none either).
- No tablet-first or desktop-class layouts (they must work, see 9.6, but are not the
  design target).
- No iOS. No Kotlin Multiplatform in v1 (the architecture should not block it later,
  see 7.2).

---

## 2. Feature mapping: web → Android

Legend: **Keep** = same feature, native UI. **Adapt** = same intent, different
shape. **Drop** = not built, with the reason. **New** = mobile-only.

### 2.1 Main page (spots list)

| Web feature | Android | Notes |
|---|---|---|
| Grid of spot cards with live wind + daily forecast | **Keep** | `LazyColumn` of compact cards (section 5.2) |
| Search (`/` shortcut, header search) | **Adapt** | Material 3 `SearchBar` with expanded results; no keyboard shortcut |
| Country filter | **Keep** | Bottom sheet with flags + spot counts, searchable |
| Favourites | **Keep** | Star on card / spot screen; own tab |
| Custom ordering with drag & drop | **Adapt** | Long-press reorder only in the Favourites tab (the only list a user curates) |
| "Firing now" sort (by live wind) | **Keep** | Default sort; sort chip menu: Firing now / Name / Windiest today / Distance (new) |
| Live-stations-only filter | **Keep** | Filter chip |
| 2-column / 3-column toggle | **Drop** | Desktop layout concern. Replaced by a density setting (comfortable / compact) |
| List view with sortable columns | **Drop** | Tabular layout doesn't fit a phone; sort chips cover it |
| Hero section (random spot photo + slogan) | **Adapt** | Collapsing top app bar with a random spot photo on the Spots tab; can be turned off in settings. No "close behind a confirmation modal" flow |
| Forecast sweep progress ("forecasts loading N/827") | **Keep** | Thin progress banner under the top bar while `/api/v1/status/forecast` reports `inProgress` |
| Stale live data indicator (yellow) | **Keep** | Same rule, rendered as a tinted timestamp chip |
| Sponsors | **Keep** | Small, unobtrusive row at the end of the list and on the About screen |
| Theme toggle (dark/light) | **Adapt** | System / Light / Dark in settings, plus Material You dynamic colour |
| Language (EN/PL, auto-detected) | **Adapt** | Android per-app language (`LocaleManager`, Android 13+ system picker; in-app picker below) |
| Sidebar navigation / mobile drawer | **Adapt** | Bottom navigation bar (section 5.1) |
| About modal with running version | **Keep** | About screen; app version + backend version from `/api/v1/status` |

### 2.2 Single spot page

| Web feature | Android | Notes |
|---|---|---|
| Spot header, country, links | **Keep** | Large collapsing header with spot photo |
| Live conditions + 12 h history | **Keep** | Live card + 12 h sparkline chart (wind/gust band, direction arrows), switchable to a table |
| Daily forecast | **Keep** | Horizontally scrolling day chips that also act as day selector for the hourly view |
| Hourly forecast (Windguru-style table) | **Adapt** | Hourly timeline: a horizontally scrollable, sticky-label table on the phone, with a chart toggle (section 5.3) |
| "Windy days only" filter | **Keep** | Toggle in the forecast toolbar |
| Forecast model dropdown (GFS, IFS, ... 40+, ICM) | **Keep** | Model selector chip → bottom sheet listing `availableModels` |
| AI analysis button + confirmation modal | **Keep** | Same flow, confirmation as a `ModalBottomSheet` / dialog (section 6.5) |
| ICM forecast button + confirmation modal | **Drop** | Removed from the web too: the server fetches ICM with the other models, for free |
| AI analysis EN/PL + generation time | **Keep** | Shown in app language; "generated at HH:mm" in device time zone |
| Spot info (type, best wind, water temp, hazards, season, description) | **Keep** | Info section with icons; follows app language (`spotInfo` / `spotInfoPL`) |
| Embedded map | **Adapt** | Static mini map (MapLibre `MapSnapshotter` image, cached) + "Navigate" button opening any maps app via `geo:` intent |
| Links (Windguru, Windfinder, ICM, webcam) | **Keep** | Opened in Custom Tabs |
| Webcam link | **Keep** | Custom Tab; if the URL is a direct image/stream, an inline preview (optional, v1.1) |
| Header search on spot page | **Drop** | The search is a global destination on Android |
| Side peek on the map | **Drop** | Desktop pattern; replaced by the map's bottom sheet (2.3) |

### 2.3 Wind map

| Web feature | Android | Notes |
|---|---|---|
| Map with clustered spot markers coloured by wind | **Keep** | MapLibre map with GeoJSON-source clustering (section 5.4) |
| Wind field overlay (heatmap + animated particles) | **Keep** | Custom GPU/Canvas overlay, on/off toggle (section 6.4). Particle density lowered on low-end devices and when battery saver is on |
| Spot visibility toggle | **Keep** | |
| Hourly forecast timeline slider | **Keep** | Bottom slider; phone default reach 120 h (same as web phone), "full run" option in settings |
| Popups with wind for selected hour | **Adapt** | Tap a marker → peekable `BottomSheetScaffold` with the spot summary for the selected hour; expand → full spot screen |
| Spot side peek (desktop) | **Drop** | See above |
| Globe view (/globe) | **Build** | A switch on the map tab; Canvas port of `common/globe.js` (see README deviations) |

### 2.4 Other pages

| Web page | Android | Notes |
|---|---|---|
| Kite & board size calculator | **Keep** | Own screen reachable from the More tab and prefilled from a spot ("calculate for this wind") |
| Status page (uptime, counts) | **Adapt** | Compact "Service status" section in About: backend version, uptime, spots/countries/live stations, forecast sweep progress |
| Sources page | **Adapt** | Simple list in About ("Data sources") linking out; health pings are not reproduced |
| MCP server page | **Drop** | Developer-facing, irrelevant inside a phone app |
| Metrics dashboard | **Drop** | Operator tool |
| Logs dashboard (password-protected) | **Drop** | Operator tool; must never ship a password in an APK |
| TV view (/tv) | **Drop** | Big-screen kiosk. Android TV is out of scope |
| Embed widget generator (/embed) | **Drop / replaced** | The Android equivalent is the home-screen widget (2.5) |
| SEO pages, sitemap | **Drop** | Web-only; but the app handles their URLs as deep links (6.7) |
| `/llms/*.md` | **Drop** | Not needed by the app |

### 2.5 New, mobile-only features

1. **Home-screen widgets** (Jetpack Glance) - section 6.8:
   - *Spot widget* (1 spot): live wind, gusts, direction arrow, temp, age of reading;
     larger sizes add the next 12 h forecast strip.
   - *Favourites widget* (list, resizable): favourites sorted by "firing now".
2. **Wind alerts** - "notify me when spot X reaches ≥ N kts from directions D within
   the next H hours / right now (live)". Evaluated on device by a periodic worker
   (section 6.6). The web cannot do this; it is the single most useful mobile addition.
3. **Nearby spots** - optional location permission: sort/filter by distance, "spots
   within 50 km" chip, distance on cards. Coordinates already exist in the API.
4. **Navigate to spot** - one tap to Google Maps / Waze / any `geo:` handler.
5. **Share spot** - Android share sheet with the `https://varun.surf/spot/{id}` link
   (and a short text "Jastarnia: 18 kts SW, gusts 23 - varun.surf"). The link opens the
   app if installed (App Links) or the web otherwise.
6. **Deep links / App Links** for `varun.surf/spot/{id}` and
   `varun.surf/country/{name}`.
7. **App shortcuts** (long-press launcher icon): up to 4 dynamic shortcuts to the most
   recently opened / favourite spots, plus static "Wind map" and "Search".
8. **Offline-first cache** - last known spots list, favourites' detail and forecasts
   are stored locally and shown immediately with an "updated X min ago" label.
9. **Pull-to-refresh** everywhere data is shown.
10. **Compass-relative wind** (spot screen, optional, v1.1): when the user is within
    ~2 km of the spot, a small compass widget shows the wind direction relative to
    where the phone points (rotation vector sensor). Useful for judging on/off/side-
    shore at the launch.
11. **Units** - knots (default, as web), m/s, km/h, Beaufort; °C/°F. The web is knots-
    only, but phones are personal devices and a setting is cheap. All thresholds stay
    defined in knots internally.
12. **Haptics** on the timeline slider (tick per hour, stronger tick at midnight) and
    on favourite toggle.
13. **Quick Settings tile** (v1.1): shows the live wind at the first favourite; tap opens
    the spot.

### 2.6 Explicitly considered and rejected for v1

- Wear OS tile/complication - good fit, but separate module and design pass; v2.
- Android Auto - no meaningful use case.
- Live wallpaper with the wind field - cute, no value.
- FCM push - requires backend changes and a Firebase project; on-device polling
  covers alerts for v1 (see 12 for the server-side option).

---

## 3. Backend API used by the app

Base URL: `https://varun.surf` (configurable per build type: `debug` may point at
`http://10.0.2.2:8080`).

| Endpoint | Method | Used for | Notes |
|---|---|---|---|
| `/api/v1/session` | GET | Obtain the `SESSION` cookie | 204 + `Set-Cookie`. Called on first launch and on any 401 |
| `/api/v1/spots` | GET | Spots list, map markers, search index | Stripped of `forecastHourly` and `currentConditionsHistory`. Large (~820 spots) - cache it |
| `/api/v1/spots/{wgId}` | GET | Spot screen (GFS) | Includes `forecastHourly`, `currentConditionsHistory`, `availableModels`. Triggers server-side model discovery |
| `/api/v1/spots/{wgId}/{model}` | GET | Spot screen with selected model | `model` = `AvailableModel.key` (e.g. `gfs`, `ifs`, `icm`, ...) |
| `/api/v1/forecast/{wgId}` | GET | Grid-aligned hourly forecast (wind, temp, rain, cloud, pressure, waves) | 404 for unknown spot. Used by widgets and alerts worker (smaller than full spot) |
| `/api/v1/wind?hours=N` | GET | Map timeline + wind field | Default 120 h, max 16 days. ~100 KB gzipped |
| `/api/v1/spots/{wgId}/analysis?lang=en\|pl` | POST | Generate AI analysis (both languages) | 404 unknown, 503 failed. Costs money - confirmation required |
| `/api/v1/spots/{wgId}/icm` | POST | Not used | Fetches ICM by name; opening a spot fetches it anyway |
| `/api/v1/sponsors` | GET | Sponsors row | |
| `/api/v1/status` | GET | About → service status, backend version | |
| `/api/v1/status/forecast` | GET | "Forecasts are being refreshed" banner | Poll every 5 s only while visible and `inProgress` |
| `/api/v1/health` | GET | Connectivity check (no cookie) | Optional |

### 3.1 Session handling

- The API is gated by a stateless signed `SESSION` cookie (HMAC, 24 h lifetime,
  re-issued past its half-life on page visits).
- The app uses an OkHttp `CookieJar` persisted in DataStore (or encrypted prefs) so the
  cookie survives process death.
- An interceptor handles `401`: call `GET /api/v1/session` once (single-flight with a
  `Mutex`), store the cookie, retry the original request once. A second 401 surfaces
  as an error.
- Proactive refresh: if the stored cookie's expiry (the `<epoch>.` prefix of its value,
  or `Max-Age`) is less than 12 h away, call `/api/v1/session` before the next batch of
  requests. Never parse/verify the HMAC on the client.
- Background workers (widgets, alerts) go through the same client and the same logic.

### 3.2 Data contracts (as served today)

```text
Spot {
  wgId: Int                       // primary key everywhere
  name, country: String
  windguruUrl, windguruFallbackUrl?, windfinderUrl, icmUrl, webcamUrl, locationUrl: String ("" = absent)
  currentConditions?: CurrentConditions        // omitted when empty
  currentConditionsHistory?: List<CurrentConditions>   // single spot only, 12 h, ~1/min
  forecast: List<Forecast>        // daily; date = "Today", "Tomorrow", then weekday labels
  forecastHourly?: List<Forecast> // single spot only; date = "Fri 23 Oct 2026 14:00"
  aiAnalysisEn?, aiAnalysisPl?: String
  aiAnalysisEnCreatedAt?, aiAnalysisPlCreatedAt?: String  // ISO-8601 instant
  spotPhotoUrl?: String           // relative, e.g. "/images/spots/500760.jpg?v=47b8f173"
  coordinates?: { lat, lon }
  spotInfo, spotInfoPL: SpotInfo  // type, bestWind, waterTemp, experience, launch, hazards, season, description
  sponsors?: List<Sponsor>
  availableModels?: List<{ key, name }>
  lastUpdated: String             // "2026-10-23 12:01:44 UTC"
}

CurrentConditions { date: "23/10/26 14:00:10", wind: Int, gusts: Int, direction: "SW", temp: Int }

Forecast {
  date: String, wind, gusts: Double, direction: String, temp, precipitation,
  cloudCoverPercent, pressureHpa: Double, wave?, wavePeriod?: Double, waveDirection?: String
}

HourlyForecast { wgId: Int, hours: List<Forecast> }      // missing hours are absent, not zero

WindTimeline {
  hours: List<String>                                      // shared grid, same format as Forecast.date
  spots: List<{ wgId, wind: List<Int?>, gusts: List<Int?>, direction: List<Int?> }>
}                                                          // direction = index into [N,NE,E,SE,S,SW,W,NW]

ForecastFetchProgress { inProgress, total, completed, fetched, empty, failed, cached, startedAt, finishedAt, elapsedMs }
Status { status, version, uptime, uptimeSeconds, startTime, spotsCount, countriesCount, liveStations }
Sponsor { id, main, name, link }
```

Parsing rules the client must implement (and unit-test):

- **All fields are optional on the client**, unknown fields ignored
  (`ignoreUnknownKeys = true`, `explicitNulls = false`, defaults everywhere). The
  backend evolves independently of app releases.
- Empty strings for URLs mean "absent".
- `Forecast.date` for hourly data is a local wall-clock string without zone. Interpret
  it in the **spot's** time zone - see open question Q1. `CurrentConditions.date` is
  `dd/MM/yy HH:mm:ss` in the station's local time.
- Daily `date` labels ("Today", "Tomorrow", weekday names) are English tokens from the
  server; map them to localized labels on the client.
- `spotPhotoUrl` is relative - resolve against the base URL. It carries a content hash
  (`?v=`), so it can be cached forever.

---

## 4. Domain rules (must match the web)

These live in a pure-Kotlin `:core:domain` module with unit tests that mirror the
web's behaviour.

### 4.1 Wind classes (knots)

| Class | Range | Use |
|---|---|---|
| calm | < 5 | map only |
| light | 5 - 11.99 | map only (list uses "weak" for < 12) |
| weak | < 12 | cards, forecast cells |
| moderate | 12 - 17.99 | |
| strong | 18 - 25 | |
| extreme | > 25 | |

Colours come from the design system (section 9.2) but each class must also be
distinguishable without colour (weight / icon / label) for accessibility.

### 4.2 "Firing now" score

Average of live `wind` and `gusts`; spots without live data fall back to the forecast
entry closest to now (hourly if present, else today's daily entry); spots with neither
sort last. Same as `firingScore()` in `src/frontend/js/page/index.js`.

### 4.3 Stale live conditions

A live reading older than the web's threshold is shown as stale (yellow on web). Use
the same threshold (read it from `src/frontend/js/common/weather.js` when
implementing; do not invent a new one).

### 4.4 Kite & board calculator

Port `src/frontend/js/common/calculator.js` 1:1:

- Kite size = `weightKg × factor / windKts`, factor by skill/conditions
  (beginner-flat 3.0, beginner-small 2.8, intermediate-flat 2.5, intermediate-medium
  2.3, advanced-flat 2.2, advanced-medium 2.0, advanced-large 1.8), rounded to the
  nearest of 7, 9, 10, 12, 14, 15, 17 m².
- Board size rules (twin tip 136-160 cm with width, or directional by weight) and the
  input validation warnings (wind 5-50 kts etc.) exactly as on the web.
- Golden test cases generated from the JS implementation and asserted in Kotlin.

### 4.5 AI analysis language

Show `aiAnalysisPl` when the app language is Polish, else `aiAnalysisEn`; if the
chosen one is missing but the other exists, show it with a small "in English / po
polsku" label rather than nothing.

### 4.6 Windy days filter

Same definition as the web's "windy days" toggle on the spot page.

---

## 5. Information architecture and screens

### 5.1 Navigation

Bottom navigation bar (navigation rail on width ≥ 600 dp) with four destinations:

1. **Spots** - all spots (list), country filter, search, sort/filter chips.
2. **Favourites** - the user's spots, reorderable; the default start destination once
   the user has at least one favourite.
3. **Map** - wind map with timeline.
4. **More** - Calculator, Wind alerts, Settings, About (status, sources, sponsors,
   licences, privacy).

Spot detail and search are pushed on top of any tab (tab back-stacks are preserved,
predictive back supported). Deep links land on Spot detail with the Spots tab beneath.

### 5.2 Spots list (tab 1) and Favourites (tab 2)

- Collapsing top bar: hero photo (optional) → condenses to a search bar.
- Chips row (horizontally scrollable): country (opens sheet), sort, "Live only",
  "Nearby" (if location granted), "Windy today".
- Card (compact, ~88 dp high):
  - left: name, country flag + name, distance (optional)
  - centre: live wind `18 kts` large, gusts `/ 23`, direction arrow rotated to the
    wind, temperature; timestamp chip ("3 min ago", tinted when stale); if no live
    station, the forecast for "now" labelled "forecast"
  - right: favourite star
  - bottom strip: next 3 days (day label, avg wind coloured by class, direction arrow)
- Tap → spot detail with a shared-element transition on the name and photo.
- Swipe on a card: none (avoid accidental actions). Long-press → context menu:
  favourite, share, navigate, create alert.
- Empty states: no favourites ("Tap ☆ on any spot"), no search results, offline
  without cache, forecasts still being swept (banner, section 2.1).

### 5.3 Spot detail

Single scrolling screen, `LargeTopAppBar` with the spot photo collapsing into the
title. Actions: favourite, share, overflow (open on web, create alert, navigate).

Sections in order:

1. **Now** - live wind card (wind, gusts, direction arrow + cardinal, temp, reading
   age, source). Below: 12 h history chart (wind line, gust band, direction ticks), with a switch to a table of the readings (newest first); the chart is the default.
   Hidden if the spot has no live station.
2. **Forecast** - toolbar: model chip (`GFS 13 km ▾`), "windy days" toggle, table/chart
   toggle.
   - Day selector: horizontally scrolling daily chips (from `forecast`), selecting one
     scrolls the hourly view to that day.
   - Hourly **table** (default, mirrors the Windguru-style table the web shows): rows =
     variables (time, wind, gusts, direction arrow, temp, rain, cloud, pressure, wave
     height/period/direction when present), columns = hours; first column sticky; cells
     coloured by wind class; day separators.
   - Hourly **chart** alternative: wind/gust lines with direction arrows along the
     x-axis, rain bars below.
   - Changing model calls `/api/v1/spots/{wgId}/{model}`; the last chosen model is
     remembered per app session (web uses sessionStorage) and optionally globally.
3. **AI analysis** (only when the feature is enabled server-side, i.e. the endpoint
   doesn't return 503 "feature off" - see Q3):
   - If present: text + "Generated 14:32" in device time zone.
   - If absent: "Generate AI analysis" button → confirmation (6.5) → progress (can take
     ~15-30 s) → result. Error 503 → "Couldn't generate the analysis, try later".
4. **ICM forecast** - no section of its own: for Polish and Czech spots it arrives in the
   model selector with the other models.
5. **About this spot** - `spotInfo`/`spotInfoPL` fields with icons; description.
6. **Location** - static map thumbnail, coordinates (copyable), "Navigate" button.
7. **Links** - Windguru, Windfinder, webcam (Custom Tabs).
8. **Sponsors** of the spot, if any.
9. Footer: "Data updated 12:01 UTC" (`lastUpdated`).

Floating "Kite size" action (or overflow item) opens the calculator prefilled with the
live wind (or the selected hour's forecast).

### 5.4 Map (tab 3)

- Full-screen map, top: search field + filter chips (country, favourites only, live
  only); right-side FABs: my location, layers (wind field on/off, markers on/off,
  satellite/light), fit to favourites.
- The map's search button opens its own sheet over the spots the map shows; a pick
  flies the camera to the spot and opens its panel instead of leaving for the spot page
- Rendering: MapLibre Native via `maplibre-compose`. Spots are one GeoJSON source
  (feature per spot with `wgId`, `wind`, `gusts`, `dirIndex`, `windClass` properties for
  the selected hour) and are drawn with style layers, not per-marker views - so ~820
  spots stay cheap and a timeline step only swaps the source data.
- Markers: circle layer coloured by wind class (data-driven `match` on `windClass`) with
  a symbol layer for the wind number and a rotated arrow icon (`icon-rotate` from
  `dirIndex × 45`). Clusters come from the source's built-in clustering
  (`cluster: true`, `clusterProperties` aggregating `max` wind), showing count and the
  max wind inside; tap a cluster → zoom to its expansion zoom.
- Bottom: forecast timeline slider (hour label "Sat 14:00", day ticks, play/pause
  button that animates through the hours). "Now" snaps to live data where available.
- Tap marker → bottom sheet peek (name, wind for selected hour, live reading, mini
  3-day strip, buttons: open spot, favourite, navigate).
- Data: `/api/v1/spots` (positions, live) + `/api/v1/wind?hours=120` (timeline). Both
  cached; the timeline is refetched when older than 1 h or when the forecast sweep
  finished after it was fetched.
- Basemaps (no API key, no Google Play Services dependency for maps):
  - **Light / dark**: OpenFreeMap vector styles (`liberty` / `positron`, plus a dark
    style) - free, keyless, OSM data. The style follows the app theme.
  - **Satellite**: raster imagery from a provider that needs a key (MapTiler free tier
    is the default candidate). The key is injected at build time from CI secrets /
    `local.properties`; without it the satellite option is hidden.
  - Style URLs live in one config object so the provider can be swapped (or pointed at
    self-hosted tiles on the varun.surf server later) without code changes.
  - OSM / provider attribution is always visible (MapLibre attribution control), as the
    licences require.
- Offline: MapLibre's ambient tile cache is kept (sized ~100 MB); an explicit "download
  this area" offline region for favourites is a v1.1 option.

### 5.5 Search

Full-screen `SearchBar` results, local and instant (in-memory index over the cached
spots list): matches name, country (EN + PL names), with diacritics folded ("Leba" →
"Łeba"). Sections: recent searches, recently viewed spots, results. Also reachable
from the Spots top bar and from an app shortcut (the map searches in place, see 5.4).

### 5.6 Calculator

Inputs: wind (kts, slider + field), weight (kg), skill level & conditions (segmented /
dropdown with the 7 web options). Output cards: kite size, board size, warning text.
Inputs persisted (web persists them too).

### 5.7 Wind alerts

- List of alerts; create/edit sheet:
  - spot (search picker), min wind (kts, slider), optional max wind, optional direction
    sectors (8-way compass toggle - prefilled from the spot's `bestWind`),
  - trigger: **Live** (station reading ≥ threshold) or **Forecast** (any hour in the next
    N days within daylight hours meets the rule for ≥ K consecutive hours),
  - quiet hours, max one notification per alert per day (forecast) / per 3 h (live).
- Notification: "Jastarnia is firing - 21 kts SW (gusts 26)" / "Wind window Sat
  13:00-17:00 at Jastarnia, up to 22 kts". Tap → spot detail scrolled to that hour.
  Actions: "Mute today".
- Requires `POST_NOTIFICATIONS` (asked when the first alert is created, never on
  launch).

### 5.8 Settings

Theme (system/light/dark), dynamic colour on/off, language (system/EN/PL), units
(wind, temperature), card density, hero photo on/off, default start tab, map timeline
reach (5 days / full run), wind field particle quality (auto/low/high), location usage,
notifications, clear cache.

### 5.9 About

App version, backend version and uptime (`/api/v1/status`), spots / countries / live
stations counts, forecast sweep progress, data sources list (static, linking out),
sponsors, author, GitHub link, privacy policy, open-source licences.

---

## 6. Behaviour details

### 6.1 Data refresh policy

| Data | Freshness on server | App refresh |
|---|---|---|
| Spots list incl. live conditions | live: 1 min, forecast: 3 h | on app foreground if older than 1 min; every 60 s while the Spots/Favourites/Map screen is visible (same as web auto-refresh); pull-to-refresh |
| Spot detail | same | on open if cached copy older than 1 min; every 60 s while visible |
| Wind timeline | 3 h | on Map open if older than 1 h |
| Hourly forecast for widgets/alerts | 3 h | worker, every 1-3 h |
| Status / sweep progress | - | 5 s poll only while banner is visible |

All polling stops when the app is in the background (`repeatOnLifecycle(STARTED)`).

### 6.2 Offline and errors

- Repository pattern: UI observes Room via `Flow`; network refreshes write into Room.
  The UI is never blank when there is a cached copy.
- Every screen shows "Updated X ago" and an offline banner when the last refresh failed
  due to connectivity (`ConnectivityManager` callback).
- 5xx / timeouts: retry with exponential backoff (max 3) for GETs only; never
  auto-retry the two POSTs.
- 404 on a spot (e.g. removed from `spots.json`): show "This spot no longer exists" and
  offer to remove it from favourites/alerts.

### 6.3 Performance budgets

- Cold start to first spots list frame from cache: < 1 s on a mid-range device
  (Baseline Profiles + startup profile).
- Spots list scroll: no jank at 120 Hz on mid-range (stable keys, immutable UI models,
  `@Immutable` / strong skipping).
- `/api/v1/spots` parsing off the main thread, streaming decoder; parse + DB write
  < 300 ms for ~820 spots.
- Map with wind field: ≥ 50 fps on a mid-range device; auto-degrade particle count when
  frame time exceeds budget.
- APK (universal, R8) < 15 MB excluding map SDK native libs.

### 6.4 Wind field overlay

- Input: `WindTimeline` for the selected hour → scattered (lat, lon, u, v) samples.
- Interpolate to a screen-space grid (inverse distance weighting, same idea as the
  web's `map.js`), recomputed on camera idle and hour change on a background
  dispatcher.
- Render: heat colour wash (bitmap/shader) + particles advected along the field, drawn
  in a Compose `Canvas` overlay above the MapLibre view (or an AGSL `RuntimeShader` on
  API 33+), projected with the map's camera/projection on every frame so the field
  stays glued to the map while panning. The colour wash may alternatively be fed to
  MapLibre as an image source under the marker layers, so markers stay on top.
  Reach, particle speed/lifetime/density constants taken from `map.js` so the look
  matches the web.
- Off when battery saver is on unless the user forces it.

### 6.5 Paid operations (AI analysis)

Each press may cost an LLM call (the server caches for 24 h, so repeated presses are
free, but the client cannot know). Therefore:

- Always confirm via a dialog explaining "This generates a forecast summary with AI.
  It's shared with every visitor for 24 h." (wording from the web's modal, both
  languages).
- Button disabled while a request is in flight; one request per spot at a time
  (in-memory `Mutex` keyed by `wgId`) - the server also deduplicates.
- Client timeout for these POSTs: 90 s (they stream server-side).
- Survive configuration change and navigation away: run in an application-scoped
  coroutine; result is written to the spot cache, so it appears when the user returns.
- Never triggered from widgets, workers, or alerts.

### 6.6 Background work

- `WorkManager` periodic workers, constraint `NetworkType.CONNECTED`:
  - `AlertsWorker`: live alerts every 15 min (WorkManager minimum), forecast alerts every
    3 h. Fetches only the spots that have alerts: `/api/v1/spots/{wgId}` is heavy and
    triggers server-side model discovery, so for live checks use the cached
    `/api/v1/spots` list (one request for all) and for forecast checks
    `/api/v1/forecast/{wgId}`.
  - `WidgetRefreshWorker`: every 30 min (configurable), same data sources.
- Budget: at most one `/api/v1/spots` request per 15 min from background work across
  all workers (shared freshness check in the repository). Be a good citizen - the
  backend sits behind nginx rate limits.
- No foreground service, no exact alarms.

### 6.7 Deep links

- Verified App Links (`autoVerify`) for:
  - `https://varun.surf/spot/{id}` → Spot detail
  - `https://varun.surf/country/{name}` → Spots tab filtered by country
  - `https://varun.surf/map` (or the web map path) → Map tab
- Requires `/.well-known/assetlinks.json` on the server (section 12).
- Custom scheme `varun://spot/{id}` for widgets/notifications/shortcuts (internal).

### 6.8 Widgets (Glance)

- Spot widget: sizes 2×1 (wind number + arrow), 3×2 (+ gusts, temp, age), 4×2 (+ 12 h
  forecast strip). Configuration activity picks the spot (defaults to first favourite).
- Favourites widget: 3×3 and larger, `LazyColumn` of favourites sorted by firing score.
- Colours: dynamic colour on API 31+, wind class colours for numbers.
- Tap → spot detail; refresh button → one-off work request.

### 6.9 Localization

- EN and PL from day one; all strings in `strings.xml` (no hard-coded copy). Port the
  relevant keys from `src/frontend/js/common/translations.js` so wording matches.
- Country names localized (server sends English names; map them).
- Dates/times via `java.time` + device locale; 24 h vs 12 h per system setting.
- Plurals via `plurals` resources (Polish has 3 forms).

### 6.10 Privacy

- No analytics SDKs, no ads, no Firebase in v1.
- Location used only on device for distance; never sent to the backend.
- Crash reporting: none by default; consider an opt-in, self-hosted option later.
- Data safety form: "no data collected" (the session cookie is functional, not
  tracking).

---

## 7. Technical architecture

### 7.1 Stack (all latest stable at project start)

| Area | Choice |
|---|---|
| Application ID | `surf.varun.app` (reverse of the owned domain; permanent once uploaded to Play) |
| Language | Kotlin 2.2+ (K2 compiler), coroutines + Flow |
| Build | Gradle (Kotlin DSL), AGP 9.x, version catalog (`libs.versions.toml`), convention plugins in `build-logic/` |
| SDK levels | `minSdk 28` (Android 9), `targetSdk` / `compileSdk` = latest stable (36+) |
| UI | Jetpack Compose (BOM), Material 3 (incl. Expressive components where stable), adaptive layouts (`material3-adaptive`, `NavigationSuiteScaffold`) |
| Navigation | Navigation 3 (`androidx.navigation3`) with type-safe keys; fall back to Navigation Compose type-safe routes if Nav3 is not stable at kickoff |
| DI | Hilt (with `hilt-navigation-compose`); Koin acceptable if KMP becomes a goal |
| Networking | Retrofit 3 + OkHttp 5 + kotlinx.serialization converter (or Ktor client if KMP is prioritized). OkHttp `Cache` for HTTP-level caching |
| Serialization | kotlinx.serialization (`ignoreUnknownKeys`, `explicitNulls = false`, `coerceInputValues`) |
| Persistence | Room (KSP) for spots/forecasts/alerts; Proto or Preferences DataStore for settings, favourites order, cookie |
| Images | Coil 3 (OkHttp network fetcher sharing the app's client) |
| Maps | MapLibre Native Android via `maplibre-compose` (Compose bindings), OpenFreeMap vector tiles for light/dark, optional key-based satellite raster (section 5.4). Clustering via GeoJSON source clustering; no Google Maps SDK |
| Charts | Vico (Compose, M3) for wind history and forecast charts; the hourly table is custom Compose |
| Background | WorkManager (+ Hilt workers) |
| Widgets | Jetpack Glance (+ Glance M3) |
| Location | Platform `LocationManager` (coarse only), so the app has no hard Google Play Services dependency; Fused Location Provider can be added behind the same interface if accuracy/battery needs it |
| Browser | AndroidX Browser (Custom Tabs) |
| Splash | Core SplashScreen API |
| Logging | Timber (debug only) |
| Static analysis | ktlint (or Spotless + ktlint), detekt, Android Lint with `warningsAsErrors` on CI, Compose compiler metrics |
| Tests | JUnit 5 (via `android-junit5`) or JUnit 4 + kotlin.test, Truth (matches backend), MockK, Turbine, kotlinx-coroutines-test, MockWebServer (matches backend), Robolectric, Compose UI tests, Roborazzi screenshot tests, Maestro for E2E flows |
| Performance | Baseline Profile generator module (Macrobenchmark), R8 full mode |

### 7.2 Module structure

```
android/
├── SPEC.md                         # this document
├── settings.gradle.kts
├── gradle/libs.versions.toml
├── build-logic/                    # convention plugins (android-app, android-library, compose, hilt, test)
├── app/                            # Application, MainActivity, nav graph, DI root, App Links, shortcuts
├── core/
│   ├── model/                      # pure Kotlin domain models (Spot, Forecast, WindClass, Units...)
│   ├── domain/                     # pure Kotlin rules: wind classes, firing score, calculator, alert evaluation, date parsing
│   ├── network/                    # Retrofit API, DTOs, session cookie jar + 401 interceptor
│   ├── database/                   # Room entities, DAOs, migrations
│   ├── datastore/                  # settings, favourites order, cookie
│   ├── data/                       # repositories (offline-first), DTO→model mappers
│   ├── designsystem/               # theme, colours, typography, wind colour scale, shared components (WindArrow, WindBadge...)
│   ├── ui/                         # shared Compose UI (SpotCard, ForecastStrip, empty/error states)
│   └── testing/                    # fakes, fixtures (real JSON samples), test rules
├── feature/
│   ├── spots/                      # Spots + Favourites tabs, country sheet, sort/filter
│   ├── spot/                       # spot detail, model selector, AI/ICM flows
│   ├── map/                        # map, clustering, wind field overlay, timeline
│   ├── search/
│   ├── calculator/
│   ├── alerts/                     # alert editor + AlertsWorker
│   ├── settings/
│   └── about/
├── widget/                         # Glance widgets + WidgetRefreshWorker
└── benchmark/                      # baseline profile + macrobenchmarks
```

Rules: `feature:*` depend on `core:*` only, never on each other; navigation between
features goes through route keys defined in `app` (or a tiny `core:navigation`).
`core:model` and `core:domain` have no Android dependencies, so they are KMP-ready.

### 7.3 Layers and state

- **UI**: stateless composables + one `ViewModel` per screen exposing
  `StateFlow<UiState>` (sealed/immutable data classes) and accepting `UiEvent`s. Side
  effects (snackbars, navigation) via a `Channel`/one-shot effect flow.
  `collectAsStateWithLifecycle` everywhere.
- **Domain**: plain functions / small use-case classes only where logic is shared
  (e.g. `ObserveFavouritesWithLiveWindUseCase`, `EvaluateAlertUseCase`). No use-case
  boilerplate for 1:1 repository passthroughs.
- **Data**: repositories own the "cache then network" logic and expose `Flow`s from
  Room; network calls are `suspend`. Single source of truth = Room.

### 7.4 Local database (Room)

- `spot` (wgId PK, static info, coordinates, photo URL, links, spotInfo EN/PL as
  embedded JSON, lastUpdated, fetchedAt)
- `live_conditions` (wgId PK, wind, gusts, direction, temp, observedAt, fetchedAt)
- `live_history` (wgId, observedAt, wind, gusts, direction, temp) - only for opened
  spots, trimmed to 12 h
- `daily_forecast` (wgId, index, label, ...)
- `hourly_forecast` (wgId, model, time, ...) - only for opened/favourite spots, trimmed
  to the forecast horizon; keep at most N=30 spots (LRU by last opened)
- `ai_analysis` (wgId, lang, text, createdAt)
- `available_model` (wgId, key, name)
- `favourite` (wgId PK, position)
- `alert` (id, wgId, minWind, maxWind?, directions bitmask, trigger, horizon, quiet
  hours, lastFiredAt, enabled)
- `recent_spot` (wgId, openedAt), `recent_search` (query, at)

Wind timeline for the map is stored as a single compressed blob (file in `cacheDir`)
rather than rows.

### 7.5 Migration of web favourites (nice to have)

Web favourites live in the browser's `localStorage` (`favoriteSpots`). Offer an
import: the user opens `https://varun.surf/?export=favourites` (requires a tiny
frontend addition, section 12) which shows a QR code / share link
`varun://import?ids=1,2,3`. Optional, v1.1.

---

## 8. Quality

### 8.1 Testing strategy

- `core:domain`: exhaustive unit tests (wind classes, firing score, calculator golden
  tests from the JS implementation, alert evaluation including direction sectors
  wrapping 337.5°→22.5°, date parsing in spot time zones and DST edges).
- `core:network`: MockWebServer tests with **real captured payloads** from production
  (`/api/v1/spots`, `/spots/{id}`, `/wind`, `/forecast/{id}`) checked into
  `core:testing`; tests for missing/extra fields and empty strings; session 401 → refresh
  → retry flow, single-flight under concurrency.
- `core:data`: repository tests with fake API + in-memory Room (offline-first
  behaviour, freshness rules).
- ViewModels: Turbine-based state tests.
- UI: Compose UI tests for key interactions; Roborazzi screenshots in light/dark, EN/PL,
  font scale 1.0/2.0, compact/expanded width.
- E2E: Maestro flows against a debug build pointing at a local backend
  (`./gradlew bootRun`): open spot, change model, favourite, search, map timeline,
  calculator. AI/ICM flows only against a mocked backend.
- Coverage ≥ 80 % for `core:*` (same bar as the backend), measured with Kover.

### 8.2 Accessibility

- All touch targets ≥ 48 dp; content descriptions for arrows ("wind from south-west").
- Wind classes never conveyed by colour alone.
- Supports font scale up to 200 % (cards reflow, table cells grow).
- TalkBack: cards read as one node ("Jastarnia, Poland, 18 knots gusting 23 from
  south-west, 3 minutes ago, favourite").
- Respects "remove animations" (no particles, no shared-element transitions).

### 8.3 CI/CD

- New workflow `.github/workflows/android.yml`, triggered on changes under `android/**`:
  lint, detekt, ktlint, unit tests, Roborazzi verify, assembleRelease (R8), upload APK
  artifact.
- Release: tag `android-vX.Y.Z` → signed AAB (signing keys in GitHub secrets) → upload
  to Play Console internal track via Gradle Play Publisher. Promotion to production is
  manual.
- Dependency updates: Renovate/Dependabot for the Android version catalog, alongside the
  existing `deps.yml`.
- Versioning: `versionName` semver, `versionCode` from CI run number.

---

## 9. Design

### 9.1 Principles

- Wind numbers are the hero: largest type on every card/screen.
- One-handed: primary actions in the bottom half (bottom bar, FABs, bottom sheets).
- Glanceable in sunlight: high-contrast light theme option; avoid thin fonts for data.
- Same visual language as the web (logo, wind colour scale), not a pixel copy.

### 9.2 Theme

- Material 3 with a brand colour scheme derived from the web palette; dynamic colour
  (Material You) optional on Android 12+.
- Wind colour scale (calm / light / weak / moderate / strong / extreme) defined once in
  `core:designsystem`, tuned for contrast in both themes, matching the web's hues.
- Typography: system font (Roboto Flex) with tabular figures for numbers.

### 9.3 Motion

- Shared element: card → spot detail (title, photo).
- Direction arrows animate rotation on data refresh.
- Predictive back on all pushed screens and sheets.

### 9.4 Iconography

Material Symbols (rounded); custom wind arrow and kite icons as vector drawables;
adaptive + themed (monochrome) launcher icon from the existing `logo.png`.

### 9.5 Edge-to-edge

`enableEdgeToEdge()`, insets handled per screen; map draws under status/navigation bars.

### 9.6 Large screens and foldables

Not the target, but must not break: navigation rail at ≥ 600 dp; list-detail layout
(`ListDetailPaneScaffold`) for Spots → Spot detail on expanded width; map uses the
side sheet instead of the bottom sheet on expanded width.

---

## 10. Delivery plan

| Milestone | Content |
|---|---|
| **M0 - Foundations** | Project skeleton, build-logic, CI, design system, network + session handling, Room, captured fixtures |
| **M1 - Browse** | Spots list, country filter, search, favourites (incl. reorder), spot detail (live, history, daily, hourly table, model selector, info, links, navigate, share), settings, EN/PL, offline cache |
| **M2 - Map** | Map with clustering, markers by wind class, timeline slider, marker bottom sheet, wind field overlay |
| **M3 - Mobile extras** | Widgets, wind alerts + notifications, nearby spots, app shortcuts, App Links |
| **M4 - Paid features & polish** | AI analysis and ICM flows, calculator, about/status, accessibility pass, baseline profiles, Play Store listing |
| **v1.1** | Compass-relative wind, Quick Settings tile, webcam previews, favourites import from web |
| **v2** | Wear OS tile/complication, optional server push alerts |

---

## 11. Acceptance criteria (v1)

- Every **Keep/Adapt** row in section 2 is implemented and reachable within 2 taps
  from the start screen.
- With airplane mode on after one successful launch, the app opens straight into the
  last known favourites/spots with "updated X ago" labels and no error dialogs.
- Live wind on favourites refreshes at most 60 s apart while visible and not at all in
  the background except through workers.
- AI analysis / ICM generation can only be triggered through an explicit confirmation,
  never twice concurrently for the same spot, and never from background work.
- A Polish-language device shows Polish UI, `spotInfoPL`, and `aiAnalysisPl`.
- Wind alerts fire for a test rule within one worker period and respect quiet hours
  and the once-per-day limit.
- Opening `https://varun.surf/spot/500760` from another app opens the spot in the app.
- No crash-free-rate regressions: ≥ 99.5 % crash-free users on the internal track
  before production.
- Lint/detekt clean, unit coverage ≥ 80 % in `core:*`, screenshot tests green.

---

## 12. Backend / web changes (optional, proposed)

None of these block M0-M2.

1. **`/.well-known/assetlinks.json`** served by Spring (static resource) or nginx, with
   the app's package name and signing certificate SHA-256 - required for verified App
   Links (M3). Must be exempt from the session filter and served with `no-cache`.
2. **Time zone in forecasts** - add `timeZone` (IANA id) per spot, or emit hourly
   `date` as ISO-8601 with offset, so the app doesn't have to guess (Q1).
3. **ETag / `If-None-Match`** on `/api/v1/spots` and `/api/v1/wind` - lets the app
   and widgets poll cheaply (304 instead of re-downloading ~820 spots).
4. **Feature flags in `/api/v1/status`** - e.g. `features: { aiAnalysis: true, icm:
   true }`, so the app can hide the buttons instead of discovering a 503 after a
   confirmation (Q3).
5. **Lightweight live endpoint** - `GET /api/v1/live?ids=1,2,3` returning only
   `currentConditions` for given spots; ideal for widgets and live alerts.
6. **Server-side alerts via FCM** (v2) - device registers alert rules; the backend,
   which already refreshes live data every minute, pushes notifications. Removes the
   15-minute WorkManager latency and saves battery.
7. **Favourites export on the web** - QR/share link for 7.5.
8. **`X-Client: varun-android/<version>`** header recognised in metrics, so app traffic
   is visible separately on the `/metrics` page and in nginx rate-limit tuning.
9. **Docs** - once the app exists, add an Android section to README.md, CLAUDE.md and
   AGENTS.md.

---

## 13. Decisions

- **D1 - Maps: MapLibre** (not Google Maps). No API key or billing for the base map,
  no Google Play Services dependency, open styles that can be matched to the web's look
  and later self-hosted. Clustering and markers are done with GeoJSON source clustering
  and style layers (section 5.4).
- **D2 - Application ID: `surf.varun.app`.** Derived from the owned domain, which is also
  the domain App Links are verified against (`assetlinks.json`, section 12.1). It cannot
  be changed after the first Play upload, so it is fixed from M0. Kotlin source packages
  use the same root (`surf.varun.app.*`, `surf.varun.core.*`, `surf.varun.feature.*`).

## 14. Open questions

- **Q1 - Time zone of `Forecast.date` and `CurrentConditions.date`.** Hourly forecasts
  look like spot-local wall-clock time (Windguru convention) and live readings like
  station-local time, but neither carries a zone. Confirm on the backend; until then
  derive the zone from spot coordinates (bundled tz lookup, e.g. a small offline
  tz-boundary dataset) and show times in spot-local time, labelled when it differs from
  the device zone.
- **Q2 - Stale threshold** for live readings: reuse the web value (confirm exact number
  in `weather.js`/`index.js`).
- **Q3 - Detecting that AI/ICM is disabled** server-side without spending a click -
  see proposal 12.4.
- **Q4 - Satellite imagery provider**: MapTiler (free tier, key) is the default
  candidate; confirm, or drop satellite from v1.
- **Q5 - Spot photos licence** for use in the app/Play listing (they are served by the
  backend already).
- **Q6 - Alert evaluation hours** ("daylight only") - reuse the backend AI window
  (06:00-21:00 spot-local, `AiService.FIRST_DAY_HOUR`/`LAST_DAY_HOUR`) as the default.
