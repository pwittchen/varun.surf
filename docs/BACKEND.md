# Backend Architecture

### High-level System Overview (ASCII)
```
+---------------------+            HTTP (REST)                +------------------------------+
|  Browser Frontend   |  <------------------------------>     |  Spring Boot Backend API     |
|  (static/index.html)|  GET /api/v1/spots (SESSION cookie)   |  /api/v1/* (JSON)            |
|                     |  GET /api/v1/sponsors                 |  SessionAuthenticationFilter |
+---------------------+                                       +-------------+----------------+
                                                                            |
                                                                            |
                                       +-----------------------------------v-----------------------------------+
                                       |             AggregatorService (core orchestrator)                     |
                         +-------------+  - schedules: forecasts (3h), conditions (1m), AI (24h)                |
                         |             |  - caches: spots, forecasts (40+ models), conditions, AI, maps        |
                         |             |  - semaphore-based rate limiting (32 forecasts, 32 conditions, 16 AI) |
                         |             |  - uses Java 25 StructuredTaskScope for concurrent execution          |
                         |             +------------+--------------------+-----------------+-------------------+
                         |                          |                    |                 |
                         |                          |                    |                 |
                         |      +-------------------v----+   +-----------v--------+  +-----v-----------+
                         |      |   ForecastService      |   |CurrentConditions   |  |GoogleMapsService|
                         |      | (Windguru micro API)   |   |Service (strategies)|  |   (embed maps)  |
                         |      |   GFS & IFS models     |   +----------+---------+  +--------+--------+
                         |      +------------+-----------+              |                     |
                         |                   |                          |                     |
                         |       +-----------v-----------+   +----------v-----------+   +-----v------------+
                         |       | External: Windguru    |   | External: WiatrKadyny|   |   Google Maps    |
                         |       | micro.windguru.cz     |   | & Kiteriders stations|   |  (URL resolver)  |
                         |       +-----------------------+   +----------------------+   +------------------+
                         |
                         |                          +----------------------------+
                         +------------------------->|        AiService           |
                                                    | (Spring AI ChatClient)     |
                                                    +-------------+--------------+
                                                                  |
                                        +-------------------------v-----------------------+
                                        |       LLM provider via Spring AI (OpenAI)       |
                                        +-------------------------------------------------+

                    +-------------------+
                    | SponsorsService   |  (loads sponsors.json at startup)
                    +-------------------+
                             |
                    +--------v---------+
                    |SponsorsController|  GET /api/v1/sponsors
                    +------------------+
```

### Request/Update Flow
```
[Application Startup]
  -> JsonSpotsDataProvider loads spots.json (~780 spots)
  -> JsonSponsorsDataProvider loads sponsors.json
  -> AggregatorService.init() subscribes to spots

[Scheduler @ AggregatorService - Multiple scheduled tasks running in parallel]

  every 3h  -> fetchForecasts() (GFS model for all spots, daily + hourly)
                 -> uses StructuredTaskScope with virtual threads
                 -> semaphore limits to 32 concurrent requests
                 -> for each Spot.wgId -> ForecastService.getForecastData(id)
                 -> Windguru micro API (text format, regex-parsed)
                 -> updates forecastCache{spotId -> ForecastData(daily, Map<ForecastModel, List<Forecast>>)}

  every 1m  -> fetchCurrentConditions()
                 -> uses StructuredTaskScope with virtual threads
                 -> semaphore limits to 32 concurrent requests
                 -> for each Spot.wgId -> CurrentConditionsService.fetchCurrentConditions(id)
                 -> strategy pattern: WiatrKadyny, Podersdorf, etc.
                 -> updates currentConditions{spotId -> CurrentConditions}

  every 24h -> fetchAiForecastAnalysisEn() + fetchAiForecastAnalysisPl() (if enabled via feature flag)
                 -> uses StructuredTaskScope with virtual threads (separate scopes for EN and PL)
                 -> semaphore limits to 16 concurrent requests
                 -> for each Spot -> getHourlyForecast(wgId) [the spot's hourly forecast]
                 -> AiServiceEn.fetchAiAnalysis(spot, hourly) + AiServicePl.fetchAiAnalysis(spot, hourly)
                 -> prompt carries the full hourly forecast (no daily averages),
                    so the summary names hour ranges instead of whole days
                 -> hourly for the first 48h, every 3h after that (~72 rows over 5 days)
                 -> a spot without an hourly forecast is skipped, not summarised from its name
                 -> Spring AI ChatClient -> OpenAI
                 -> updates aiAnalysisEn{spotId -> String} and aiAnalysisPl{spotId -> String}

[Session Authentication Flow]
  Browser: GET /  →  SessionFilter creates session  →  Set-Cookie: SESSION=abc
  Browser: GET /api/v1/spots (Cookie: SESSION=abc)  →  SessionFilter validates  →  OK  →  Controller
  curl: GET /api/v1/spots (no cookie)  →  SessionFilter  →  401 empty body
  curl: GET /api/v1/health (no cookie)  →  SessionFilter exempts  →  200
  curl: GET /llms/spots.md (no cookie)  →  SessionFilter exempts  →  200 (text/markdown)

  Exempt paths (no session required):
    /api/v1/health, /actuator/**, /llms/**, static assets (.js, .css, .png, etc.)

[Client Request Flow]

  GET /api/v1/spots (requires SESSION cookie)
    -> SpotsController.spots()
    -> AggregatorService.getSpots()
    -> enriches each spot with cached data (forecasts, conditions, AI, maps)
    -> lazy-loads embedded maps on-demand (GoogleMapsService)
    -> returns Flux<Spot>

  GET /api/v1/spots/{id}
    -> SpotsController.spot(id)
    -> AggregatorService.getSpotById(id) [default: GFS model]
    -> enriches spot with cached data
    -> triggers async fetchForecastsForAllModels(id) [discovers all 40+ Windguru models]
    -> returns Mono<Spot>

  GET /api/v1/spots/{id}/{model}
    -> SpotsController.spot(id, model) [model: any valid ForecastModel key]
    -> AggregatorService.getSpotById(id, ForecastModel)
    -> enriches spot with model-specific hourly forecast + availableModels list
    -> triggers async fetchForecastsForAllModels(id)
    -> returns Mono<Spot>

  GET /api/v1/wind?hours=N
    -> SpotsController.wind(hours)
    -> AggregatorService.getWindTimeline(hours) [hourly GFS forecasts of every spot]
    -> HourlyForecastMapper projects them onto one shared grid
       [N hours, default 120, capped at 16 days and trimmed to the forecast]
    -> returns Mono<WindTimeline>

  GET /api/v1/forecast/{wgId}
    -> SpotsController.wind(wgId)
    -> AggregatorService.getHourlyForecast(wgId) [same grid, one spot, every field]
    -> returns Mono<ResponseEntity<HourlyForecast>> (404 when the spot is unknown)

  GET /api/v1/sponsors
    -> SponsorsController.sponsors()
    -> SponsorsController.mainSponsors()
    -> returns Flux<Sponsor>

  GET /llms/spots.md | /llms/spots/{id}.md | /llms/spots/{id}/wind.md | /llms/wind.md
      | /llms/countries.md | /llms/countries/{slug}.md
    -> LlmController renders Markdown from AggregatorService caches
    -> no SESSION cookie required (path is exempt in SessionAuthenticationFilter)
    -> returns text/markdown; charset=UTF-8

[Coordinates Extraction (Lazy Loading)]
  -> On spot enrichment, if coordinates not in cache
  -> scheduleCoordinatesFetch(spot) triggered
  -> GoogleMapsService.getCoordinates(spot)
  -> unshortens goo.gl URLs, extracts @lat,lon from Google Maps URLs
  -> stores in coordinates{spotId -> Coordinates}
  -> cached for subsequent requests
  -> Frontend generates embedded map iframe from coordinates
```

### Data Model (simplified)
```
Spot
├─ wgId : int (derived from windguruUrl, or deterministic hash if no Windguru station)
├─ forecastWgId : int (Windguru ID for forecasts, may use fallback URL)
├─ name / country / windguruUrl / windfinderUrl / icmUrl / webcamUrl / locationUrl
├─ windguruFallbackUrl : String (optional, alternative Windguru station for forecasts)
├─ forecast : List<Forecast> (3-day daily forecast)
├─ forecastHourly : List<Forecast> (48-hour hourly forecast, GFS or IFS)
├─ currentConditions : CurrentConditions
├─ currentConditionsHistory : List<CurrentConditions> (12-hour history, 1-min intervals)
├─ aiAnalysisEn : String (optional, AI-generated forecast summary in English)
├─ aiAnalysisPl : String (optional, AI-generated forecast summary in Polish)
├─ spotPhotoUrl : String (optional, spot photo from /images/spots/{wgId}.jpg)
├─ coordinates : Coordinates (lat, lon - lazy-loaded, used for map generation in frontend)
├─ spotInfo : SpotInfo (description, bestWind, hazards, season, waterType in English)
├─ spotInfoPL : SpotInfo (description, bestWind, hazards, season, waterType in Polish)
├─ sponsors : List<Sponsor> (list of sponsors associated with this spot)
└─ lastUpdated : String (timestamp of last update, ISO format with timezone)

Note on wgId generation:
- If windguruUrl exists: extracts numeric ID from URL
- If no windguruUrl: generates deterministic ID (9_000_000 + hash) based on name:country
- forecastWgId() uses windguruFallbackUrl if primary URL is empty

Coordinates
├─ lat : double (latitude)
└─ lon : double (longitude)

AvailableModel
├─ key : String (model key, e.g. "gfs", "ifs", "icon")
└─ name : String (display name, e.g. "GFS 13 km", "IFS 9 km")

ForecastData (internal cache structure)
├─ daily : List<Forecast> (GFS daily forecasts)
└─ hourly : Map<ForecastModel, List<Forecast>> (per-model hourly forecasts)

ForecastModel (enum - 40+ models with modelKey and displayName)
├─ GFS ("gfs", "GFS 13 km") - default
├─ IFS ("ifs", "IFS 9 km")
├─ ICON ("icon", "ICON 13 km")
├─ HRRR ("hrrr", "HRRR 3 km")
├─ AROME ("arome", "AROME 1 km")
├─ NAM ("nam", "NAM 12 km")
└─ ... (40+ total: global, European, Asia-Pacific, Americas, wave models)

Forecast
├─ time : String (hourly timestamp or daily date)
├─ windSpeed : double (knots)
├─ gust : double (knots)
├─ directionDeg : int (0-360)
├─ directionCardinal : String (N, NE, E, SE, S, SW, W, NW - via WeatherForecastMapper)
├─ tempC : double
└─ precipMm : double

CurrentConditions
├─ date : String (timestamp of last update)
├─ wind : int (wind speed in knots)
├─ gusts : int (gust speed in knots)
├─ direction : String (cardinal direction: N, NE, E, etc.)
└─ temp : int (temperature in °C)

SpotInfo
├─ description : String
├─ bestWind : String (optimal wind directions)
├─ hazards : String (safety warnings)
├─ season : String (best season for kitesurfing)
└─ waterType : String (e.g., "flatwater", "waves", "choppy")

Sponsor
├─ id : int
├─ name : String
├─ websiteUrl : String
├─ logoUrl : String
├─ isMain : boolean (indicates main sponsor status)
└─ description : String
```

### External Integrations
```
1. Windguru micro API (micro.windguru.cz)
   - Text-based forecast exports (GFS & IFS models)
   - Parsed using regex patterns in ForecastService
   - Provides daily and hourly forecasts (wind, temp, precipitation)

2. Weather Station Providers (via strategy pattern, 14 implementations)
   - WiatrKadyny (wiatrkadyny.pl) - Polish stations (Kadyny, Jastarnia, etc.)
   - Kiteriders (kiteriders.at) and scpodo.at - Austrian Podersdorf stations
   - MB Weather (mb-wetter.com) - German/Polish stations
   - Turawa (turawa.pl) - Polish Turawa lake station
   - Weather Underground PWS (api.weather.com) - Turawa South
   - Puck (Polish station)
   - Mietkow (Polish Mietków lake station)
   - Svencele (Lithuanian station)
   - TarifaArteVida (Spanish Tarifa station)
   - ElMedano (Spanish Tenerife station)
   - winds-up.com - Le Barcarès (France)
   - prasonisi.com - Prasonisi (Greece, Rhodes)
   - kitesailing.ch - Silvaplana (Switzerland)
   - HTML scraping/parsing (and JSON for the PWS) for real-time wind data
   - Strategy implementations in service/live/strategy/

3. Google Maps
   - URL unshortening (goo.gl, maps.app.goo.gl)
   - Coordinate extraction from @lat,lon format in Google Maps URLs
   - Lazy-loaded and cached per spot
   - Frontend generates embedded iframe from coordinates

4. Spring AI (optional, feature-flagged)
   - OpenAI API (gpt-4o-mini)
   - ChatClient for AI-powered forecast analysis
   - Enabled via app.feature.ai.forecast.analysis.enabled, model under spring.ai.openai
   - Multi-language support:
     - AiServiceEn: English prompts and analysis
     - AiServicePl: Polish prompts and analysis
     - Both services run in parallel every 24 hours
     - Separate caches for each language

5. ICM Meteogram Integration (Poland & Czech Republic only)
   - IcmGridMapper converts lat/lon to ICM grid coordinates
   - Uses empirically fitted coefficients for UM 4km grid
   - Validates meteogram availability via HTTP HEAD requests
   - Caches validated grid points to avoid repeated checks
   - Search radius of 8 grid points for finding valid meteograms
```

### Multi-Language Support

```
Backend (Java):
  - Spot model includes aiAnalysisEn and aiAnalysisPl fields
  - SpotInfo and SpotInfoPL for translated spot descriptions
  - AiServiceEn and AiServicePl with language-specific prompts
  - Separate scheduled tasks: fetchAiForecastAnalysisEn() and fetchAiForecastAnalysisPl()
  - Separate in-memory caches: aiAnalysisEn and aiAnalysisPl

Frontend (JavaScript):
  - translations.js contains EN and PL strings for all UI elements
  - Language stored in localStorage with key 'language'
  - Dynamic content switching:
    - AI analysis: displays aiAnalysisEn or aiAnalysisPl based on current language
    - Spot info: displays spotInfo or spotInfoPL
    - Modal titles: aiAnalysisTitle, icmForecastTitle
    - Disclaimers: aiDisclaimer
  - Language toggle button updates all content reactively
  - Applies to both views:
    - index.html (all spots view) via js/page/index.js
    - spot.html (single spot view) via js/page/spot.js

Supported Languages:
  - English (EN) - default
  - Polish (PL)

Translation Pattern:
  - Backend: language-specific service classes with template method pattern
  - Frontend: centralized translations.js with t() function lookup
  - Content selection: ternary operators based on localStorage.getItem('language')
  - Example: currentLang === 'pl' ? spot.aiAnalysisPl : spot.aiAnalysisEn
```

### Deployment/Build
```
Build:
  - Gradle 8.x with Java 25 + preview features enabled
  - ./gradlew build (build) or ./gradlew bootRun (build + run)
  - ./gradlew test (JUnit 5 + Truth assertions)
  - ./gradlew testE2e (Playwright E2E tests, headless)
  - ./gradlew testE2eNoHeadless (E2E tests with visible browser)

Configuration:
  - application.yml (NOT .properties)
  - Feature flags:
      app.feature.ai.forecast.analysis.enabled: false (default)
      app.feature.icm.vision.enabled: false (default)

Containerization:
  - Dockerfile -> multi-stage build
  - GitHub Actions CI/CD:
      ci.yml (build + tests with Gradle)
      cd.yml (CI, push to ghcr.io/pwittchen/varun.surf, deploy, GitHub release)
      deps.yml (scheduled dependency updates)
  - VPS deployment via deployment.sh script

Runtime:
  - Spring Boot 3.5.16 (Reactive WebFlux)
  - Port 8080 (default)
  - In-memory caching (no database)
  - Java 25 virtual threads via StructuredTaskScope
```

### Caching Strategy
```
In-Memory Caches (ConcurrentHashMap):
  1. forecastCache: Map<Integer, ForecastData>
     - Key: spotId (wgId)
     - Value: ForecastData(daily, Map<ForecastModel, List<Forecast>> hourly)
     - Updated: every 3 hours (scheduled)
     - Lifetime: until next scheduled update

  2. currentConditions: Map<Integer, CurrentConditions>
     - Key: spotId (wgId)
     - Value: CurrentConditions (wind, temp, direction)
     - Updated: every 1 minute (scheduled)
     - Filter: Empty conditions are not cached

  3. currentConditionsHistory: Map<Integer, EvictingQueue<CurrentConditions>>
     - Key: spotId (wgId)
     - Value: EvictingQueue with 12-hour history (720 entries at 1-min intervals)
     - Updated: every 1 minute along with currentConditions
     - Used for: wind trend charts on single spot page

  4. aiAnalysisEn: Map<Integer, String>
     - Key: spotId (wgId)
     - Value: AI-generated forecast summary in English
     - Updated: every 24 hours (if enabled)
     - Conditional: only enabled if feature flag is true

  5. aiAnalysisPl: Map<Integer, String>
     - Key: spotId (wgId)
     - Value: AI-generated forecast summary in Polish
     - Updated: every 24 hours (if enabled)
     - Conditional: only enabled if feature flag is true

  6. locationCoordinates: Map<Integer, Coordinates>
     - Key: spotId (wgId)
     - Value: Coordinates (lat, lon)
     - Updated: lazy-loaded on first request
     - Lifetime: persists until application restart
     - Frontend uses coordinates to generate embedded map iframe

  7. spotPhotos: Map<Integer, String>
     - Key: spotId (wgId)
     - Value: URL path to spot photo (/images/spots/{id}.jpg or .png)
     - Loaded: on first spot access, checks classpath resources
     - Lifetime: persists until application restart

  8. hourlyForecastCacheTimestamps: Map<Integer, Long>
     - Key: spotId (wgId)
     - Value: timestamp (milliseconds)
     - Purpose: prevent redundant multi-model fetches
     - TTL: 3 hours

  9. spots: ConcurrentMap<Integer, Spot>
     - Loaded once at startup from spots.json
     - Immutable data (name, country, URLs, spotInfo)
     - Enriched on-demand with cached data

  10. forecastModelsLocks: Map<Integer, Object>
      - Key: spotId (wgId)
      - Value: lock object for synchronizing forecast model fetches
      - Purpose: prevent concurrent fetches for same spot

Cache Invalidation:
  - No explicit invalidation (in-memory only)
  - Data refreshes automatically via scheduled tasks
  - Application restart clears all caches
```

### Concurrency & Performance
```
Java 25 StructuredTaskScope (Preview Feature):
  - Scoped concurrency for structured parallel execution
  - Virtual threads (lightweight, millions possible)
  - Subtasks tracked within scopes
  - Automatic cleanup on scope exit

Semaphore-based Rate Limiting:
  - forecastLimiter: 32 permits (max 32 concurrent Windguru API calls)
  - currentConditionsLimiter: 32 permits (max 32 concurrent station calls)
  - aiLimiter: 16 permits (max 16 concurrent LLM API calls)
  - discoveryLimiter: 16 permits (max 16 concurrent model discovery calls)
  - Prevents overwhelming external APIs
  - Ensures fair resource distribution

Reactive Patterns (Spring WebFlux):
  - Non-blocking I/O throughout the stack
  - Mono<T> for single-value async operations
  - Flux<T> for multi-value streams
  - Schedulers.boundedElastic() for blocking operations
  - backpressure handling via Reactor

Error Handling:
  - @Retryable with exponential backoff
  - @Recover methods for fallback behavior
  - Graceful degradation (missing data = empty fields)
  - Structured exception hierarchy (FetchingForecastException, etc.)

Performance Characteristics:
  - Startup: ~2-5 seconds (loads ~780 spots from JSON)
  - Forecast fetch (all spots): ~780 spots at 32 concurrent, wall clock dominated
    by the Windguru round trip (not re-measured since the spot list grew)
  - Current conditions fetch: ~3-5 seconds (fewer stations, 32 concurrent)
  - Single spot response: <50ms (cached data)
  - Embedded map lazy load: ~1-3 seconds (URL unshortening + conversion)
```

### API Endpoints Summary
```
Spots:
  GET /api/v1/spots
    - Returns all spots with cached forecasts, conditions, AI analysis
    - Excludes: currentConditionsHistory, forecastHourly (for bandwidth optimization)
    - Response: Flux<Spot> (streaming JSON)

  GET /api/v1/spots/{id}
    - Returns single spot by wgId with GFS forecast (default)
    - Includes: full currentConditionsHistory and forecastHourly
    - Triggers async fetch for all forecast models (GFS + IFS)
    - Response: Mono<Spot>

  GET /api/v1/spots/{id}/{model}
    - Returns single spot with specified forecast model (any valid modelKey)
    - Includes availableModels list for frontend model selector
    - Triggers async discovery of all forecast models if not cached
    - Response: Mono<Spot>

  GET /api/v1/wind?hours=N
    - Returns hourly wind for every spot on one shared time grid
    - Feeds the map's forecast timeline: /api/v1/spots strips forecastHourly,
      which would be megabytes across ~780 spots
    - hours: how far the grid reaches (default 120, capped at 16 days). A desktop
      map asks for the whole forecast run, a phone for the five days its slider
      has room for, and neither pays for the other's payload
    - The grid ends where the forecast stops covering most spots, so asking for
      more hours than exist returns what there is - not a tail of empty hours
      drawn from the handful of spots whose forecast reaches furthest
    - Per spot: wind, gusts and direction as arrays parallel to the grid, with
      direction as an index into WindTimeline.DIRECTIONS (roughly 100 KB gzipped
      for 120 hours across the current spot list, ~300 KB for a full run - the
      payload is per spot, so it grows as spots.json does)
    - Samples are held forward across the three-hourly stride the forecast drops
      to after ~3 days; wider gaps stay null
    - Response: Mono<WindTimeline>

  GET /api/v1/forecast/{wgId}
    - Returns one spot's full hourly forecast on the same grid: wind, gusts,
      direction, temperature, rain, cloud, pressure and waves
    - The all-spots variant carries wind alone because the map draws every spot
      at once; a single spot can afford the rest (~24 KB vs ~210 KB)
    - 404 when the spot is unknown; a known spot with no forecast cached yet
      returns 200 with no hours
    - Response: Mono<ResponseEntity<HourlyForecast>>

Sponsors:
  GET /api/v1/sponsors
    - Returns only main sponsors (isMain = true)
    - Response: Flux<Sponsor>

Health & Status:
  GET /api/v1/health
    - Simple health check endpoint
    - Response: {"status": "UP"}

  GET /api/v1/status
    - Detailed system status with uptime, version, counts
    - Response: {
        "status": "UP",
        "version": "x.y.z",
        "uptime": "1d 2h 3m 4s",
        "uptimeSeconds": 93784,
        "startTime": "2025-01-26T10:00:00Z",
        "spotsCount": 782,
        "countriesCount": 43,
        "liveStations": 15
      }

  GET /api/v1/status/history
    - Health check history (90 points, one per minute) plus a summary
    - Response: {
        "history": [...],
        "summary": {
          "totalChecks": 90,
          "successfulChecks": 90,
          "uptimePercentage": 100.0,
          "avgLatencyMs": 12.3,
          "oldestCheckTimestamp": "2025-01-26T10:00:00Z"
        },
        "currentlyHealthy": true
      }

  GET /api/v1/status/sources
    - Data sources behind the app; forecast sources are pinged live in a
      StructuredTaskScope, the rest are static definitions
    - Response: {"forecastSources": [...], "liveStationSources": [...], "spotsDataSources": [...]}

  GET /api/v1/status/forecast
    - How far the running (or last) forecast sweep got. The sweep covers the whole
      spot list and publishes spot by spot, so a freshly started instance serves
      spots without a forecast for as long as it lasts
    - Response: {"inProgress": true, "total": 782, "completed": 310, "fetched": 308,
                 "empty": 2, "failed": 0, "cached": 308, "startedAt": ..., "finishedAt": ...,
                 "elapsedMs": 8123}

SEO (server-rendered, no SESSION cookie required):
  GET /spot/{id}
    - Spot page with meta tags for crawlers and link previews

  GET /country/{countryName}
    - Country page with meta tags

  GET /sitemap.xml
    - Sitemap covering all spots and countries
    - Content-Type: application/xml

LLM-Friendly Markdown (PUBLIC, no SESSION cookie required):
  GET /llms/spots.md
    - Index of all kite spots (name, country) with links to per-spot markdown documents
    - Also lists all countries with per-country markdown links
    - Content-Type: text/markdown; charset=UTF-8

  GET /llms/spots/{wgId}.md
    - Full spot markdown: overview, current conditions (when available),
      daily forecast table, hourly forecast table (next 24 entries), links
    - Returns 404 if spot id is unknown
    - Content-Type: text/markdown; charset=UTF-8

  GET /llms/spots/{wgId}/wind.md?hours=&minWind=
    - One spot's wind hour by hour on the grid-aligned forecast (the data behind
      /api/v1/forecast/{wgId}): wind, gusts and direction per hour, with a summary
      naming the windiest hour and how many hours reach 12 kts
    - hours: how far ahead to report (default 72, trimmed to what the forecast holds)
    - minWind: knots below which hours are left out (default: every hour is listed)
    - Returns 404 if spot id is unknown
    - Content-Type: text/markdown; charset=UTF-8

  GET /llms/wind.md?minWind=&hours=&country=&limit=
    - Every spot reaching a given wind speed in the hours ahead, strongest first
      (the data behind /api/v1/wind): first and last windy hour, how many hours
      are windy, peak wind and gusts, and the direction at the peak
    - minWind: knots a spot must reach (default 12)
    - hours: how far ahead to scan (default 24, trimmed to the grid)
    - country: name or slug to restrict the search to; 404 when it matches nothing
    - limit: how many spots to list (default 20, capped at 100)
    - Content-Type: text/markdown; charset=UTF-8

  GET /llms/countries.md
    - Index of all countries with spot counts and links to per-country markdown

  GET /llms/countries/{slug}.md
    - List of spots in a given country (links to per-spot markdown)
    - {slug}: lowercased country name, spaces replaced by hyphens ("poland", "czech-republic")
    - Returns 404 if slug does not match any known country
    - Matched case-insensitively against spot country names

  All /llms/** endpoints are exempted from the SESSION filter and referenced from /llms.txt,
  so they can be crawled or fetched by LLM tooling without going through the frontend.

  The two wind documents are rendered by static methods on LlmController
  (renderWindForecast, renderWindySpots) that McpToolService also calls for its
  get_wind_forecast and find_windy_spots tools, so both surfaces render identically.

Metrics (session cookie only - no password):
  GET /api/v1/metrics
    - Application metrics: gauges, counters, timers, JVM stats, HTTP client stats
    - Includes: spots total, cache sizes, fetch counts, memory usage, threads

  GET /api/v1/metrics/history
    - Historical metrics data for charting (time-series)
    - Returns list of metric snapshots with timestamps
    - 60 points, sampled every 5 seconds (5 minutes of history)
```

### Code Organization
```
src/main/java/com/github/pwittchen/varun/
├── Application.java                      # Main entry point
├── config/                               # Spring configuration
│   ├── AsyncConfig.java                  # @Async executor config
│   ├── CacheControlFilter.java           # Cache-Control headers (cache busting)
│   ├── CorsConfig.java                   # CORS policy
│   ├── GsonConfig.java                   # JSON serialization
│   ├── LLMConfig.java                    # Spring AI ChatClient
│   ├── LoggingFilter.java                # HTTP request logging
│   ├── MetricsConfig.java                # Micrometer metrics configuration
│   ├── NettyConfig.java                  # Netty HTTP client tuning
│   ├── OkHttpClientConfig.java           # OkHttpClient bean configuration
│   ├── SecurityConfig.java               # Spring Security (HTTP Basic + session filter)
│   ├── SessionTokenService.java          # signed stateless SESSION cookie token
│   ├── SessionAuthenticationFilter.java  # Session-based API access gating
│   ├── CacheControlFilter.java           # Cache-Control headers (cache busting)
│   ├── LogAppenderConfig.java            # In-memory log appender
│   ├── McpConfig.java                    # MCP tool callback provider
│   └── WebConfig.java                    # Web MVC configuration
├── controller/                           # REST controllers
│   ├── LlmController.java                # /llms/*.md (public Markdown for LLMs)
│   ├── LogsController.java               # /api/v1/logs/*
│   ├── MetricsController.java            # /api/v1/metrics/*
│   ├── SeoController.java                # /spot/{id}, /country/{name}, /sitemap.xml
│   ├── SponsorsController.java           # /api/v1/sponsors/*
│   ├── SpotsController.java              # /api/v1/spots/*, /api/v1/wind, /api/v1/forecast/*
│   └── StatusController.java             # /api/v1/health, /api/v1/status/*
├── data/                                 # Data providers
│   ├── sponsors/
│   │   ├── JsonSponsorsDataProvider.java
│   │   └── SponsorsDataProvider.java (interface)
│   └── spots/
│       ├── JsonSpotsDataProvider.java
│       └── SpotsDataProvider.java (interface)
├── exception/                            # Custom exceptions
│   ├── FetchingAiForecastAnalysisException.java
│   ├── FetchingCurrentConditionsException.java
│   ├── FetchingForecastException.java
│   └── FetchingForecastModelsException.java
├── mapper/                               # Data transformation
│   ├── WeatherForecastMapper.java        # Degrees -> cardinal directions
│   └── HourlyForecastMapper.java         # Hourly forecasts onto one shared time grid
├── metrics/                              # Metrics instrumentation
│   ├── AggregatorServiceMetrics.java     # Service-level metrics
│   ├── HttpClientMetricsEventListener.java # OkHttp request metrics
│   └── SpotsControllerMetrics.java       # API request counters
├── model/                                # Domain models
│   ├── forecast/
│   │   ├── AvailableModel.java            # Model key + displayName for frontend
│   │   ├── Forecast.java
│   │   ├── ForecastData.java              # daily + Map<ForecastModel, List<Forecast>> hourly
│   │   ├── ForecastModel.java (enum: 40+ models - GFS, IFS, ICON, etc.)
│   │   ├── ForecastWg.java
│   │   ├── HourlyForecast.java           # One spot's hourly forecast on the shared grid
│   │   ├── WindTimeline.java             # All spots' wind on one shared hourly grid
│   │   └── IcmGrid.java                  # ICM meteogram grid coordinates
│   ├── live/                             # Live conditions
│   │   ├── CurrentConditions.java
│   │   ├── filter/CurrentConditionsEmptyFilter.java
│   │   └── filter/CurrentConditionsStalenessChecker.java
│   ├── map/
│   │   └── Coordinates.java
│   ├── sponsor/
│   │   └── Sponsor.java
│   ├── spot/
│   │   ├── Spot.java
│   │   └── SpotInfo.java
│   └── status/
│       ├── Uptime.java                   # Uptime record (seconds, formatted)
│       └── SourceHealthResult.java       # Ping result for /api/v1/status/sources
└── service/                              # Business logic
    ├── AggregatorService.java            # Core orchestrator
    ├── ai/                               # AI forecast analysis
    │   ├── AiService.java                # Base service (abstract)
    │   ├── AiServiceEn.java              # English AI analysis
    │   └── AiServicePl.java              # Polish AI analysis
    ├── forecast/
    │   ├── ForecastService.java          # Windguru API client
    │   ├── IcmGridMapper.java            # Lat/lon to ICM grid conversion
    │   ├── IcmForecastVisionService.java # ICM meteogram parsing (feature-flagged)
    │   └── ForecastAverageCalculator.java # Daily averages from hourly rows
    ├── live/                             # Live conditions
    │   ├── CurrentConditionsService.java # Station data aggregator
    │   ├── FetchCurrentConditions.java   # Strategy interface
    │   ├── FetchCurrentConditionsStrategyBase.java # Base implementation
    │   └── strategy/                     # 14 strategy implementations
    │       ├── FetchCurrentConditionsStrategyElMedano.java    # Tenerife
    │       ├── FetchCurrentConditionsStrategyLeBarcares.java  # France (winds-up.com)
    │       ├── FetchCurrentConditionsStrategyMB.java          # MB Weather
    │       ├── FetchCurrentConditionsStrategyMietkow.java     # Mietków
    │       ├── FetchCurrentConditionsStrategyPodersdorf.java  # Austria
    │       ├── FetchCurrentConditionsStrategyPodersdorfScpodo.java # Austria (scpodo.at)
    │       ├── FetchCurrentConditionsStrategyPrasonisi.java   # Greece (Rhodes)
    │       ├── FetchCurrentConditionsStrategyPuck.java        # Puck
    │       ├── FetchCurrentConditionsStrategySilvaplana.java  # Switzerland
    │       ├── FetchCurrentConditionsStrategySvencele.java    # Lithuania
    │       ├── FetchCurrentConditionsStrategyTarifaArteVida.java # Spain
    │       ├── FetchCurrentConditionsStrategyTurawa.java      # Turawa
    │       ├── FetchCurrentConditionsStrategyTurawaWunderground.java # Turawa South (PWS)
    │       └── FetchCurrentConditionsStrategyWiatrKadynyStations.java # WiatrKadyny
    ├── map/
    │   └── GoogleMapsService.java        # Maps URL converter
    ├── mcp/
    │   └── McpToolService.java           # MCP tools over the spot data
    ├── seo/
    │   └── SeoService.java               # Server-rendered pages, sitemap
    ├── metrics/
    │   └── MetricsHistoryService.java    # Metrics history (60 points, every 5s)
    ├── logs/
    │   ├── LogsService.java              # In-memory log buffer (1000 entries)
    │   ├── InMemoryLogAppender.java      # Logback appender
    │   └── LogEntry.java                 # Log entry record
    ├── health/
    │   ├── HealthHistoryService.java     # Health history (90 points, every minute)
    │   └── HealthCheckResult.java        # Health check record
    └── sponsors/
        └── SponsorsService.java          # Sponsors management

src/e2e/java/com/github/pwittchen/varun/e2e/
├── BaseE2eTest.java                      # Base class: Spring Boot + Playwright setup
├── MainPageE2eTest.java                  # Main page tests (spots, filters, modals)
├── SingleSpotE2eTest.java                # Single spot view tests (tabs, models)
├── SourcesPageE2eTest.java               # Sources page tests (data source listings)
├── McpPageE2eTest.java                   # MCP page tests (endpoint, install command)
└── StatusPageE2eTest.java                # Status page tests (system info, endpoints)
```

### E2E Testing Architecture
```
E2E Test Execution Flow:
  @BeforeAll (per test class)
    -> SpringApplication.run() starts embedded server on port 8080
    -> waitForApplicationReady() polls /api/v1/health
    -> Playwright.create() initializes browser automation
    -> browser.chromium().launch() starts Chromium (headless or visible)

  @BeforeEach (per test method)
    -> browser.newContext() creates isolated browser context
    -> context.newPage() creates new browser page
    -> page.setDefaultTimeout(60000) configures timeouts

  Test Execution
    -> page.navigate(BASE_URL + path) loads page
    -> page.locator(selector).waitFor() waits for elements
    -> page.locator(selector).click() / .fill() / etc. interacts
    -> assertThat(condition).isTrue() verifies expectations

  @AfterEach (per test method)
    -> context.close() cleans up browser context

  @AfterAll (per test class)
    -> browser.close() closes Chromium
    -> playwright.close() cleans up Playwright
    -> applicationContext.close() stops Spring Boot

Test Classes:
  MainPageE2eTest (10 tests)
    - Page loading and title verification
    - Spots grid display with cards
    - Grid/list view toggle (#columnToggle)
    - Map view toggle (#mapToggle)
    - Info modal open/close (#infoToggle, #appInfoModal)
    - Kite size calculator modal (#kiteSizeToggle, #kiteSizeModal)
    - Search filtering (#searchInput)
    - Theme toggle (#themeToggle)
    - Country dropdown filter (#dropdownButton, #dropdownMenu)

  SingleSpotE2eTest (8 tests)
    - Spot page loading (/spot/{wgId})
    - Spot container content display
    - Forecast tabs switching
    - Chart view toggle
    - Model dropdown (GFS/IFS) (#modelDropdown)
    - Info modal on spot page
    - Theme toggle on spot page
    - Navigation back to main page via logo
    - Language toggle (#languageToggle)

  StatusPageE2eTest (8 tests)
    - Status page loading (/status)
    - System status indicator (#status-indicator)
    - Service information display (version, uptime, spots count)
    - API endpoints status (.status-endpoint)
    - Refresh status button (#refresh-status)
    - Back to dashboard navigation (a[href='/'])
    - Operational status text
    - Last updated timestamp (#last-updated)

Configuration:
  - Headless mode: controlled by -Dplaywright.headless=true/false
  - Viewport: 1920x1080
  - Default timeout: 60s
  - Navigation timeout: 90s
  - Browser: Chromium (via Playwright)
```

### Metrics System
```
Micrometer-based Observability (via Spring Boot Actuator):

Gauges (current values):
  - varun.spots.total              # Total spots loaded
  - varun.countries.total          # Unique countries
  - varun.live_stations.active     # Stations with live data
  - varun.cache.forecasts.size     # Forecast cache entries
  - varun.cache.conditions.size    # Conditions cache entries
  - varun.fetch.forecasts.last_timestamp  # Last forecast fetch
  - varun.fetch.conditions.last_timestamp # Last conditions fetch

Counters (cumulative):
  - varun.fetch.forecasts.total/success/failure  # Forecast fetch counts
  - varun.fetch.conditions.total/success/failure # Conditions fetch counts
  - varun.fetch.ai.total/success/failure         # AI analysis fetch counts
  - varun.api.spots.requests       # GET /api/v1/spots requests
  - varun.api.spot.requests        # GET /api/v1/spots/{id} requests

Timers (duration tracking):
  - varun.fetch.forecasts.duration   # Time to fetch all forecasts
  - varun.fetch.conditions.duration  # Time to fetch all conditions
  - varun.fetch.ai.duration          # Time to fetch AI analysis

HTTP Client Metrics:
  - varun.http.client.active_requests      # In-flight requests
  - varun.http.client.requests.total       # Total outgoing requests
  - varun.http.client.requests.success     # Successful responses
  - varun.http.client.requests.failed      # Failed requests
  - varun.http.client.request.duration     # Request timing
  - varun.http.client.dns.duration         # DNS resolution timing
  - varun.http.client.connect.duration     # TCP connect timing

JVM Metrics (auto-collected):
  - jvm.memory.used/max (heap/nonheap)
  - jvm.threads.live/peak/daemon
  - jvm.gc.pause (count, total time)
  - process.cpu.usage, system.cpu.usage
  - process.uptime

Metrics History:
  - MetricsHistoryService stores periodic snapshots
  - Used for time-series charts on /status page
  - In-memory storage, cleared on restart
```

### Legend
- Rectangles: components in your codebase
- Rounded rectangles: external services
- Solid arrows: synchronous calls (HTTP or method)
- Dashed arrows: scheduled/background processes

## Related Documentation

- **CLAUDE.md**: Backend architecture, API endpoints, data models
- **FRONTEND.md**: Frontend architecture high-level overview (same directory)
- **README.md**: User guide, build instructions, deployment

## Contact & Contributing

For backend-related issues, feature requests, or contributions:
- **GitHub Issues**: https://github.com/pwittchen/varun.surf/issues
- **Email**: hello@varun.surf
- **Pull Requests**: Welcome! Follow existing code style and conventions

---

**Last Updated**: March 2026
**Maintained By**: @pwittchen
