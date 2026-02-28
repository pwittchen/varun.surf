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
                         +-------------+  - schedules: forecasts (3h), conditions (1m), AI (8h)                |
                         |             |  - caches: spots, forecasts (GFS/IFS), conditions, AI, maps           |
                         |             |  - semaphore-based rate limiting (32 forecasts, 32 conditions, 16 AI) |
                         |             |  - uses Java 24 StructuredTaskScope for concurrent execution          |
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
                    |SponsorsController|  GET /api/v1/sponsors, /api/v1/sponsors/{id}
                    +------------------+
```

### Request/Update Flow
```
[Application Startup]
  -> JsonSpotsDataProvider loads spots.json (74+ spots)
  -> JsonSponsorsDataProvider loads sponsors.json
  -> AggregatorService.init() subscribes to spots

[Scheduler @ AggregatorService - Multiple scheduled tasks running in parallel]

  every 3h  -> fetchForecasts() (GFS model for all spots, daily + hourly)
                 -> uses StructuredTaskScope with virtual threads
                 -> semaphore limits to 32 concurrent requests
                 -> for each Spot.wgId -> ForecastService.getForecastData(id)
                 -> Windguru micro API (text format, regex-parsed)
                 -> updates forecastCache{spotId -> ForecastData(daily, hourlyGfs, hourlyIfs)}

  every 1m  -> fetchCurrentConditions()
                 -> uses StructuredTaskScope with virtual threads
                 -> semaphore limits to 32 concurrent requests
                 -> for each Spot.wgId -> CurrentConditionsService.fetchCurrentConditions(id)
                 -> strategy pattern: WiatrKadyny, Podersdorf, etc.
                 -> updates currentConditions{spotId -> CurrentConditions}

  every 8h  -> fetchAiForecastAnalysisEn() + fetchAiForecastAnalysisPl() (if enabled via feature flag)
                 -> uses StructuredTaskScope with virtual threads (separate scopes for EN and PL)
                 -> semaphore limits to 16 concurrent requests
                 -> for each Spot -> AiServiceEn.fetchAiAnalysis(spot) + AiServicePl.fetchAiAnalysis(spot)
                 -> Spring AI ChatClient -> OpenAI
                 -> updates aiAnalysisEn{spotId -> String} and aiAnalysisPl{spotId -> String}

[Session Authentication Flow]
  Browser: GET /  →  SessionFilter creates session  →  Set-Cookie: SESSION=abc
  Browser: GET /api/v1/spots (Cookie: SESSION=abc)  →  SessionFilter validates  →  OK  →  Controller
  curl: GET /api/v1/spots (no cookie)  →  SessionFilter  →  401 empty body
  curl: GET /api/v1/health (no cookie)  →  SessionFilter exempts  →  200
  curl: GET /api/v1/session  →  SessionFilter exempts  →  Set-Cookie: SESSION=abc

  Exempt paths (no session required):
    /api/v1/health, /api/v1/session, /actuator/**, static assets (.js, .css, .png, etc.)

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
    -> triggers async fetchForecastsForAllModels(id) [GFS + IFS hourly forecasts]
    -> returns Mono<Spot>

  GET /api/v1/spots/{id}/{model}
    -> SpotsController.spot(id, model) [model: "gfs" or "ifs"]
    -> AggregatorService.getSpotById(id, ForecastModel)
    -> enriches spot with model-specific hourly forecast
    -> triggers async fetchForecastsForAllModels(id)
    -> returns Mono<Spot>

  GET /api/v1/sponsors
    -> SponsorsController.sponsors()
    -> SponsorsController.mainSponsors()
    -> returns Flux<Sponsor>

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

ForecastData (internal cache structure)
├─ daily : List<Forecast> (GFS daily forecasts)
├─ hourlyGfs : List<Forecast> (GFS hourly forecasts)
└─ hourlyIfs : List<Forecast> (IFS hourly forecasts)

ForecastModel (enum)
├─ GFS (Global Forecast System - NOAA)
└─ IFS (Integrated Forecast System - ECMWF)

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

2. Weather Station Providers (via strategy pattern)
   - WiatrKadyny (wiatrkadyny.pl) - Polish stations (Kadyny, Jastarnia, etc.)
   - Kiteriders (kiteriders.at) - Austrian Podersdorf station
   - MB Weather (mb-wetter.com) - German/Polish stations
   - Turawa (turawa.pl) - Polish Turawa lake station
   - Puck (Polish station)
   - Mietkow (Polish Mietków lake station)
   - Svencele (Lithuanian station)
   - TarifaArteVida (Spanish Tarifa station)
   - ElMedano (Spanish Tenerife station)
   - HTML scraping/parsing for real-time wind data
   - Strategy implementations in service/live/strategy/

3. Google Maps
   - URL unshortening (goo.gl, maps.app.goo.gl)
   - Coordinate extraction from @lat,lon format in Google Maps URLs
   - Lazy-loaded and cached per spot
   - Frontend generates embedded iframe from coordinates

4. Spring AI (optional, feature-flagged)
   - OpenAI API (gpt-4o-mini)
   - ChatClient for AI-powered forecast analysis
   - Configured via application.yml (app.ai.provider)
   - Multi-language support:
     - AiServiceEn: English prompts and analysis
     - AiServicePl: Polish prompts and analysis
     - Both services run in parallel every 8 hours
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
    - index.html (all spots view) via script.js
    - spot.html (single spot view) via script-spot.js

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
  - Gradle 8.x with Java 24 + preview features enabled
  - ./gradlew build (build) or ./gradlew bootRun (build + run)
  - ./gradlew test (JUnit 5 + Truth assertions)
  - ./gradlew testE2e (Playwright E2E tests, headless)
  - ./gradlew testE2eNoHeadless (E2E tests with visible browser)

Configuration:
  - application.yml (NOT .properties)
  - Feature flags:
      app.feature.ai.forecast.analysis.enabled: false (default)
      app.feature.ai.forecast.analysis.enabled: false (default)

Containerization:
  - Dockerfile -> multi-stage build
  - GitHub Actions CI/CD:
      gradle.yml (Java CI with Gradle)
      docker.yml (push to ghcr.io/pwittchen/varun.surf)
  - VPS deployment via deployment.sh script

Runtime:
  - Spring Boot 3.5.5 (Reactive WebFlux)
  - Port 8080 (default)
  - In-memory caching (no database)
  - Java 24 virtual threads via StructuredTaskScope
```

### Caching Strategy
```
In-Memory Caches (ConcurrentHashMap):
  1. forecastCache: Map<Integer, ForecastData>
     - Key: spotId (wgId)
     - Value: ForecastData(daily, hourlyGfs, hourlyIfs)
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
     - Updated: every 8 hours (if enabled)
     - Conditional: only enabled if feature flag is true

  5. aiAnalysisPl: Map<Integer, String>
     - Key: spotId (wgId)
     - Value: AI-generated forecast summary in Polish
     - Updated: every 8 hours (if enabled)
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
     - Purpose: prevent redundant IFS model fetches
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
Java 24 StructuredTaskScope (Preview Feature):
  - Scoped concurrency for structured parallel execution
  - Virtual threads (lightweight, millions possible)
  - Subtasks tracked within scopes
  - Automatic cleanup on scope exit

Semaphore-based Rate Limiting:
  - forecastLimiter: 32 permits (max 32 concurrent Windguru API calls)
  - currentConditionsLimiter: 32 permits (max 32 concurrent station calls)
  - aiLimiter: 16 permits (max 16 concurrent LLM API calls)
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
  - Startup: ~2-5 seconds (loads 90+ spots from JSON)
  - Forecast fetch (all spots): ~10-20 seconds (90 spots, 32 concurrent)
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
    - Returns single spot with specified forecast model (gfs or ifs)
    - Triggers async fetch for all forecast models if not cached
    - Response: Mono<Spot>

Sponsors:
  GET /api/v1/sponsors
    - Returns only main sponsors (isMain = true)
    - Response: Flux<Sponsor>

  GET /api/v1/sponsors/{id}
    - Returns single sponsor by id
    - Response: Mono<Sponsor>

Session:
  GET /api/v1/session
    - Creates/initializes session, returns SESSION cookie
    - Response: {"status": "OK"}
    - No session required (exempt from session filter)
    - Useful for programmatic API access (curl, scripts)

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
        "spotsCount": 90,
        "countriesCount": 25,
        "liveStations": 15
      }

Metrics (password-protected via X-Metrics-Password header):
  GET /api/v1/metrics
    - Application metrics: gauges, counters, timers, JVM stats, HTTP client stats
    - Includes: spots total, cache sizes, fetch counts, memory usage, threads

  GET /api/v1/metrics/history
    - Historical metrics data for charting (time-series)
    - Returns list of metric snapshots with timestamps

  POST /api/v1/metrics/auth
    - Authenticate for metrics access
    - Body: {"password": "xxx"}
    - Response: {"authenticated": true}
```

### Code Organization
```
src/main/java/com/github/pwittchen/varun/
├── Application.java                      # Main entry point
├── config/                               # Spring configuration
│   ├── AsyncConfig.java                  # @Async executor config
│   ├── CorsConfig.java                   # CORS policy
│   ├── GsonConfig.java                   # JSON serialization
│   ├── LLMConfig.java                    # Spring AI ChatClient
│   ├── LoggingFilter.java                # HTTP request logging
│   ├── MetricsConfig.java                # Micrometer metrics configuration
│   ├── NettyConfig.java                  # Netty HTTP client tuning
│   ├── OkHttpClientConfig.java           # OkHttpClient bean configuration
│   ├── SecurityConfig.java               # Spring Security (HTTP Basic + session filter)
│   ├── SessionConfig.java                # SESSION cookie configuration
│   ├── SessionAuthenticationFilter.java  # Session-based API access gating
│   └── WebConfig.java                    # Web MVC configuration
├── controller/                           # REST controllers
│   ├── MetricsController.java            # /api/v1/metrics/*
│   ├── SessionController.java            # /api/v1/session (session init)
│   ├── SponsorsController.java           # /api/v1/sponsors/*
│   ├── SpotsController.java              # /api/v1/spots/*
│   └── StatusController.java             # /api/v1/health, /api/v1/status
├── data/                                 # Data layer
│   └── provider/                         # Data providers
│       ├── sponsors/
│       │   ├── JsonSponsorsDataProvider.java
│       │   └── SponsorsDataProvider.java (interface)
│       └── spots/
│           ├── JsonSpotsDataProvider.java
│           └── SpotsDataProvider.java (interface)
├── exception/                            # Custom exceptions
│   ├── FetchingAiForecastAnalysisException.java
│   ├── FetchingCurrentConditionsException.java
│   ├── FetchingForecastException.java
│   └── FetchingForecastModelsException.java
├── mapper/                               # Data transformation
│   └── WeatherForecastMapper.java        # Degrees -> cardinal directions
├── metrics/                              # Metrics instrumentation
│   ├── AggregatorServiceMetrics.java     # Service-level metrics
│   ├── HttpClientMetricsEventListener.java # OkHttp request metrics
│   └── SpotsControllerMetrics.java       # API request counters
├── model/                                # Domain models
│   ├── forecast/
│   │   ├── Forecast.java
│   │   ├── ForecastData.java
│   │   ├── ForecastModel.java (enum: GFS, IFS)
│   │   ├── ForecastWg.java
│   │   └── IcmGrid.java                  # ICM meteogram grid coordinates
│   ├── live/                             # Live conditions
│   │   ├── CurrentConditions.java
│   │   └── filter/CurrentConditionsEmptyFilter.java
│   ├── map/
│   │   └── Coordinates.java
│   ├── sponsor/
│   │   └── Sponsor.java
│   ├── spot/
│   │   ├── Spot.java
│   │   └── SpotInfo.java
│   └── status/
│       └── Uptime.java                   # Uptime record (seconds, formatted)
└── service/                              # Business logic
    ├── AggregatorService.java            # Core orchestrator
    ├── MetricsHistoryService.java        # Metrics history storage for charts
    ├── ai/                               # AI forecast analysis
    │   ├── AiService.java                # Base service (abstract)
    │   ├── AiServiceEn.java              # English AI analysis
    │   └── AiServicePl.java              # Polish AI analysis
    ├── forecast/
    │   ├── ForecastService.java          # Windguru API client
    │   └── IcmGridMapper.java            # Lat/lon to ICM grid conversion
    ├── live/                             # Live conditions
    │   ├── CurrentConditionsService.java # Station data aggregator
    │   ├── FetchCurrentConditions.java   # Strategy interface
    │   ├── FetchCurrentConditionsStrategyBase.java # Base implementation
    │   └── strategy/                     # Strategy implementations
    │       ├── FetchCurrentConditionsStrategyElMedano.java    # Tenerife
    │       ├── FetchCurrentConditionsStrategyMB.java          # MB Weather
    │       ├── FetchCurrentConditionsStrategyMietkow.java     # Mietków
    │       ├── FetchCurrentConditionsStrategyPodersdorf.java  # Austria
    │       ├── FetchCurrentConditionsStrategyPuck.java        # Puck
    │       ├── FetchCurrentConditionsStrategySvencele.java    # Lithuania
    │       ├── FetchCurrentConditionsStrategyTarifaArteVida.java # Spain
    │       ├── FetchCurrentConditionsStrategyTurawa.java      # Turawa
    │       └── FetchCurrentConditionsStrategyWiatrKadynyStations.java # WiatrKadyny
    ├── map/
    │   └── GoogleMapsService.java        # Maps URL converter
    └── sponsors/
        └── SponsorsService.java          # Sponsors management

src/e2e/java/com/github/pwittchen/varun/e2e/
├── BaseE2eTest.java                      # Base class: Spring Boot + Playwright setup
├── MainPageE2eTest.java                  # Main page tests (spots, filters, modals)
├── SingleSpotE2eTest.java                # Single spot view tests (tabs, models)
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
- **prompts/new-kite-spot.md**: Adding new kite spots

## Contact & Contributing

For backend-related issues, feature requests, or contributions:
- **GitHub Issues**: https://github.com/pwittchen/varun.surf/issues
- **Email**: hello@varun.surf
- **Pull Requests**: Welcome! Follow existing code style and conventions

---

**Last Updated**: January 2026
**Maintained By**: @pwittchen
