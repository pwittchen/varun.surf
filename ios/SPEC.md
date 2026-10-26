# varun.surf for iOS - Product & Technical Specification

Status: draft v1 (2026-10-26)
Scope: a native iOS client for the existing varun.surf backend, written in Swift,
with the same functionality as the Android app in `../android`. This document
describes **what** to build and **how**; it contains no implementation.

Reference material this spec is derived from:

- `../android/SPEC.md` - the Android product spec (section numbers prefixed `A` below,
  e.g. A5.3)
- `../android/README.md` - what the Android app actually ships, including its
  documented **deviations** from its own spec. Where the Android app and its spec
  disagree, **the shipped Android app wins**: iOS targets feature parity with the app,
  not with the spec
- `../android/CLAUDE.md` - Android architecture, backend contract and conventions
- `../CLAUDE.md`, `../docs/BACKEND.md`, `../docs/FRONTEND.md` - backend and web

---

## 1. Goals and non-goals

### Goals

- Feature parity with the Android app: ~820 spots in 59 countries, live wind from 14
  station integrations, multi-model forecasts, the wind map with wind field and globe,
  AI analysis and ICM forecast on demand, kite & board calculator, favourites, wind
  alerts, widgets, deep links, offline-first cache, EN/PL.
- Answer the two rider questions fast:
  1. **"Is it blowing now at my spots?"** - favourites with live wind, on the Home
     Screen and Lock Screen widgets and in notifications, without opening the app.
  2. **"When and where will it blow?"** - per-spot forecast, and the map timeline
     across spots.
- Feel like an iOS app, not an Android port: SwiftUI, system navigation and sheets,
  Dynamic Type, SF Symbols, haptics, WidgetKit, App Intents (Siri, Shortcuts,
  Spotlight), Universal Links, share sheet.
- Work on a poor beach connection: show cached data instantly, refresh in the
  background, degrade gracefully offline.
- Reuse the existing public API as-is - exactly the endpoints the Android app uses.
  The only backend change v1 needs is the `apple-app-site-association` file for
  Universal Links (section 12.1).

### Non-goals

- No business logic in the app that belongs to the backend: the app never scrapes
  Windguru or weather stations and never talks to OpenAI.
- No user accounts, no favourites sync between devices or with Android in v1. iCloud
  key-value sync of favourites is a v1.1 option (section 10).
- No shared code with Android (no Kotlin Multiplatform, no Skip/Flutter/React
  Native). The two apps share **rules**, kept in sync through golden tests and shared
  fixtures (section 8.1), not code.
- iPad and Mac (Designed for iPad) must work but are not the design target
  (section 9.6). No watchOS, tvOS, visionOS or CarPlay in v1.

---

## 2. Feature mapping: Android → iOS

Legend: **Keep** = same feature, native iOS UI. **Adapt** = same intent, different
shape because of the platform. **Drop** = not built, with the reason. **New** =
iOS-only.

### 2.1 Spots list and Favourites (A2.1, A5.2)

| Android (shipped) | iOS | Notes |
|---|---|---|
| `LazyColumn` of spot cards (live wind, daily strip) | **Keep** | `List`/`LazyVStack` of cards, section 5.2 |
| Search bar on the Spots tab | **Keep** | `.searchable` on the Spots navigation stack plus a dedicated Search tab role (5.5) |
| Country filter bottom sheet (flags, counts, searchable) | **Keep** | `.sheet` with detents `[.medium, .large]` and its own `.searchable` |
| Sort: Firing now / Name / Windiest today / Distance | **Keep** | `Menu` with a `Picker` in the toolbar; Distance only when location is on |
| "Live only" filter chip | **Keep** | Toggle chip in the filter row |
| No "Nearby" / "Windy today" chips (Android deviation) | **Keep** | Same scope as Android; distance on cards and distance sort stay |
| Favourites tab with long-press drag reorder | **Keep** | `List` with `.onMove` (`EditButton` + drag handles; drag also via long press) |
| Card density comfortable / compact | **Keep** | Setting |
| Hero photo collapsing header | **Adapt** | A random spot photo as a stretchy header above the list, scrolling away under a standard large title. No setting on Android - none on iOS either |
| Forecast sweep progress banner (`/status/forecast`) | **Keep** | Thin banner under the navigation bar, `ProgressView(value:)` |
| Stale live data (tinted timestamp chip) | **Keep** | Same 60 min rule |
| Sponsors row at the end of the list | **Keep** | |
| Theme System / Light / Dark | **Keep** | `preferredColorScheme` |
| Material You dynamic colour | **Drop** | No iOS equivalent; the brand palette is used everywhere |
| Per-app language with in-app picker | **Adapt** | iOS per-app language lives in the system Settings app; section 6.9 |
| Bottom navigation bar | **Adapt** | `TabView` (iOS 18 `Tab` API, `.sidebarAdaptable` on iPad) |
| Long-press card context menu (favourite, share, navigate, create alert) | **Keep** | `.contextMenu` with a card preview |
| Shared-element transition card → spot | **Adapt** | iOS 18 zoom navigation transition (`.navigationTransition(.zoom)` + `matchedTransitionSource`) |

### 2.2 Spot detail (A2.2, A5.3)

| Android (shipped) | iOS | Notes |
|---|---|---|
| Collapsing header with spot photo | **Keep** | Stretchy photo header + inline title once scrolled |
| Now: live card + 12 h history chart, switch to table | **Keep** | Swift Charts (`LineMark` wind, `AreaMark` gust band, direction arrows as `PointMark` symbols); `Picker(.segmented)` Chart/Table |
| Day chips acting as day selector | **Keep** | Horizontal `ScrollView` of chips; selecting one scrolls the hourly view (`ScrollViewReader` / `scrollPosition`) |
| Hourly table (sticky first column, wind-class cells, day separators) | **Keep** | Custom SwiftUI grid: pinned label column + horizontally scrolling `LazyHStack` of hour columns |
| Hourly chart alternative | **Keep** | Swift Charts: wind/gust lines, direction arrows, rain bars |
| "Windy days" toggle | **Keep** | Same rule (any daytime hour ≥ 12 kts) |
| Model selector → sheet listing `availableModels` | **Keep** | Toolbar `Menu` for short lists, sheet with `.searchable` for long ones |
| AI analysis with confirmation, generation time, other-language label | **Keep** | `.confirmationDialog` is too terse for the hint text - use a custom sheet (6.5) |
| "Report analysis" / "Report ICM forecast" (mail) | **Keep** | `MFMailComposeViewController`, falling back to a `mailto:` URL |
| ICM forecast button (spots with `icmUrl`), appears in model list | **Keep** | Same flow; on success ICM is selected |
| Spot info (`spotInfo` / `spotInfoPL`) with icons | **Keep** | SF Symbols |
| Static mini map + Navigate | **Adapt** | `MKMapSnapshotter` image (cached on disk) + Navigate menu: Apple Maps, Google Maps, Waze (when installed) |
| Coordinates (copyable) | **Keep** | `UIPasteboard` + `.sensoryFeedback(.success)` |
| Links (Windguru, Windfinder, ICM, webcam, location) in Custom Tabs | **Adapt** | `SFSafariViewController` |
| Sponsors of the spot | **Keep** | |
| "Data updated" footer, "Open on varun.surf" | **Keep** | |
| Kite size action prefilled with live / selected-hour wind | **Keep** | Toolbar button → calculator sheet |
| "This spot no longer exists" (404) with remove from favourites/alerts | **Keep** | |
| Landscape phone layout (one-line bar, two columns) | **Adapt** | Size-class driven: `verticalSizeClass == .compact` gets the condensed header and two-column sections |

### 2.3 Wind map and globe (A2.3, A5.4, A6.4)

| Android (shipped) | iOS | Notes |
|---|---|---|
| MapLibre map, keyless basemaps (Esri satellite default, OpenFreeMap liberty) | **Adapt** | **MapKit** (decision D1): `.imagery` (satellite, default) and `.standard`. Native, keyless, follows dark mode |
| Clustered markers coloured by wind class, max wind in cluster | **Keep** | `MKMapView` with `MKClusterAnnotation`; cluster shows count and max wind (section 5.4) |
| Wind field overlay (colour wash + particles) on/off | **Keep** | Overlay view above `MKMapView`, maths ported 1:1 from Android `WindField.kt` (6.4) |
| Markers on/off | **Keep** | |
| Timeline slider with play/pause, day ticks, haptics | **Keep** | Custom slider; `.sensoryFeedback(.selection)` per hour, `.impact` at midnight |
| Timeline reach 5 days / full run (setting) | **Keep** | `hours=120` / `384` |
| Timeline visibility toggle | **Keep** | |
| Tap marker → bottom sheet (summary for the selected hour) | **Keep** | `.sheet` with `[.height(…), .medium]` detents and `.presentationBackgroundInteraction(.enabled)`, so the map stays usable |
| Map search sheet: pick flies the camera and opens the panel | **Keep** | |
| Filters: country, favourites only, live only | **Keep** | |
| Controls in one floating card that folds to a button | **Keep** | |
| My location, fit to favourites | **Keep** | |
| Globe view (Canvas port of `globe.js`), idle spin, setting kept | **Keep** | SwiftUI `Canvas` port of Android `Globe*.kt`; same world-atlas 1:110m / 1:50m outlines bundled (6.4.3) |
| Side sheet on wide screens | **Adapt** | On iPad regular width the spot panel is an inspector (`.inspector`) |
| Wind field off under battery saver / reduced motion | **Keep** | `ProcessInfo.isLowPowerModeEnabled`, `accessibilityReduceMotion` |

### 2.4 More tab (A2.4, A5.6-A5.9)

| Android (shipped) | iOS | Notes |
|---|---|---|
| Calculator (persisted inputs, prefill from spot) | **Keep** | |
| Wind alerts list + editor | **Keep** | Delivery is weaker on iOS - see 6.6 |
| Settings | **Keep** | Minus dynamic colour; language opens system Settings |
| About: status, sources, sponsors, contact, privacy, licences | **Keep** | Licences screen generated from the SPM dependency list |
| Status / metrics / logs / MCP / TV / embed / SEO / llms pages | **Drop** | Same reasons as Android (operator or web-only) |

### 2.5 Mobile extras (A2.5)

| Android (shipped) | iOS | Notes |
|---|---|---|
| Glance spot widget (2×1, 3×1+) | **Adapt** | WidgetKit spot widget: `systemSmall`, `systemMedium` (adds gusts, temp, age, 12 h strip) |
| Glance favourites widget | **Adapt** | `systemMedium` (3 rows), `systemLarge` (≤ 7 rows) |
| Widget refresh button | **Keep** | Interactive widget `Button(intent:)` (iOS 17 App Intents) |
| Widget configuration activity | **Adapt** | `AppIntentConfiguration` with a `SpotEntity` picker (searchable) |
| Wind alerts via WorkManager (15 min live, 3 h forecast) | **Adapt** | `BGAppRefreshTask`; best effort, no guaranteed period (6.6) |
| Notification action "Mute today" | **Keep** | `UNNotificationCategory` action |
| App Links + `varun://` scheme | **Adapt** | Universal Links + `varun://` URL scheme (6.7) |
| Launcher shortcuts (4 dynamic + Wind map + Search) | **Adapt** | Home Screen quick actions (`UIApplicationShortcutItem`): 2 static + 2 dynamic (iOS shows 4 in total) |
| Share spot (link + short text) | **Keep** | `ShareLink` |
| Navigate to spot | **Keep** | Apple Maps / Google Maps / Waze |
| Units (kts, m/s, km/h, Bft; °C/°F) | **Keep** | |
| Haptics | **Keep** | `.sensoryFeedback` |
| Offline-first cache, pull-to-refresh | **Keep** | `.refreshable` |
| Lock Screen widgets | **New** | `accessoryCircular` (wind number + arrow), `accessoryRectangular` (spot, wind/gusts, direction), `accessoryInline` |
| App Intents / App Shortcuts | **New** | "What's the wind at <spot>?", "Open <spot>", "Show the wind map" - Siri, Shortcuts app, Spotlight, Action button |
| Spotlight indexing | **New** | `CSSearchableItem` per favourite / recently opened spot; tapping opens the spot |
| StandBy | **New** | Comes free with `systemSmall` widgets; verify legibility in night mode (red tint) |

### 2.6 Considered and deferred

- **Control Center control** (iOS 18 `ControlWidget`) showing the first favourite's
  wind - the iOS counterpart of Android's v1.1 Quick Settings tile. v1.1.
- **Live Activity** "session at <spot>" with live wind on the Lock Screen and Dynamic
  Island. Requires frequent updates; without server push (APNs) it can only update
  while the app runs. v2, together with server-side push alerts.
- **watchOS complication / app** - good fit, separate design pass; v2 (Android: Wear OS v2).
- **Compass-relative wind**, **webcam previews**, **favourites import from web**,
  **offline map regions** - v1.1, same as Android.
- **CarPlay** - no meaningful use case.

---

## 3. Backend API used by the app

Base URL: `https://varun.surf`; overridable per build configuration (`Debug` may point
at `http://localhost:8080`, which the Simulator reaches directly - needs an ATS
exception for `localhost` in the Debug Info.plist only).

The app uses **exactly** the endpoints the Android app uses (`../android/CLAUDE.md`,
"Backend Contract"):

| Endpoint | Method | Used for | Notes |
|---|---|---|---|
| `/api/v1/session` | GET | Obtain the `SESSION` cookie | 204 + `Set-Cookie`. On first launch, ahead of expiry, and on any 401 |
| `/api/v1/spots` | GET | Spots list, map markers, search index, widgets, live alerts | Large (~820 spots, ~580 KB). Decode off the main actor |
| `/api/v1/spots/{wgId}` | GET | Spot screen (GFS) | Includes `forecastHourly`, `currentConditionsHistory`, `availableModels`. **Triggers server-side model discovery - never from background code** |
| `/api/v1/spots/{wgId}/{model}` | GET | Spot screen with selected model | `model` = `AvailableModel.key` |
| `/api/v1/forecast/{wgId}` | GET | Grid-aligned hourly forecast | Widgets' 12 h strip, forecast alerts. 404 = unknown spot |
| `/api/v1/wind?hours=N` | GET | Map timeline + wind field | 120 or 384 hours |
| `/api/v1/spots/{wgId}/analysis?lang=en\|pl` | POST | Generate AI analysis (both languages) | 404 / 503. **Paid** - confirmation required |
| `/api/v1/spots/{wgId}/icm` | POST | Generate ICM forecast | 404 / 503. **Paid** - confirmation required |
| `/api/v1/sponsors` | GET | Sponsors | |
| `/api/v1/status` | GET | About → service status, backend version | |
| `/api/v1/status/forecast` | GET | Sweep progress banner | 5 s poll only while the banner is visible and `inProgress` |

Every request sends `X-Client: varun-ios/<CFBundleShortVersionString>`, so iOS
traffic is distinguishable from `varun-android/*` in nginx logs and metrics.

### 3.1 Session handling

Same contract as Android (`SessionManager.kt`):

- The `SESSION` cookie is a stateless signed token `<expiry epoch second>.<signature>`
  with a 24 h lifetime. The client never verifies the signature; it only reads the
  expiry prefix.
- **Do not rely on `HTTPCookieStorage`.** Configure the `URLSession`s with
  `httpShouldSetCookies = false` and `httpCookieAcceptPolicy = .never`, read
  `Set-Cookie` from `/api/v1/session` responses with
  `HTTPCookie.cookies(withResponseHeaderFields:for:)`, and attach
  `Cookie: SESSION=<token>` explicitly, only for the base URL's host. Reasons: the
  token must be shared with the widget extension and the background task, must
  survive app termination, and a 401 → renew → retry flow needs one owner.
- The token is stored in the **Keychain** with a shared access group
  (`$(AppIdentifierPrefix)surf.varun.shared`), accessibility
  `kSecAttrAccessibleAfterFirstUnlock` so background refresh and widgets can read it
  while the device is locked.
- A `SessionManager` **actor** owns the token:
  - `needsRenewal` when there is no token or fewer than 12 h of its 24 h are left;
    checked before each request batch.
  - `renew(stale:)` is single-flight: concurrent callers await one in-flight `Task`;
    a caller whose stale token was already replaced gets the new one without a call.
  - A 401 triggers one renewal and one retry of the original request; a second 401
    surfaces as `ApiError.unauthorized`.
- The widget extension and app process may renew concurrently; both write to the same
  Keychain item and last-writer-wins is fine (both tokens are valid).

### 3.2 Data contracts

As in A3.2 (and `android/core/network/dto/Dtos.kt`):

```text
Spot {
  wgId: Int                       // primary key everywhere
  name, country: String
  windguruUrl, windguruFallbackUrl?, windfinderUrl, icmUrl, webcamUrl, locationUrl: String ("" = absent)
  currentConditions?: CurrentConditions
  currentConditionsHistory?: [CurrentConditions]       // single spot only, 12 h
  forecast: [Forecast]            // daily; date = "Today", "Tomorrow", weekday names
  forecastHourly?: [Forecast]     // single spot only; date = "Fri 23 Oct 2026 14:00"
  aiAnalysisEn?, aiAnalysisPl?: String
  aiAnalysisEnCreatedAt?, aiAnalysisPlCreatedAt?: String   // ISO-8601 instant
  spotPhotoUrl?: String           // relative, content-hashed (?v=) - cache forever
  coordinates?: { lat, lon }
  spotInfo, spotInfoPL: SpotInfo  // type, bestWind, waterTemp, experience, launch, hazards, season, description
  sponsors?: [Sponsor]
  availableModels?: [{ key, name }]
  lastUpdated: String             // "2026-10-23 12:01:44 UTC"
}
CurrentConditions { date: "23/10/26 14:00:10", wind: Int, gusts: Int, direction: "SW", temp: Int }
Forecast { date, wind, gusts, direction, temp, precipitation, cloudCoverPercent, pressureHpa,
           wave?, wavePeriod?, waveDirection? }
HourlyForecast { wgId, hours: [Forecast] }              // missing hours absent, not zero
WindTimeline { hours: [String], spots: [{ wgId, wind: [Int?], gusts: [Int?], direction: [Int?] }] }
                                                         // direction = index into [N,NE,E,SE,S,SW,W,NW]
ForecastFetchProgress { inProgress, total, completed, fetched, empty, failed, cached, startedAt, finishedAt, elapsedMs }
Status { status, version, uptime, uptimeSeconds, startTime, spotsCount, countriesCount, liveStations }
Sponsor { id, main, name, link }
```

Decoding rules (unit-tested against the real fixtures, 8.1):

- **Every field is optional on the client and unknown fields are ignored.** Swift's
  synthesized `Decodable` throws on a missing non-optional key or a type mismatch, so
  DTOs must not use it naively. Either declare every DTO property optional and map to
  domain models with defaults, or use a small `@Default` property wrapper with a
  `KeyedDecodingContainer` extension. One malformed spot must never fail the whole
  `/api/v1/spots` decode: decode the array element-wise through a "lossy array"
  wrapper and drop (and log) elements that fail.
- Numbers may arrive as `Int` or `Double` (and `null` inside the wind timeline arrays):
  decode numerics leniently.
- Empty-string URLs mean "absent" (`URL?` = `nil`).
- `spotPhotoUrl` is relative: resolve against the base URL.
- Daily labels are English tokens; map to a `DayLabel` enum and localize on the client.
- DTOs (`Sendable` structs) live in the network layer; the rest of the app sees domain
  models only.

---

## 4. Domain rules (must match the web and Android)

These live in a pure-Swift `VarunDomain` package target (Foundation only, no UIKit /
SwiftUI / SwiftData), so they test on macOS with `swift test` in milliseconds. Each
rule names its Android and web source; **when one side changes, change the others**.

| Swift (`VarunDomain`) | Android (`core/domain`) | Web (`src/frontend/js/`) |
|---|---|---|
| `WindClass.swift` | `WindClass.kt` | `common/weather.js` |
| `Conditions.swift` | `Conditions.kt` | `common/weather.js`, `page/index.js` (`firingScore`) |
| `KiteCalculator.swift` | `KiteCalculator.kt` | `common/calculator.js` |
| `ForecastDays.swift` | `ForecastDays.kt` | spot page windy days filter |
| `WindField.swift` | `WindField.kt` | `common/map.js` |
| `DateParsing.swift` | `DateParsing.kt` | `common/date.js` |
| `SpotTimeZones.swift` | `SpotTimeZones.kt` | - |
| `AlertEvaluator.swift` | `AlertEvaluator.kt` | - |
| `SpotSearch.swift` | `SpotSearch.kt` | - |
| `Countries.swift` | `Countries.kt` | `translations.js`, `flags.js` |
| `Units.swift` | `Units.kt` | - |
| `Geo.swift` | `Geo.kt` | - |
| `GlobeMath.swift` | `feature/map/GlobeMath.kt` | `common/globe.js` |

### 4.1 Wind classes (knots)

| Class | Range | Use |
|---|---|---|
| calm | < 5 | map only |
| light | 5 - < 12 | map only |
| weak | < 12 | cards, forecast cells |
| moderate | 12 - < 18 | |
| strong | 18 - 25 (inclusive) | |
| extreme | > 25 | |

`of(knots)` for cards/cells, `forMap(knots)` splits weak into calm/light,
`ofAverage(wind, gusts)` = class of `(wind + gusts) / 2`, which tints whole cards and
rows. `isRideable` = moderate or above. Never conveyed by colour alone.

### 4.2 Current reading and "firing now"

- Current reading = live station reading if present; else the hourly entry closest to
  "now" in the spot's zone; else today's daily entry; else the first daily entry.
- Firing score = average of wind and gusts of the current reading; spots without any
  reading sort last (`-infinity`).

### 4.3 Stale live conditions

A reading is stale when its age is **≥ 60 minutes** (`date.js`), and also when its
date can't be parsed. Age is measured by interpreting the station's wall-clock time in
the spot's time zone.

### 4.4 Dates and time zones

- Forecast hours: `"EEE dd MMM yyyy HH:mm"` and `"EEE d MMM yyyy HH:mm"`, English
  (`Locale(identifier: "en_US_POSIX")`), whitespace collapsed; spot-local wall-clock.
- Station times: three formats, `yyyy-MM-dd HH:mm[:ss]`, `dd.MM.yyyy HH:mm`,
  `dd/MM/yy HH:mm:ss` (year + 2000).
- Represent wall-clock values as a `LocalDateTime`-like value type (year...second,
  `Comparable`, `Hashable`) rather than `Date`, and convert to `Date` only with an
  explicit `TimeZone`. Cache `DateFormatter`s (or use regex + `DateComponents`): they
  are expensive and the list re-evaluates every 60 s.
- Spot zone: the country → IANA zone table split by coordinates for Spain, Portugal,
  USA, Brazil, Australia and Mexico, **copied verbatim from `SpotTimeZones.kt`**;
  unknown country → device zone. Cache `TimeZone` instances.
- Times are shown in spot-local time, labelled with the zone abbreviation when it
  differs from the device's.

### 4.5 Kite & board calculator

1:1 port of `calculator.js` via `KiteCalculator.kt`: kite factors per skill, nearest
of 7/9/10/12/14/15/17 m² (ties keep the earlier size), twin-tip / directional board
rules, validation in the web's order, JavaScript `Math.round` semantics
(`floor(x + 0.5)`, **not** Swift's `.rounded()` which rounds half away from zero - same
for positives, but write it as `floor(x + 0.5)` to match the reference exactly). The
result is either a warning or a result, never both. Golden-tested against
`android/core/domain/src/test/resources/calculator_golden.txt`.

### 4.6 Windy days and daytime

Daytime = hours 06:00-21:00 inclusive, spot-local. A day is windy when any of its
daytime hours has wind ≥ 12 kts. The hourly views show daytime hours grouped by day;
"Windy days" drops non-windy days.

### 4.7 AI analysis language

Show `aiAnalysisPl` when the app language is Polish, else `aiAnalysisEn`; if the chosen
one is missing but the other exists, show it with an "in English / po polsku" label.

### 4.8 Wind alerts

Port `AlertEvaluator.kt` exactly:

- Rule: `wind ≥ minWind`, `wind ≤ maxWind` when set, direction in the selected set when
  non-empty (a reading without direction fails a direction-restricted rule).
- Live: fires on the current reading; at most once per **3 h** (`LIVE_COOLDOWN`).
- Forecast: the first run of ≥ `minConsecutiveHours` (default 2) contiguous daytime
  hours within `horizonDays` (default 3) from the current hour (spot-local) that all
  match; reports start, end, max wind, max gusts, dominant direction. At most once per
  calendar day (device zone).
- Suppressed when disabled, muted (`mutedUntil`), or in quiet hours (device-local,
  default 22-7, may wrap midnight, equal start/end = none).
- Thresholds always stored in knots; directions stored as an 8-bit mask in
  `[N, NE, E, SE, S, SW, W, NW]` order.

### 4.9 Search

Fold to lowercase, strip combining marks (NFD), and map `ł→l, đ→d, ø→o, æ→a, ß→s,
ı→i`. Index = name + English country + Polish country. Every query word must match;
rank name-prefix, then name-contains, then country-only, then alphabetical.

### 4.10 Units

Knots (default), m/s (one decimal below 10), km/h, Beaufort (limits
1, 4, 7, 11, 17, 22, 28, 34, 41, 48, 56, 64 kts); °C/°F. Display only - everything is
stored and compared in knots / °C.

---

## 5. Information architecture and screens

### 5.1 Navigation

`TabView` with the iOS 18 `Tab` API, four tabs matching Android, plus the search role:

1. **Spots** - all spots, filters, sort.
2. **Favourites** - reorderable; the default start tab once the user has a favourite
   (setting "Start tab": Auto / Spots / Favourites / Map).
3. **Map** - wind map / globe with timeline.
4. **More** - Calculator, Wind alerts, Settings, About.
5. `Tab(role: .search)` - global search (5.5). On iPhone it renders as the separated
   search button of the tab bar.

Each tab owns a `NavigationStack` with a typed path (`[Route]`, `Route: Hashable,
Codable`), so tab stacks are preserved and restorable (`@SceneStorage`). Routes mirror
`android/core/navigation/Routes.kt`:

```text
enum Route { spot(wgId, focusTime?), calculator(windKnots?), alerts,
             alertEditor(alertId?, wgId?), settings, about, licences }
Tab roots: spots(country?), favourites, map(focusWgId?), more, search
```

A single `@Observable AppRouter` (injected through the environment) owns the selected
tab and the paths; features navigate only through it (`navigate(_:)`,
`switchTo(_:root:)`), never by importing one another. Deep links, quick actions,
notifications, widgets and App Intents all resolve to `AppRouter` calls (6.7).

iPad (regular width): `.tabViewStyle(.sidebarAdaptable)`; Spots → spot detail as a
`NavigationSplitView` is v1.1 (Android has no list-detail pane either).

### 5.2 Spots list and Favourites

- Large title, stretchy random-spot photo header, `.searchable`.
- Filter row (horizontal scroll): Country (sheet), Sort (menu), Live only.
- Card (comfortable ~88 pt, compact ~64 pt; both grow with Dynamic Type):
  - leading: name, flag emoji + localized country, distance when location is on
  - centre: wind (largest type, monospaced digits), `/ gusts`, rotating direction
    arrow, temperature; age chip ("3 min ago", stale tint); "forecast" label when the
    reading is not live
  - trailing: favourite star (haptic on toggle)
  - bottom strip: next 3 days (localized day label, average wind tinted by class,
    arrow)
- Tap → spot detail (zoom transition). Context menu: favourite, share, navigate,
  create alert. No swipe actions (avoid accidental actions, as Android).
- Favourites: `EditButton` mode with drag handles plus long-press drag; order stored
  as positions.
- Empty states via `ContentUnavailableView`: no favourites, no results, offline
  without cache.

### 5.3 Spot detail

Sections in the Android order (A5.3 as shipped):

1. **Now** - live card (wind, gusts, arrow + cardinal, temp, reading age, stale state),
   12 h history Chart / Table (newest first). Hidden without a live station.
2. **Forecast** - toolbar: model menu (`GFS 13 km`), Windy days toggle, Table / Chart.
   Day chips; hourly table (default) or chart. Changing model calls
   `/api/v1/spots/{wgId}/{model}`; the chosen model is remembered for the app session,
   or globally when "Remember model" is on. A `focusTime` (from an alert) scrolls to
   and highlights that hour.
3. **AI analysis** - text + "Generated 14:32" (device zone) + disclaimer + Report; or
   the Generate button (6.5).
4. **ICM forecast** - only with `icmUrl`: Generate button, or "ICM forecast is ready -
   pick it in the model list" / auto-select on success.
5. **About this spot** - info fields with SF Symbols; description.
6. **Location** - map snapshot, coordinates (copy), Navigate.
7. **Links** - Windguru, Windfinder, ICM meteogram, webcam, location.
8. **Sponsors** of the spot.
9. Footer: "Data updated 12:01 UTC", "Open on varun.surf".

Toolbar: favourite, share (`ShareLink` with `https://varun.surf/spot/{id}` and
"Jastarnia: 18 kts SW, gusts 23 - varun.surf"), menu (kite size, create alert,
navigate, open on web).

### 5.4 Map

- `MKMapView` wrapped in `UIViewRepresentable` (decision D1: SwiftUI `Map` has no
  clustering and no per-frame projection access, which the wind field needs - the same
  reason Android uses MapLibre in an `AndroidView`).
- Map type: satellite (`MKImageryMapConfiguration`, default, matching the web and
  Android) or standard (`MKStandardMapConfiguration`, follows dark mode). Points of
  interest hidden. Apple attribution (legal label) always visible.
- Annotations: one `MKAnnotation` per spot (~820) with `clusteringIdentifier`. Custom
  `MKAnnotationView`: circle tinted by `WindClass.forMap` of the selected hour, wind
  number, arrow rotated `dirIndex × 45°` (+180° so it points downwind, matching
  Android). Cluster view: count + max wind, tinted by the max. Tap cluster → zoom to
  its members' bounding rect. Annotation views are reused; a timeline step updates
  the existing views' properties in place (no remove/add), keeping steps cheap.
- Timeline: hour label ("Sat 14:00", localized), day ticks, play/pause (about 4 h/s),
  "Now" snaps to live readings where present. Haptics per hour, stronger at midnight.
- Spot panel: sheet with a small detent showing name, wind for the selected hour, live
  reading, 3-day strip, buttons Open spot / Favourite / Navigate; updates as the
  timeline moves.
- Controls card (folds to one button): map type, wind field, markers, timeline, globe,
  globe spin, my location, fit to favourites, filters (country, favourites only, live
  only), search.
- Data: `/api/v1/spots` (positions, live) + `/api/v1/wind?hours=120|384`. The timeline
  is cached on disk and refetched when older than 1 h, or when the forecast sweep
  finished after it was fetched.

### 5.5 Search

Local and instant over the cached list (4.9). Sections: recent searches, recently
viewed spots, results. Reachable from the search tab, the Spots tab's `.searchable`,
a quick action and an App Intent. The map searches in place (5.4).

### 5.6 Calculator

Wind (slider + field, in the user's unit, converted to knots), weight (kg), skill &
conditions (the 7 web options). Output: kite size, board size, or the warning.
Inputs persisted; prefilled from a spot.

### 5.7 Wind alerts

- List (`List` with toggles, swipe to delete) and editor sheet: spot (search picker),
  min wind slider, optional max wind, 8-way direction toggles prefilled from the spot's
  `bestWind` (port `BestWind.kt`), trigger Live / Forecast, horizon days, consecutive
  hours, quiet hours.
- A banner in the list explains that iOS decides when background checks run (6.6),
  and offers to open Background App Refresh settings when it is off
  (`UIApplication.shared.backgroundRefreshStatus`).
- Notification permission is requested when the first alert is saved, never at launch.
- Notification copy as Android (`AlertMessages.kt`): "Jastarnia is firing - 21 kts SW
  (gusts 26)" / "Wind window Sat 13:00-17:00 at Jastarnia, up to 22 kts". Tap →
  spot detail at `focusTime`. Action "Mute today" sets `mutedUntil` to the next local
  midnight. `threadIdentifier` per spot so notifications group.

### 5.8 Settings

Theme (System / Light / Dark), language (opens system Settings, 6.9), wind unit,
temperature unit, card density, start tab, remember model globally, timeline reach
(5 days / full run), particle quality (Auto / Low / High / Off), use location,
notifications (status + link to system settings), clear cache (with confirmation).
Map toggles (wind field, markers, satellite, globe, globe spin, timeline) persist from
the map's controls card, as on Android.

### 5.9 About

App version + build, backend version / uptime / start time / spots / countries / live
stations (`/api/v1/status`), forecast sweep state, data sources (forecasts, live
stations, spots - static, linking out), sponsors, author, contact e-mail, "Suggest a
spot", GitHub, website, privacy policy (`https://varun.surf/privacy`), licences, map
data attribution.

---

## 6. Behaviour details

### 6.1 Data refresh policy

| Data | App refresh |
|---|---|
| Spots list incl. live | on `scenePhase == .active` if older than 1 min; every 60 s while Spots / Favourites / Map is visible; pull-to-refresh |
| Spot detail | on open if older than 1 min; every 60 s while visible |
| Wind timeline | on Map open if older than 1 h, or sweep finished since |
| Widget data | widget timeline reload, 30 min policy (6.8) |
| Sweep progress | 5 s poll only while the banner is visible |

Polling is structured concurrency tied to view lifetime: `.task { }` loops that sleep
with `Task.sleep(for:)` and are cancelled automatically when the view disappears; all
polling also stops when `scenePhase` leaves `.active`.

### 6.2 Offline and errors

- Offline-first: views observe the local store (6.10, 7.4); network refreshes write
  into it. Never blank when a cached copy exists.
- "Updated X ago" on every data screen; offline banner driven by `NWPathMonitor`.
- GETs retry on 5xx / timeouts with exponential backoff, max 3 attempts. The paid
  POSTs never retry.
- 404 on a spot: "This spot no longer exists", offer to remove it from favourites and
  alerts.

### 6.3 Performance budgets

- Cold launch to first list frame from cache: < 1 s on an iPhone 12-class device
  (measure with `XCTApplicationLaunchMetric`).
- `/api/v1/spots` decode + store write off the main actor: < 300 ms for ~820 spots.
- List scrolling without hitches at 120 Hz (ProMotion): row models are `Equatable`
  value types, stable `wgId` identity, images decoded off the main thread.
- Map with wind field ≥ 50 fps on an iPhone 12-class device; particle count degrades
  automatically when frame time exceeds budget (port Android's quality scaling).
- App download size < 15 MB (no third-party map SDK).

### 6.4 Wind field and globe

#### 6.4.1 Maths

`WindField.swift` ports `WindField.kt` constant for constant: IDW power 2 over spots
projected to screen space, `MAX_DIST` 70 px influence growing ×2 per zoom level past
Leaflet zoom 8 (cap ×8), fade from half the radius, heat cells every 8 px at base alpha
0.6, colour stops (5 grey → 8 blue → 11 cyan → 12 green → 18 amber → 25 red → 35 dark
red), velocity grid every 16 px, particle speed 0.09, life 60 frames, trail fade 0.12,
18 particles per visible spot clamped to 40-1400, particle colour lightened 65 %
toward white, colour lookup tables at 4 steps per knot.

**Zoom mapping:** Android converts MapLibre zoom to Leaflet zoom (`+1`). MapKit has no
zoom level; derive the web-equivalent Leaflet zoom from the visible region:
`zoom = log2(360 × (viewWidthPt / 256) / region.span.longitudeDelta)`, then feed that
to `radius(leafletZoom:)`. Golden-test it against the Android values at a few known
camera positions so the field reaches equally far on all three platforms.

#### 6.4.2 Rendering

- An overlay view above `MKMapView` (user interaction disabled), redrawn every frame
  from a `CADisplayLink`. Spots are projected with `mapView.convert(_:toPointTo:)` on
  every frame while the camera moves, so the field stays glued to the map.
- Heat wash: rendered off the main actor into a `CGImage` (one pixel per cell, drawn
  scaled with linear interpolation) on camera idle and hour change.
- Particles: v1 draws them with Core Graphics / SwiftUI `Canvas` inside a
  `TimelineView(.animation)`, which matches what Android does with Compose Canvas. If
  the 50 fps budget is missed on the reference device, move the particle pass to a
  Metal `MTKView` (point sprites + trail fade by blending a translucent quad) - the
  maths package stays the same.
- The heat wash sits below the annotations: annotations live in the map's own view
  hierarchy, so the overlay is inserted between the map's tile content and annotation
  container only if feasible; otherwise draw the wash as an `MKOverlay` with a custom
  `MKOverlayRenderer` (which MapKit keeps below annotations) and only the particles
  in the overlay view. Decide during M2 spike.
- Off under Low Power Mode or Reduce Motion unless the user forces it in settings
  (Particle quality ≠ Auto).

#### 6.4.3 Globe

MapKit's own globe (visible when zoomed out on satellite) cannot host the wind field
or the custom clusters, so the globe is a SwiftUI `Canvas` port of the Android globe
(`GlobeMath.kt`, `GlobeCamera.kt`, `GlobeView.kt`, `GlobeOutlines.kt`):
orthographic projection, land clipped at the horizon, wind wash and particles,
clusters, drag / pinch / fling, idle spin (off under Low Power Mode and Reduce
Motion). Coastlines: the same world-atlas 1:110m and 1:50m outlines Android ships in
`assets/globe/`, bundled as resources (copied by the same
`feature/map/tools/globe_outlines.py` output). The choice and the spin persist in
settings.

### 6.5 Paid operations (AI analysis, ICM)

Each press may cost an LLM call; mirror `OnDemandRepository.kt`:

- Always behind a confirmation sheet with the Android/web wording in both languages
  (title, question, hint - `ai_modal_*`, `icm_modal_*` strings). Buttons: Cancel /
  Generate.
- One request per spot and kind at a time: an `OnDemandService` actor keyed by
  `(wgId, kind)` holds the running `Task`; a second press awaits the same task. The
  button is disabled with a progress label while running.
- The task is owned by the app-level service, not the view, so leaving the screen
  doesn't cancel a call that may already be billed. If the app is backgrounded
  mid-request, wrap it in `UIApplication.beginBackgroundTask` so it can finish.
  The result is written to the store, so it appears wherever the spot is shown.
- Separate `URLSession` with a 90 s request timeout and no retry.
- 503 → "could not be generated, try again later"; 404 → spot gone (6.2).
- **Never** triggered from widgets, App Intents, background tasks, alerts or any
  automatic path. No App Intent exposes these operations.

### 6.6 Background work and alerts

iOS has no WorkManager equivalent with a guaranteed period. This is the biggest
behavioural difference from Android and must be stated in the UI (5.7).

- `BGTaskScheduler` with one `BGAppRefreshTask` identifier
  `surf.varun.app.refresh`, registered at launch and re-submitted at the end of every
  run with `earliestBeginDate` = now + 15 min while any live alert is enabled, else
  now + 3 h while any forecast alert is enabled; nothing is scheduled without alerts.
  iOS decides the actual time based on usage; expect anything from ~15 min to several
  hours, and nothing at all when the user force-quits the app or turns Background App
  Refresh off.
- One run does (within the ~30 s budget, honouring `expirationHandler`):
  1. live alerts: one `/api/v1/spots` request **if the cached list is older than
     15 min** (the shared budget rule from Android), then `AlertEvaluator` per alert;
  2. forecast alerts due (last forecast check ≥ 3 h ago): `/api/v1/forecast/{wgId}`
     per spot with an alert;
  3. post notifications for `Fire` decisions, update `lastFiredAt`;
  4. `WidgetCenter.shared.reloadAllTimelines()` so widgets pick up the fresh list.
- Never call `/api/v1/spots/{wgId}` from background code (server-side model
  discovery; Windguru has blocked the production IP before).
- Every app foreground also runs the alert evaluation over freshly fetched data, so
  opening the app catches up on missed checks (without re-notifying, thanks to the
  cooldown rules).
- No background modes besides `fetch` (`processing` is not needed), no silent push,
  no location-based wakeups.
- Proper fix for latency is server-side push (APNs) - section 12, v2.

### 6.7 Deep links

| Link | Destination |
|---|---|
| `https://varun.surf/spot/{id}` | Spots tab → spot detail |
| `https://varun.surf/country/{name}` | Spots tab root filtered by country |
| `https://varun.surf/map` | Map tab |
| `varun://spot/{id}?focus=<spot-local ISO time>` | Spot detail scrolled to that hour |
| `varun://map`, `varun://search`, `varun://favourites` | tabs |

- Universal Links via the Associated Domains entitlement `applinks:varun.surf` and
  `/.well-known/apple-app-site-association` on the server (12.1), with components
  limited to `/spot/*`, `/country/*`, `/map`.
- One `DeepLinkParser` (pure, unit-tested, port of `DeepLinks.kt`) handles
  `.onOpenURL`, `.onContinueUserActivity(NSUserActivityTypeBrowsingWeb)`,
  notification responses, quick actions, widgets (`widgetURL` / `Link`) and App
  Intents.

### 6.8 Widgets (WidgetKit extension)

- **Spot widget** (`AppIntentConfiguration`, parameter `spot: SpotEntity`, default the
  first favourite): `systemSmall` (wind number + arrow + name + age),
  `systemMedium` (adds gusts, temp, age and a 12 h forecast strip from
  `/api/v1/forecast/{wgId}`), `accessoryCircular`, `accessoryRectangular`,
  `accessoryInline`.
- **Favourites widget** (`StaticConfiguration`): `systemMedium` (3 rows),
  `systemLarge` (up to 7), sorted by firing score.
- Timeline provider: one entry now plus "age" entries every 15 min (so "3 min ago"
  updates and turns stale without a fetch), policy `.after(now + 30 min)`. Fetches
  through the shared network/session code; reads the cache from the App Group when
  the network fails. Respects the 15-min shared `/api/v1/spots` budget.
- Refresh button: `Button(intent: RefreshWidgetsIntent())` - refetches and reloads.
- Colours: wind class colours for numbers; `widgetAccentable` for tinted / clear Home
  Screen modes (iOS 18+), legible in StandBy night mode.
- Tap → `varun://spot/{id}`.

### 6.9 Localization

- EN and PL from day one in String Catalogs (`Localizable.xcstrings`) per module, with
  plural variants (Polish one / few / many / other). Port wording from the Android
  `strings.xml` / `values-pl/strings.xml` files, which follow the web's
  `translations.js`. No hardcoded UI text.
- Language: follows the system per-app language (Settings → varun.surf → Language,
  which iOS shows once the app has two localizations). The in-app "Language" row
  shows the current one and opens `UIApplication.openSettingsURLString`. Deviation
  from Android, which has an in-app picker: overriding the locale at runtime on iOS
  fights the system (bundle lookup, widgets, App Intents, notifications all stay in
  the system language).
- The language that decides `spotInfoPL` / `aiAnalysisPl` / the `lang` parameter is
  `Bundle.main.preferredLocalizations.first`.
- Country names localized via `Countries.swift`; dates and times via
  `Date.FormatStyle` with the user's locale and 12/24 h preference.
- App Intents and widget strings localized too (`LocalizedStringResource`).

### 6.10 Privacy

- No analytics, ads, tracking or third-party SDKs that collect data; no ATT prompt.
- Location: `NSLocationWhenInUseUsageDescription`, requested only when the user turns
  on "Use location"; reduced (approximate) accuracy is enough and is accepted. Used on
  device only for distances; never sent to the backend.
- **Privacy manifest** (`PrivacyInfo.xcprivacy`) for the app and the widget
  extension: no tracking, no collected data types, required-reason API declarations
  for `UserDefaults` (CA92.1 / 1C8F.1 for the App Group) and file timestamps if used.
- App Store privacy label: "Data Not Collected".
- Crash reporting: Apple's own (App Store Connect / MetricKit, opt-in by the user at
  the OS level); no third-party crash SDK.
- Update the website's privacy page (`/privacy`, EN/PL), which today covers the
  website and the Android app, to cover the iOS app too (12.4).

---

## 7. Technical architecture

### 7.1 Stack

| Area | Choice |
|---|---|
| Bundle ID | `surf.varun.app` (same as Android; permanent once on App Store Connect). Widget: `surf.varun.app.widgets` |
| App Group | `group.surf.varun.app` (store, settings, widget cache) |
| Keychain group | `$(AppIdentifierPrefix)surf.varun.shared` (session token) |
| Language | Swift 6 with **strict concurrency** (complete checking) from day one |
| Deployment target | iOS 18.0 (iPhone + iPad; Mac "Designed for iPad" allowed). Required for the `Tab` API, zoom transitions, `@Observable`-friendly SwiftData, tinted widgets |
| Tooling | Latest stable Xcode at kickoff; project generated with **XcodeGen** from `ios/project.yml` (no hand-merged `.pbxproj`), SwiftLint + `swift-format` |
| UI | SwiftUI; UIKit only where SwiftUI lacks it (`MKMapView`, `SFSafariViewController`, `MFMailComposeViewController`) via representables |
| State | Observation framework (`@Observable` models per screen, `@MainActor`) |
| Concurrency | async/await, actors for shared mutable state (session, on-demand, store writer) |
| Networking | `URLSession` + `Codable`; no Alamofire |
| Persistence | **SwiftData** in the App Group container for spots, forecasts, favourites, alerts, recents; `UserDefaults(suiteName:)` for settings; files in the caches directory for the wind timeline blob and map snapshots (decision D3) |
| Images | `AsyncImage` is not cacheable enough; a small `ImagePipeline` actor over `URLCache` (disk-backed, large capacity) - photo URLs are content-hashed, so cache forever. Nuke is an acceptable alternative |
| Maps | MapKit (`MKMapView`), no API key |
| Charts | Swift Charts |
| Background | `BGTaskScheduler` (`BGAppRefreshTask`) |
| Widgets | WidgetKit + App Intents (interactive, configurable) |
| Notifications | UserNotifications |
| Intents | App Intents (`AppEntity` for spots, `AppShortcutsProvider`) |
| Spotlight | Core Spotlight |
| Location | Core Location (when in use, approximate OK) |
| Browser | SafariServices |
| Logging | `os.Logger` with subsystem `surf.varun.app`, per-module categories |
| Tests | Swift Testing (`@Test`) for units, XCTest for UI tests and performance; swift-snapshot-testing (pointfreeco) for screenshot tests |

Third-party dependencies are kept to a minimum: none are required for v1;
swift-snapshot-testing in tests only; Nuke optional.

### 7.2 Project structure

```
ios/
├── SPEC.md                         # this document
├── README.md, CLAUDE.md            # written with M0
├── project.yml                     # XcodeGen spec (targets, entitlements, settings)
├── Config/                         # .xcconfig per configuration (BASE_URL, versions)
├── App/                            # @main App, AppRouter, tab scaffold, deep links, quick actions,
│   │                               # BGTask registration, App Intents, Spotlight, Info.plist, entitlements
│   └── Resources/Assets.xcassets   # the existing ios/Assets.xcassets (app icon) moves here
├── Widgets/                        # WidgetKit extension (spot + favourites widgets, refresh intent)
├── Packages/VarunKit/              # local Swift package, one library target per module:
│   ├── Package.swift
│   └── Sources/
│       ├── VarunModel/             # pure value types (Spot, ForecastEntry, Direction, WindAlert, Settings...)
│       ├── VarunDomain/            # pure rules (section 4); depends on VarunModel only
│       ├── VarunNetwork/           # API client, DTOs + mappers, SessionManager, retries, paid client
│       ├── VarunStore/             # SwiftData models, store actor, settings, App Group paths
│       ├── VarunData/              # offline-first repositories, OnDemandService, connectivity, location
│       ├── VarunDesign/            # palette, wind colours, typography, WindArrow, WindNumber, chips
│       ├── VarunUI/                # SpotCard, formatting, banners, Safari/mail/maps helpers
│       ├── FeatureSpots/           # Spots + Favourites, country sheet, sort/filter
│       ├── FeatureSpot/            # spot detail, model menu, AI/ICM flows
│       ├── FeatureMap/             # map, annotations, wind field overlay, timeline, globe
│       ├── FeatureSearch/
│       ├── FeatureCalculator/
│       ├── FeatureAlerts/          # editor, list, alert checker, notifications
│       ├── FeatureSettings/
│       ├── FeatureAbout/
│       └── VarunTesting/           # fixtures (real payloads), fakes
│   └── Tests/                      # one test target per module
└── UITests/                        # XCUITest flows + launch performance
```

Rules (mirroring the Android module rules):

- `Feature*` targets depend on `Varun*` core targets only, **never on each other**.
  Cross-feature navigation goes through `Route` values in `VarunUI` (or a tiny
  `VarunNavigation` target) handled by `AppRouter` in the app target.
- `VarunModel` and `VarunDomain` import Foundation only, so `swift test` runs them on
  macOS without a simulator.
- The widget extension links `VarunModel`, `VarunDomain`, `VarunNetwork`, `VarunStore`,
  `VarunData`, `VarunDesign` - not the features - keeping it under the extension memory
  limit (~30 MB).

### 7.3 Layers and state

- **UI**: SwiftUI views are thin; one `@Observable @MainActor` screen model per screen
  exposing an immutable `State` struct and methods for user events. One-shot effects
  (toasts, navigation) as an `AsyncStream` or an optional "pending effect" property
  consumed by the view.
- **Domain**: free functions / enums with static functions in `VarunDomain`; no
  use-case boilerplate for 1:1 passthroughs (same rule as Android).
- **Data**: repositories own "cache then network", expose `AsyncStream`s of domain
  models backed by the store (store change notifications), and `async throws` refresh
  methods. Single source of truth = the store.
- Dependency injection: a plain `AppEnvironment` struct of protocols built once in the
  app (and once in the widget), passed through SwiftUI `Environment`. No DI framework.

### 7.4 Local store (SwiftData)

Mirrors the Android Room schema (`core/database/entity/Entities.kt`):

- `SpotRecord` (`wgId` unique, static info, coordinates, photo path, links, spot info
  EN/PL as encoded JSON, `lastUpdated`, `fetchedAt`)
- `LiveRecord` (`wgId`, wind, gusts, direction, temp, raw date, observed wall-clock,
  `fetchedAt`)
- `LiveHistoryRecord` - only opened spots, trimmed to 12 h
- `DailyForecastRecord` (`wgId`, index, label, values)
- `HourlyForecastRecord` (`wgId`, model, time, values) - opened / favourite / alert
  spots only, LRU-capped at 30 spots
- `AnalysisRecord` (`wgId`, lang, text, createdAt), `ModelRecord` (`wgId`, key, name)
- `FavouriteRecord` (`wgId` unique, position), `AlertRecord` (fields of 4.8 + `id`),
  `RecentSpotRecord`, `RecentSearchRecord`

Rules:

- All writes go through one `@ModelActor` store actor; bulk replace of the spots list
  in one transaction. Views never get `ModelContext` directly - they read domain
  values from repositories, which keeps SwiftData out of feature code and makes a
  switch to GRDB possible without touching features (D3).
- Versioned schema (`VersionedSchema` + `SchemaMigrationPlan`) from v1; any model
  change adds a schema version and a migration stage, with a migration test.
- The wind timeline is one compressed file (`/api/v1/wind` body) in the App Group
  caches directory, not rows.
- "Clear cache" deletes everything except favourites, alerts, settings and
  calculator inputs.

### 7.5 Build configuration

- `BASE_URL` per `.xcconfig` (`Debug.xcconfig` may override locally via an untracked
  `Local.xcconfig`), surfaced to code through Info.plist.
- `MARKETING_VERSION` semver, `CURRENT_PROJECT_VERSION` from the CI run number.
- Release: whole-module optimisation, dead-code stripping, dSYMs uploaded with the
  archive.

---

## 8. Quality

### 8.1 Testing

- **Shared fixtures with Android**: the real production payloads in
  `android/core/testing/src/main/resources/fixtures` (`spots.json`,
  `spots-500760.json`, `forecast-500760.json`, `wind.json`, `status.json`,
  `status-forecast.json`, `sponsors.json`) are the iOS fixtures too. `VarunTesting`
  bundles a copy; a CI step fails when the copies differ from Android's (or the files
  move to a shared `fixtures/` directory at the repo root - decide in M0).
- **Golden tests**: the calculator cases from
  `android/core/domain/src/test/resources/calculator_golden.txt`; port the Android
  `core:domain` unit tests case by case (wind classes, firing score, staleness, date
  parsing, time zones incl. DST edges, alerts incl. direction sectors wrapping
  NW→N and quiet hours wrapping midnight, search folding, units, wind field colours
  and radius).
- `VarunNetwork`: `URLProtocol`-based stub server tests with the fixtures; missing /
  extra fields, empty strings, numbers as int/double/null, a malformed element in the
  spots array; session 401 → renew → retry, single-flight under concurrency, renewal
  ahead of expiry; paid client never retries.
- `VarunData`: repositories with an in-memory SwiftData container and a fake API
  (offline-first behaviour, freshness rules, 15-min background budget,
  `OnDemandService` deduplication).
- Screen models: state tests with Swift Testing.
- Snapshot tests: light/dark, EN/PL, Dynamic Type default and AX3, iPhone SE and
  iPhone Pro Max, for cards, spot detail sections, widgets.
- UI tests (XCUITest) against a stubbed backend (launch argument switches the app to
  fixture responses): open spot, change model, favourite and reorder, search, map
  timeline, calculator, AI/ICM confirmation flows (never against production).
- Coverage ≥ 80 % for the core targets (same bar as backend and Android).

### 8.2 Accessibility

- Touch targets ≥ 44 × 44 pt.
- Full Dynamic Type including accessibility sizes: cards reflow vertically, the hourly
  table cells grow, nothing truncates the wind numbers.
- VoiceOver: a card is one element ("Jastarnia, Poland, 18 knots gusting 23 from
  south-west, 3 minutes ago, favourite") with custom actions (favourite, share,
  navigate, create alert); arrows have labels; charts provide
  `AXChartDescriptor` / Swift Charts accessibility labels; the map annotations are
  accessible elements.
- Wind classes never by colour alone (weight / label).
- Reduce Motion: no particles, no globe spin, cross-fade instead of zoom transitions.
- Increase Contrast and Smart Invert respected (photos marked `accessibilityIgnoresInvertColors`).

### 8.3 CI/CD

- `.github/workflows/ios.yml`, triggered on `ios/**` changes, `macos-15` (or newer)
  runner with the pinned Xcode: XcodeGen, SwiftLint, `swift test` for the package,
  `xcodebuild test` on a simulator, Release archive (unsigned on PRs).
- The backend `ci.yml` ignores `ios/**`, as it does `android/**`.
- Release: tag `ios-vX.Y.Z` → signed archive (certificate + provisioning profiles via
  App Store Connect API key in GitHub secrets, `xcodebuild -allowProvisioningUpdates`
  or fastlane `match`) → upload to TestFlight (internal testers). App Store release
  is manual.
- Dependency updates: the existing `deps.yml` gets an iOS job (Swift package
  versions, pinned Xcode), verified with the same build and tests before it pushes -
  like the Android job.

---

## 9. Design

### 9.1 Principles

Same as Android: wind numbers are the hero, one-handed (primary actions low: tab bar,
sheets, bottom controls), glanceable in sunlight, same visual language as the web
(logo, wind colour scale) - and native iOS patterns over Material ones (large titles,
sheets with detents, menus, context menus, SF Symbols).

### 9.2 Theme

- Brand palette from the web's `styles.css`, as in Android's `Color.kt` (dark: bg
  `#08090A`, surface `#0F1113`, accent `#22C3E6`; light: bg `#FFFFFF`, text `#37352F`,
  accent `#0891B2`), defined as Asset Catalog colours with light/dark (and
  high-contrast) variants in `VarunDesign`.
- Wind colours per theme exactly as `DarkWindColors` / `LightWindColors` in
  `android/core/designsystem/theme/Color.kt` (calm, light, weak, moderate, strong,
  extreme + fills, stale, favourite, temperature, rain, pressure, waves).
- Typography: SF Pro text styles (Dynamic Type), `.monospacedDigit()` for every number,
  SF Pro Rounded for the big wind numbers.

### 9.3 Motion

Zoom transition card → spot; arrows animate rotation on refresh
(`.contentTransition(.numericText())` for numbers); interactive dismissal on all sheets
and pushed screens (system-provided swipe back).

### 9.4 Iconography

SF Symbols (`wind`, `location.north.fill` for arrows, `star`, `bell`, `map`,
`globe.europe.africa`...), custom kite / wind-arrow symbols as custom SF Symbol
templates. The app icon already exists in `ios/Assets.xcassets/AppIcon.appiconset`
(the kite logo); add dark and tinted variants (iOS 18).

### 9.5 Safe areas

The map and globe draw under the status bar and home indicator; controls respect safe
areas, including landscape and the Dynamic Island.

### 9.6 iPad and landscape

Not the target, but must not break: sidebar-adaptable tabs, the map spot panel as an
inspector, readable content width on the spot page, all orientations on iPad, Stage
Manager / Split View sizes. iPhone landscape uses the compact-height layouts of 2.2.

---

## 10. Delivery plan

| Milestone | Content |
|---|---|
| **M0 - Foundations** | XcodeGen project, package skeleton, CI, design system, `VarunModel` + `VarunDomain` ported with golden tests, network + session, SwiftData store, fixtures |
| **M1 - Browse** | Spots, Favourites (reorder), country filter, search, spot detail (live, history, daily, hourly table/chart, models, info, links, navigate, share), settings, EN/PL, offline cache |
| **M2 - Map** | MapKit map, clusters, wind-class annotations, timeline, spot panel, wind field (spike for wash placement and fps first), globe |
| **M3 - iOS extras** | Widgets (Home + Lock Screen, interactive refresh), alerts + background refresh + notifications, quick actions, Universal Links, App Intents, Spotlight, nearby/distance |
| **M4 - Paid features & polish** | AI analysis and ICM flows, calculator, about/status, accessibility pass, snapshot tests, performance pass, privacy manifest, App Store listing, TestFlight |
| **v1.1** | Control Center control, iCloud favourites sync (`NSUbiquitousKeyValueStore`), compass-relative wind, webcam previews, favourites import from web, iPad split view |
| **v2** | Server push alerts (APNs) + Live Activity, watchOS |

---

## 11. Acceptance criteria (v1)

- Every **Keep / Adapt / New** row of section 2 is implemented and reachable within two
  taps from the start screen.
- Parity: for the shared fixtures, the iOS domain layer produces the same wind classes,
  firing order, stale flags, calculator results, windy days, alert decisions and search
  results as Android (asserted by the ported tests).
- With Airplane Mode on after one successful launch, the app opens straight into the
  last known favourites/spots with "updated X ago" labels and no error alerts.
- Live wind refreshes at most 60 s apart while visible and never from the app while in
  the background except through `BGAppRefreshTask` and widget timelines.
- AI analysis / ICM generation only through an explicit confirmation, never twice
  concurrently for the same spot, never from background code, widgets or intents.
- A device with Polish as the app language shows Polish UI, `spotInfoPL` and
  `aiAnalysisPl`.
- A forecast alert fires during a forced background refresh (Xcode
  `_simulateLaunchForTaskWithIdentifier`) and respects quiet hours and the once-per-day
  limit.
- Opening `https://varun.surf/spot/500760` from Messages / Safari opens the spot in the
  app.
- Widgets render from cache offline and refresh through the interactive button.
- No crashes in TestFlight over two weeks of internal use; SwiftLint clean; core
  coverage ≥ 80 %; snapshot tests green; App Store review passes with "Data Not
  Collected".

---

## 12. Backend / web changes

1. **`/.well-known/apple-app-site-association`** (required for Universal Links, M3):
   add `src/frontend/assets/apple-app-site-association` (no extension) and copy it in
   `build.ts` next to `assetlinks.json`. Content:
   `{"applinks":{"details":[{"appIDs":["<TEAMID>.surf.varun.app"],"components":[{"/":"/spot/*"},{"/":"/country/*"},{"/":"/map"}]}]}}`.
   Must be served over HTTPS **without redirects**, as `application/json`, exempt from
   the session filter (like `/.well-known/assetlinks.json` today - check
   `SessionAuthenticationFilter` and `CacheControlFilter`'s `WELL_KNOWN_PATH`) and not
   blocked by nginx/Cloudflare. Apple fetches it through its CDN, so a change can take
   hours to propagate.
2. **`X-Client: varun-ios/<version>`** recognised in metrics alongside
   `varun-android/*`, if client breakdown is added to `/metrics`.
3. **Privacy page** (`/privacy`, EN/PL): cover the iOS app; it is the App Store
   privacy policy URL.
4. **Docs**: once the app exists, add an iOS section to `README.md`, `CLAUDE.md` and
   `AGENTS.md`, and an `ios/CLAUDE.md` like `android/CLAUDE.md`.
5. **Smart App Banner** (optional): `<meta name="apple-itunes-app" content="app-id=…,
   app-argument=https://varun.surf/spot/{id}">` on the spot pages.
6. Shared with the Android wish list (A12), none blocking: time zone per spot in the
   API (removes `SpotTimeZones`), ETag on `/spots` and `/wind`, feature flags for
   AI/ICM in `/api/v1/status`, a light `/api/v1/live?ids=` endpoint for widgets and
   alerts, and server-side push alerts (APNs here, FCM on Android) for v2.

---

## 13. Decisions

- **D1 - Maps: MapKit, hosted as `MKMapView`.** First-party, keyless, free, satellite
  imagery included, dark mode for free, no SDK size. `MKMapView` over SwiftUI `Map`
  because the wind field needs per-frame projection and clustering needs
  `MKClusterAnnotation` - the same reasoning that made Android host MapLibre in an
  `AndroidView`. MapLibre Native iOS was considered for visual identity with Android;
  rejected because both Android basemaps (Esri satellite, OpenFreeMap) would need
  re-wiring and MapKit's satellite is equivalent for the purpose.
- **D2 - Bundle ID: `surf.varun.app`**, identical to the Android application id,
  permanent from M0.
- **D3 - Persistence: SwiftData behind a store actor.** First-party, App Group capable,
  enough for ~820 spots. Kept out of feature code so it can be swapped for GRDB if
  performance or migration pain shows up in M1 measurements.
- **D4 - iOS 18 minimum.** Unlocks the `Tab` API, zoom transitions, tinted widgets and
  a mature SwiftData; iOS 18+ covers the vast majority of active iPhones in late 2026.
- **D5 - No shared code with Android.** Parity is enforced by shared fixtures and
  ported tests, not a shared runtime.
- **D6 - Language follows the system per-app setting** (no in-app override), see 6.9.

## 14. Open questions

- **Q1 - Apple Developer account**: individual or organisation (seller name on the
  App Store), Team ID for the AASA file.
- **Q2 - Alert latency on iOS**: is best-effort `BGAppRefreshTask` acceptable for v1,
  or should server-side APNs alerts (12.6) move up from v2? The Android app gets ~15
  min live checks; iOS realistically will not.
- **Q3 - Fixture sharing**: copy with a CI diff check, or move the fixtures and the
  calculator golden file to a repo-level `fixtures/` directory used by both apps.
- **Q4 - Wind wash placement under MapKit annotations** (6.4.2): overlay view vs
  `MKOverlayRenderer` - settle in the M2 spike, together with the Canvas-vs-Metal
  particle choice.
- **Q5 - Spot photo licences** for the App Store screenshots (same as Android Q5).
- **Q6 - In-app language picker**: confirm the system-setting approach (D6) is fine,
  given Android offers an in-app picker.
