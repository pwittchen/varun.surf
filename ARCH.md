# Project Architecture

### High-level System Overview (ASCII)
```
+---------------------+            HTTP (REST)                +---------------------------+
|  Browser Frontend   |  <------------------------------>     |  Spring Boot Backend API  |
|  (static/index.html)|  GET /api/v1/spots                    |  /api/v1/* (JSON)         |
|                     |  GET /api/v1/sponsors                 |                           |
+---------------------+                                       +-------------+-------------+
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
                                        |  LLM provider via Spring AI (OpenAI or Ollama) |
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
                 -> Spring AI ChatClient -> OpenAI or Ollama
                 -> updates aiAnalysisEn{spotId -> String} and aiAnalysisPl{spotId -> String}

[Client Request Flow]

  GET /api/v1/spots
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
├─ wgId : int (derived from windguruUrl, exposed via @JsonProperty)
├─ name / country / windguruUrl / windfinderUrl / icmUrl / webcamUrl / locationUrl
├─ forecast : List<Forecast> (3-day daily forecast)
├─ forecastHourly : List<Forecast> (48-hour hourly forecast, GFS or IFS)
├─ currentConditions : CurrentConditions
├─ aiAnalysisEn : String (optional, AI-generated forecast summary in English)
├─ aiAnalysisPl : String (optional, AI-generated forecast summary in Polish)
├─ spotPhotoUrl : String (optional, spot photo URL)
├─ coordinates : Coordinates (lat, lon - lazy-loaded, used for map generation in frontend)
├─ spotInfo : SpotInfo (description, bestWind, hazards, season, waterType in English)
├─ spotInfoPL : SpotInfo (description, bestWind, hazards, season, waterType in Polish)
├─ sponsors : List<Sponsor> (list of sponsors associated with this spot)
└─ lastUpdated : String (timestamp of last update, ISO format with timezone)

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
   - WiatrKadyny (wiatrkadyny.pl) - Polish stations
   - Kiteriders (kiteriders.at) - Austrian Podersdorf station
   - MB Weather (mb-wetter.com) - German/Polish stations
   - Turawa (turawa.pl) - Polish Turawa lake station
   - HTML scraping/parsing for real-time wind data
   - Strategy implementations in service/live/strategy/

3. Google Maps
   - URL unshortening (goo.gl, maps.app.goo.gl)
   - Coordinate extraction from @lat,lon format in Google Maps URLs
   - Lazy-loaded and cached per spot
   - Frontend generates embedded iframe from coordinates

4. Spring AI (optional, feature-flagged)
   - OpenAI API (gpt-4o-mini) OR Ollama (smollm2:135m)
   - ChatClient for AI-powered forecast analysis
   - Configured via application.yml (app.ai.provider)
   - Multi-language support:
     - AiServiceEn: English prompts and analysis
     - AiServicePl: Polish prompts and analysis
     - Both services run in parallel every 8 hours
     - Separate caches for each language
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

Configuration:
  - application.yml (NOT .properties)
  - Feature flags:
      app.feature.ai.forecast.analysis.enabled: false (default)
      app.ai.provider: ollama (or openai)

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

  3. aiAnalysisEn: Map<Integer, String>
     - Key: spotId (wgId)
     - Value: AI-generated forecast summary in English
     - Updated: every 8 hours (if enabled)
     - Conditional: only enabled if feature flag is true

  4. aiAnalysisPl: Map<Integer, String>
     - Key: spotId (wgId)
     - Value: AI-generated forecast summary in Polish
     - Updated: every 8 hours (if enabled)
     - Conditional: only enabled if feature flag is true

  5. coordinates: Map<Integer, Coordinates>
     - Key: spotId (wgId)
     - Value: Coordinates (lat, lon)
     - Updated: lazy-loaded on first request
     - Lifetime: persists until application restart
     - Frontend uses coordinates to generate embedded map iframe

  6. hourlyForecastCacheTimestamps: Map<Integer, Long>
     - Key: spotId (wgId)
     - Value: timestamp (milliseconds)
     - Purpose: prevent redundant IFS model fetches
     - TTL: 3 hours

  7. spots: AtomicReference<List<Spot>>
     - Loaded once at startup from spots.json
     - Immutable data (name, country, URLs, spotInfo)
     - Enriched on-demand with cached data

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
    - Response: Flux<Spot> (streaming JSON)

  GET /api/v1/spots/{id}
    - Returns single spot by wgId with GFS forecast (default)
    - Triggers async fetch for GFS model (cached for 3h)
    - Response: Mono<Spot>

  GET /api/v1/spots/{id}/{model}
    - Returns single spot with specified forecast model (gfs or ifs)
    - Triggers async fetch for GFS or IFS model if not cached
    - Response: Mono<Spot>

Sponsors:
  GET /api/v1/sponsors
    - Returns only main sponsors (isMain = true)
    - Response: Flux<Sponsor>

  GET /api/v1/sponsors/{id}
    - Returns single sponsor by id
    - Response: Mono<Sponsor>

Health:
  GET /api/v1/health
    - Simple health check endpoint
    - Response: {"status": "UP"}
```

### Code Organization
```
src/main/java/com/github/pwittchen/varun/
├── Application.java                      # Main entry point
├── component/                            # Shared components
│   └── http/
│       └── HttpClientProxy.java          # OkHttp client wrapper
├── config/                               # Spring configuration
│   ├── AsyncConfig.java                  # @Async executor config
│   ├── CorsConfig.java                   # CORS policy
│   ├── GsonConfig.java                   # JSON serialization
│   ├── LLMConfig.java                    # Spring AI ChatClient
│   ├── LoggingFilter.java                # HTTP request logging
│   ├── NettyConfig.java                  # Netty HTTP client tuning
│   └── WebConfig.java                    # Web MVC configuration
├── controller/                           # REST controllers
│   ├── SponsorsController.java           # /api/v1/sponsors/*
│   └── SpotsController.java              # /api/v1/spots/*
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
├── model/                                # Domain models
│   ├── forecast/
│   │   ├── Forecast.java
│   │   ├── ForecastData.java
│   │   ├── ForecastModel.java (enum: GFS, IFS)
│   │   └── ForecastWg.java
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
│       └── Health.java
└── service/                              # Business logic
    ├── AggregatorService.java            # Core orchestrator
    ├── ai/                               # AI forecast analysis
    │   ├── AiService.java                # Base service (abstract)
    │   ├── AiServiceEn.java              # English AI analysis
    │   └── AiServicePl.java              # Polish AI analysis
    ├── forecast/
    │   └── ForecastService.java          # Windguru API client
    ├── live/                             # Live conditions
    │   ├── CurrentConditionsService.java # Station data aggregator
    │   └── strategy/                     # Strategy pattern for stations
    │       ├── FetchCurrentConditions.java (interface)
    │       ├── FetchCurrentConditionsStrategyBase.java
    │       ├── FetchCurrentConditionsStrategyMB.java
    │       ├── FetchCurrentConditionsStrategyPodersdorf.java
    │       ├── FetchCurrentConditionsStrategyTurawa.java
    │       └── FetchCurrentConditionsStrategyWiatrKadynyStations.java
    ├── map/
    │   └── GoogleMapsService.java        # Maps URL converter
    └── sponsors/
        └── SponsorsService.java          # Sponsors management
```

### Legend
- Rectangles: components in your codebase
- Rounded rectangles: external services
- Solid arrows: synchronous calls (HTTP or method)
- Dashed arrows: scheduled/background processes
