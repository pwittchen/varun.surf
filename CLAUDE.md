# CLAUDE.md - AI Agent Context

## Project Overview

**varun.surf** is a weather forecast and real-time wind conditions dashboard designed specifically for kitesurfers. The application aggregates forecast data and live wind conditions for multiple kite spots worldwide, presenting them in a unified, easy-to-browse interface.

**Live URL**: https://varun.surf

## Tech Stack

- **Backend**: Spring Boot 3.5.16 (Reactive WebFlux)
- **Java**: Version 25 with preview features enabled
- **Build Tool**: Gradle
- **Frontend Build**: Bun (replaces npm for faster builds)
- **Dependencies**:
  - Spring WebFlux (reactive, non-blocking)
  - Spring Security (authentication for protected endpoints)
  - Spring AI 1.0.2 (OpenAI integration for forecast analysis)
  - Spring AI MCP server (WebFlux SSE transport)
  - Spring Actuator with Micrometer/Prometheus metrics
  - Spring AOP (retries via @Retryable)
  - OkHttp 4.12.0 (HTTP client)
  - Gson 2.14.0 (JSON serialization)
  - JavaTuples 1.2
  - Guava 33.7.1-jre (EvictingQueue for metrics/logs history)
  - spring-dotenv 4.0.0 (environment variable loading)
- **Containerization**: Docker with GHCR deployment
- **Frontend**: Vanilla JavaScript (static/index.html)
- **Testing**: JUnit 5, Truth 1.4.5, MockWebServer, Playwright 1.62.0 (E2E), Jacoco 0.8.13 (coverage)

## Architecture Overview

### High-Level Flow

```
Browser Frontend (static/index.html)
    ↓ (HTTP REST)
Spring Boot Backend API (/api/v1/*)
    ├─→ /api/v1/spots (all spots with forecasts)
    ├─→ /api/v1/spots/{id} (single spot, triggers IFS fetch)
    ├─→ /api/v1/spots/{id}/{model} (single spot with GFS or IFS forecast)
    ├─→ /api/v1/wind (hourly wind for all spots on one shared grid, for the maps)
    ├─→ /api/v1/forecast/{wgId} (one spot's full hourly forecast: wind, temp, rain, cloud, pressure, waves)
    ├─→ /api/v1/sponsors (sponsors and main sponsors)
    ├─→ /api/v1/status (system status, uptime, counts)
    ├─→ /api/v1/status/history (health check history, uptime %, latency)
    ├─→ /api/v1/status/sources (forecast, live station and spots data sources)
    ├─→ /api/v1/metrics (application metrics, password-protected)
    ├─→ /api/v1/logs (application logs, password-protected)
    ├─→ /api/v1/health (health check)
    └─→ /llms/*.md (LLM-friendly Markdown for spots and countries)
    ↓
AggregatorService (core orchestrator with Java 25 StructuredTaskScope)
    ├─→ ForecastService → Windguru micro API (GFS & IFS models)
    ├─→ CurrentConditionsService → Multiple station providers (14 strategies)
    ├─→ GoogleMapsService → Google Maps (URL resolver, coordinates)
    ├─→ AiServiceEn/AiServicePl → LLM (OpenAI, language-specific)
    ├─→ MetricsHistoryService → Prometheus metrics with history
    ├─→ LogsService → In-memory log buffer (last 1000 entries)
    └─→ HealthHistoryService → Health check history (90 data points)
```

### Key Components

1. **AggregatorService** (`service/AggregatorService.java`)
   - Central orchestrator with multiple scheduled tasks:
     - Forecasts: every 3 hours (GFS model, daily + hourly)
     - Current conditions: every 1 minute
     - ICM meteograms: every 3 hours for Polish/Czech spots (if enabled)
     - AI analysis: every 8 hours, EN and PL separately (if enabled)
   - Uses Java 25 StructuredTaskScope with virtual threads for concurrent execution
   - Semaphore-based rate limiting (32 forecasts, 32 conditions, 16 AI, 16 model discovery)
   - Maintains multiple in-memory caches (ConcurrentHashMap):
     - forecastCache: Map<Integer, ForecastData(daily, Map<ForecastModel, List<Forecast>>)>
     - currentConditions: Map<Integer, CurrentConditions>
     - currentConditionsHistory: Map<Integer, EvictingQueue<CurrentConditions>> (12h history)
     - aiAnalysisEn/aiAnalysisPl: Map<Integer, String> (language-specific)
     - hourlyForecastCacheTimestamps: Map<Integer, Long> (3h TTL)
     - locationCoordinates: Map<Integer, Coordinates>
     - icmUrls: Map<Integer, String> (resolved ICM meteogram URLs)
     - spotPhotos: Map<Integer, String>
     - spots: ConcurrentMap<Integer, Spot>
   - On-demand IFS model fetching when single spot is accessed

2. **ForecastService** (`service/forecast/ForecastService.java`)
   - Fetches weather forecasts from Windguru micro API
   - Supports 40+ forecast models dynamically (GFS, IFS, ICON, NAM, HRRR, AROME, etc.)
   - All available Windguru models defined in `ForecastModel` enum with `modelKey` and `displayName`
   - Parses text-based exports using regex patterns
   - Returns ForecastData with daily and per-model hourly forecasts
   - Data includes: wind speed/gust, direction (deg + cardinal), temperature, precipitation

3. **CurrentConditionsService** (`service/live/CurrentConditionsService.java`)
   - Uses strategy pattern for different weather station providers
   - 14 strategy implementations for weather stations:
     - WiatrKadynyStations (Poland - multiple locations)
     - Podersdorf (Austria - Neusiedler See)
     - PodersdorfScpodo (Austria - Podersdorf, scpodo.at fallback)
     - Puck (Poland)
     - Turawa (Poland)
     - TurawaWunderground (Poland - Turawa South, Weather Underground PWS)
     - MB (Poland - Mrzeżyno)
     - TarifaArteVida (Spain - Tarifa)
     - Mietkow (Poland)
     - Svencele (Lithuania)
     - ElMedano (Spain - Tenerife)
     - LeBarcares (France - winds-up.com)
     - Prasonisi (Greece - Rhodes)
     - Silvaplana (Switzerland - kitesailing.ch)
   - Scrapes/parses real-time wind data from station websites
   - Filters empty conditions (not cached)
   - Returns current wind speed, gusts, direction, temperature, timestamp

4. **AiService** (`service/ai/AiService.java`, `AiServiceEn.java`, `AiServicePl.java`)
   - Optional feature (disabled by default via feature flag)
   - Language-specific implementations (English and Polish)
   - Generates AI-powered forecast summaries using Spring AI ChatClient
   - Uses OpenAI (gpt-4o-mini) as the LLM provider
   - Professional kitesurfing analyst prompt with kite size recommendations:
     - Below 8 kts: not rideable
     - 8-11 kts: foil only
     - 12-14 kts: large kite (12-17 m²)
     - 15-18 kts: medium kite (11-12 m²)
     - 19-25 kts: small kite (9-10 m²)
     - 28+ kts: very small kite (5-7 m²)
   - Streams responses with 15s timeout and 3 retries
   - Supports an optional spot-specific prompt fragment via `SpotInfo.llmComment`
     in `spots.json`, injected into the prompt as ADDITIONAL SPOT-SPECIFIC CONTEXT.
     Each service reads the comment from the `SpotInfo` in its own language
     (`spotInfo` for EN, `spotInfoPL` for PL) and falls back to the English one
     when the translation carries none. Use it for local effects the gridded
     forecast misses - venturi acceleration, thermal winds, direction-only spots
   - The only forecast data in the prompt is the spot's `HourlyForecast` (the
     same data `/api/v1/forecast/{wgId}` serves): wind, gusts, direction, temperature,
     rain, cloud, pressure and waves. Daily averages were dropped: they hide the
     hours a session actually happens in
   - Rows are hourly for the first `AiService.DETAILED_HOURS` (48) hours, then
     every `COARSE_STRIDE` (3) hours - which is the resolution Windguru itself
     drops to after ~3 days, so the skipped rows would only repeat held-forward
     values
   - Night rows are dropped: only hours between `FIRST_DAY_HOUR` (6) and
     `LAST_DAY_HOUR` (21) reach the prompt, and the prompt tells the model to
     analyse only the hours present, so it never names a 03:00 window. ~48 rows
     over 5 days (a third fewer than with nights). The window is generous on
     purpose - the same constants serve a Baltic summer evening and a Canarian
     winter morning
   - The three wave columns are dropped for spots with no wave data (inland
     lakes), so the header never promises a column the rows don't carry
   - A spot with no hourly forecast gets no analysis at all (rather than one
     written from its name), since nothing else in the prompt carries a forecast

5. **GoogleMapsService** (`service/map/GoogleMapsService.java`)
   - Converts location URLs to Coordinates objects
   - Unshortens goo.gl and maps.app.goo.gl URLs (max 5 redirects)
   - Extracts coordinates from @lat,lng format
   - Coordinates cached in locationCoordinates map
   - Lazy-loaded on first spot access

6. **SpotsController** (`controller/SpotsController.java`)
   - REST API endpoints:
     - `GET /api/v1/spots` - all spots with cached data
     - `GET /api/v1/spots/{id}` - single spot (GFS, triggers async IFS fetch)
     - `GET /api/v1/spots/{id}/{model}` - single spot with model selection (gfs/ifs)
     - `GET /api/v1/wind?hours=N` - hourly wind for every spot on one shared time grid
     - `GET /api/v1/forecast/{wgId}` - one spot's full hourly forecast on the same grid (404 when unknown)
   - Returns reactive types: `Flux<Spot>` and `Mono<Spot>`
   - Enriches spots with cached forecasts, conditions, AI analysis
   - Uses SpotsControllerMetrics for request tracking
   - `/api/v1/spots` strips `forecastHourly` (too large for ~230 spots), so the
     map's forecast timeline reads `/api/v1/wind` instead: `HourlyForecastMapper`
     projects every spot's hourly GFS forecast onto one shared grid and emits
     wind/gusts/direction as parallel arrays (~30 KB gzipped for 120 hours)
   - `hours` says how far the grid should reach (default 120, capped at 16 days):
     a desktop map asks for the whole forecast run, a phone for the five days its
     slider has room for, and neither pays for the other's payload. The grid ends
     where the forecast stops covering most spots, so an over-long request is
     answered with what there is rather than a tail of empty hours

7. **StatusController** (`controller/StatusController.java`)
   - REST API endpoints:
     - `GET /api/v1/health` - simple health check
     - `GET /api/v1/status` - detailed status (version, uptime, spots/countries/live stations count)
     - `GET /api/v1/status/history` - health check history with uptime % and average latency
     - `GET /api/v1/status/sources` - forecast, live station and spots data sources (forecast sources pinged live)
   - Returns application status and statistics

8. **MetricsController** (`controller/MetricsController.java`)
   - REST API endpoints:
     - `GET /api/v1/metrics` - application metrics (password-protected)
     - `GET /api/v1/metrics/history` - metrics history over time (60 points, sampled every 5s)
   - Exposes gauges, counters, timers, JVM metrics, HTTP client metrics

9. **LogsController** (`controller/LogsController.java`)
   - REST API endpoints:
     - `GET /api/v1/logs` - application logs (password-protected)
     - `GET /api/v1/logs?level={level}` - filter logs by level (ERROR, WARN, INFO, DEBUG, TRACE)
   - Returns last 1000 log entries from in-memory buffer
   - Auto-refresh every 5 seconds in frontend dashboard

10. **SessionAuthenticationFilter** (`config/SessionAuthenticationFilter.java`)
    - `WebFilter` registered in Spring Security filter chain (before authentication)
    - Gates API access behind a session cookie:
      - **Exempt paths** (no session required): `/api/v1/health`, `/actuator/**`, static assets
      - **API paths** (`/api/v1/**`): requires valid initialized session, returns 401 without
      - **Page visits** (all other paths): automatically creates and initializes session
    - Works with `SessionConfig` for cookie configuration

11. **SessionConfig** (`config/SessionConfig.java`)
    - Configures `CookieWebSessionIdResolver` bean
    - Cookie settings: name=`SESSION`, maxAge=24h, httpOnly=true, sameSite=Lax, path=/
    - Max age configurable via `app.session.max-age-seconds` (default: 86400)

12. **SponsorsController** (`controller/SponsorsController.java`)
   - REST API endpoints:
     - `GET /api/v1/sponsors` - main sponsors only (isMain=true)
   - Loads from sponsors.json at startup

13. **LlmController** (`controller/LlmController.java`)
   - Serves LLM-friendly Markdown (`text/markdown`) under `/llms`:
     - `GET /llms/spots.md` - all spots with live conditions and forecast summary
     - `GET /llms/spots/{id}.md` - single spot with full hourly forecast
     - `GET /llms/countries.md` - country index
     - `GET /llms/countries/{slug}.md` - spots in one country
   - Rendered from the AggregatorService caches, no session cookie required

14. **SeoController** (`controller/SeoController.java`)
   - Server-rendered entry points for crawlers and link previews:
     - `GET /spot/{id}` - single spot page with meta tags
     - `GET /country/{countryName}` - country page with meta tags
     - `GET /sitemap.xml` - sitemap covering all spots and countries

15. **McpToolService** (`service/mcp/McpToolService.java`, `config/McpConfig.java`)
   - Exposes the spot data as MCP tools: `list_spots`, `get_spot`, `find_spot_by_name`,
     `list_countries`, `get_spots_by_country`, `get_status`
   - Registered as a `ToolCallbackProvider` in `McpConfig`; see the MCP section of README.md

### Data Model

**Spot** (`model/spot/Spot.java`)
```java
{
  id: int,
  wgId: int,                    // Windguru ID
  name: String,
  country: String,
  windguruUrl: String,
  windguruFallbackUrl: String,  // optional fallback for forecasts
  forecast: List<Forecast>,
  forecastHourly: List<Forecast>,
  currentConditions: CurrentConditions,
  currentConditionsHistory: List<CurrentConditions>,
  aiAnalysisEn: String,
  aiAnalysisPl: String,
  spotPhotoUrl: String,
  coordinates: Coordinates,
  spotInfo: SpotInfo,
  spotInfoPL: SpotInfo,
  sponsors: List<Sponsor>,
  availableModels: List<AvailableModel>,  // dynamically discovered forecast models
  lastUpdated: String
}
```

**Forecast** (`model/forecast/Forecast.java`)
```java
{
  time: String,                 // hourly timestamp
  windSpeed: double,            // in knots
  gust: double,                 // in knots
  directionDeg: int,            // 0-360
  directionCardinal: String,    // N, NE, E, etc.
  tempC: double,
  precipMm: double
}
```

**CurrentConditions** (`model/live/CurrentConditions.java`)
```java
{
  windSpeed: double,
  gust: double,
  directionDeg: int,
  directionCardinal: String,
  tempC: double,
  updatedAt: String
}
```

## Data Sources

### spots.json
- Location: `src/main/resources/spots.json`
- Contains ~230 kite spots across 32 countries (Poland, Netherlands, France, Spain, Italy, Greece, Germany, Denmark, Brazil, Egypt, South Africa, etc.)
- Each spot includes: location, URLs (Windguru, Windfinder, ICM, webcam), spot info (water type, best wind, hazards, season)
- Loaded on startup by `JsonSpotsDataProvider`

### External APIs
- **micro.windguru.cz**: Text-based forecast exports (parsed with regex)
- **Weather stations** (14 integrations):
  - wiatrkadyny.pl (Poland - Kadyny, Puck, Mrzeżyno, etc.)
  - kiteriders.at and scpodo.at (Austria - Podersdorf)
  - Turawa station and Weather Underground PWS (Poland - Turawa)
  - Mietków station (Poland)
  - Svencele station (Lithuania)
  - Tarifa Arte Vida (Spain)
  - El Medano (Tenerife, Spain)
  - winds-up.com (France - Le Barcarès)
  - prasonisi.com (Greece - Rhodes)
  - kitesailing.ch (Switzerland - Silvaplana)

## Configuration

### application.yml
Key feature flags:
```yaml
app:
  feature:
    ai:
      forecast:
        analysis:
          enabled: false        # AI analysis disabled by default
    icm:
      vision:
        enabled: false          # ICM meteogram parsing disabled by default
  analytics:
    password: ${ANALYTICS_PASSWORD:}  # Optional password for /api/v1/metrics and /api/v1/logs
  session:
    max-age-seconds: 86400      # SESSION cookie max age (24 hours)
  wunderground:
    api-key: ${WUNDERGROUND_API_KEY:...}  # Weather Underground PWS (Turawa South)

spring:
  ai:
    openai:
      api-key: YOUR_API_KEY
      chat:
        options:
          model: gpt-4o-mini
    mcp:
      server:
        enabled: true           # MCP server (SSE at /mcp/sse, messages at /mcp/message)
        name: varun-surf
        type: ASYNC

management:
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus
  prometheus:
    metrics:
      export:
        enabled: true
```

## Build & Run

```bash
# Build
./gradlew build

# Run
./gradlew bootRun

# Test
./gradlew test

# E2E tests (headless)
./gradlew testE2e

# E2E tests (visible browser)
./gradlew testE2eNoHeadless

# Docker
docker build -t varun-surf .
docker run -p 8080:8080 varun-surf
```

## Development Practices

### Conventions
- Java 25 with preview features enabled
- Reactive programming with WebFlux (avoid blocking operations)
- In-memory caching (no database)
- Scheduled data fetching (every 3 hours for forecasts, every 1 minute for current conditions)
- Strategy pattern for extensible weather station providers
- Commit messages must NOT include "Co-Authored-By" or any AI attribution lines

### Testing
- Unit tests use JUnit 5 + Truth assertions
- MockWebServer for HTTP mocking
- E2E tests use Playwright with Chromium browser
- Test coverage for services, controllers, mappers, and strategies
- Test coverage should be at least 80%

### E2E Testing
- **Framework**: Playwright with Chromium browser
- **Location**: `src/e2e/java/com/github/pwittchen/varun/e2e/`
- **Base Class**: `BaseE2eTest` - starts Spring Boot app, manages Playwright lifecycle
- **Test Classes**:
  - `MainPageE2eTest` - main page functionality (spots grid, filters, modals, theme)
  - `SingleSpotE2eTest` - single spot view (forecast tabs, model dropdown, navigation)
  - `StatusPageE2eTest` - status page (system status, API endpoints, refresh)
- **Commands**:
  - `./gradlew testE2e` - run headless (CI mode)
  - `./gradlew testE2eNoHeadless` - run with visible browser (debugging)
- **Configuration**: Tests start embedded Spring Boot server on port 8080

### Code Organization
```
src/main/java/com/github/pwittchen/varun/
├── Application.java
├── config/                    # Configuration classes
│   ├── GsonConfig.java
│   ├── LLMConfig.java
│   ├── NettyConfig.java
│   ├── AsyncConfig.java
│   ├── MetricsConfig.java
│   ├── OkHttpClientConfig.java
│   ├── CorsConfig.java
│   ├── WebConfig.java
│   ├── SecurityConfig.java    # Spring Security (HTTP Basic Auth + session filter)
│   ├── SessionConfig.java     # SESSION cookie configuration
│   ├── SessionAuthenticationFilter.java # Session-based API access gating
│   ├── CacheControlFilter.java # Cache-Control headers (cache busting)
│   ├── LogAppenderConfig.java # In-memory log appender
│   ├── McpConfig.java         # MCP tool callback provider
│   └── LoggingFilter.java
├── controller/                # REST controllers
│   ├── SpotsController.java
│   ├── SponsorsController.java
│   ├── StatusController.java
│   ├── MetricsController.java
│   ├── LogsController.java
│   ├── LlmController.java     # /llms/*.md Markdown for LLMs
│   └── SeoController.java     # /spot/{id}, /country/{name}, /sitemap.xml
├── data/                      # Data providers
│   ├── spots/JsonSpotsDataProvider.java
│   └── sponsors/JsonSponsorsDataProvider.java
├── exception/                 # Custom exceptions
├── mapper/                    # Data transformation
│   ├── WeatherForecastMapper.java
│   └── HourlyForecastMapper.java # hourly forecasts onto one shared time grid
├── metrics/                   # Micrometer metrics
│   ├── AggregatorServiceMetrics.java
│   ├── SpotsControllerMetrics.java
│   └── HttpClientMetricsEventListener.java
├── model/                     # Domain models (records)
│   ├── forecast/              # Forecast, ForecastData, ForecastModel (40+ models), AvailableModel,
│   │                          # HourlyForecast, WindTimeline, ForecastWg, IcmGrid
│   ├── spot/                  # Spot, SpotInfo
│   ├── sponsor/               # Sponsor
│   ├── live/                  # CurrentConditions, filter/
│   ├── map/                   # Coordinates
│   └── status/                # Uptime, SourceHealthResult
└── service/                   # Business logic
    ├── AggregatorService.java
    ├── ai/                    # AiService, AiServiceEn, AiServicePl
    ├── forecast/              # ForecastService, IcmGridMapper, IcmForecastVisionService,
    │                          # ForecastAverageCalculator
    ├── live/                  # CurrentConditionsService, FetchCurrentConditionsStrategyBase
    │   └── strategy/          # 14 weather station strategies
    ├── map/                   # GoogleMapsService
    ├── mcp/                   # McpToolService (MCP tools over the spot data)
    ├── seo/                   # SeoService (server-rendered pages, sitemap)
    ├── sponsors/              # SponsorsService
    ├── metrics/               # MetricsHistoryService
    ├── logs/                  # LogsService, InMemoryLogAppender, LogEntry
    └── health/                # HealthHistoryService, HealthCheckResult
```

## Deployment

- **CI/CD**: GitHub Actions
  - `ci.yml`: builds the project and runs tests with Gradle
  - `cd.yml`: runs CI, pushes the Docker image to GHCR, deploys to production, creates a GitHub release
  - `deps.yml`: scheduled dependency updates
- **Registry**: ghcr.io/pwittchen/varun.surf
- **VPS**: Uses `deployment.sh` helper script

## Features

- [x] Single-page view of all kite spots with forecasts and live conditions
- [x] Live wind data refreshed every minute (requires page refresh)
- [x] Live conditions history (12h rolling window)
- [x] Forecasts updated every 3 hours
- [x] Spot details: description, links (Windguru, Windfinder, ICM), location, webcam
- [x] Country-based filtering
- [x] Search functionality
- [x] Favorites system
- [x] Custom spot ordering with drag-and-drop
- [x] Dark/light theme
- [x] 2-column / 3-column view toggle
- [x] Mobile-friendly UI
- [x] Kite and board size calculator
- [x] AI forecast analysis (optional, disabled by default, supports EN/PL)
- [x] Prometheus metrics export (/actuator/prometheus)
- [x] Custom metrics dashboard (/api/v1/metrics)
- [x] Custom logs dashboard (/api/v1/logs) with level filtering and search
- [x] Status page with uptime and stats
- [x] Sources page (/sources) with spots, forecast and live station data sources
- [x] MCP server page (/mcp) with endpoint, install command and JSON config
- [x] Health check history (90 data points, 1-minute intervals)
- [x] Session cookie authentication (API access gated behind SESSION cookie)
- [x] Hero section with random spot photo, name/location, and slogan (EN/PL)
- [x] Dynamic multi-model forecast support (40+ Windguru models)
- [x] Automatic language detection from browser settings
- [x] Stale live conditions indicators (yellow for outdated data)
- [x] Fallback weather station mechanism
- [x] Interactive wind map with marker clustering, wind arrows, a wind field
      overlay (heatmap + animated particles) and an hourly forecast timeline
      (the whole forecast run on a desktop, five days on a phone)
- [x] Sidebar navigation shared by every page, with a mobile drawer
- [x] Embeddable spot widget (/embed) with language selection
- [x] TV view (/tv) for a full-screen spot display
- [x] SEO pages rendered server-side (/spot/{id}, /country/{name}) and /sitemap.xml
- [x] MCP server exposing the spot data as tools (/mcp/sse)

## AI Analysis Feature (Experimental)

The AI forecast analysis is disabled by default because:
1. Limited value for this specific use case
2. Cost consideration: at ~900 tokens per prompt, one pass over ~230 spots is
   ~220k input tokens, so roughly $0.05 per language pass on gpt-4o-mini
3. Estimated monthly cost at the scheduled 8-hour interval in both languages:
   single-digit dollars per month (reasonable but not essential)

Note: the hourly block is ~48 daylight rows carrying every forecast variable,
which makes a prompt of roughly 950 tokens for a coastal spot and 850 for an
inland one (no wave columns). Lower `AiService.DETAILED_HOURS`, raise
`COARSE_STRIDE`, or narrow the `FIRST_DAY_HOUR`/`LAST_DAY_HOUR` window to trade
precision for tokens.

## Important Notes for AI Assistants

1. **Reactive Code**: This project uses Spring WebFlux. Avoid blocking operations. Use `Mono`, `Flux`, and reactive operators. Exception: `.block()` is allowed within Java 25 StructuredTaskScope contexts.

2. **Java 25 StructuredTaskScope**: This project uses preview features for structured concurrency:
   - Virtual threads via `Thread.ofVirtual().factory()`
   - Scoped concurrent execution with automatic cleanup
   - Subtasks tracked within scopes (ShutdownOnFailure or default)
   - Semaphore-based rate limiting to control concurrent API calls

3. **No Database**: All data is cached in-memory using ConcurrentHashMap. State is not persisted between restarts. This is intentional for simplicity and performance.

4. **Dynamic Multi-Model Forecast Support**:
   - GFS (default): Fetched every 3h for all spots
   - 40+ models supported (GFS, IFS, ICON, NAM, HRRR, AROME, etc.) - defined in `ForecastModel` enum
   - On-demand: When single spot is accessed, all Windguru models are discovered concurrently
   - `ForecastData` uses `Map<ForecastModel, List<Forecast>>` for per-model hourly data
   - `AvailableModel` record exposes discovered models to the frontend (key + displayName)
   - `Spot.availableModels` populated dynamically based on non-empty model data
   - Frontend model selector dropdown populated from `availableModels` field

5. **Caching Strategy**:
   - Forecasts: 3-hour refresh cycle (scheduled)
   - Current conditions: 1-minute refresh cycle (scheduled)
   - AI analysis: 8-hour refresh cycle (if enabled)
   - Embedded maps: Lazy-loaded once, cached forever
   - Forecast models: On-demand discovery when single spot accessed, 3-hour TTL per spot

6. **Immutable Data**: All models use Java records (immutable). To update, create new instances using `.withX()` methods or record constructors.

7. **External Dependencies**: Code relies on third-party APIs (Windguru, weather stations, Google Maps, LLMs). Network failures are expected and handled gracefully with timeouts, retries, and empty fallbacks.

8. **Scheduling**: Data fetching is automated via `@Scheduled` annotations with `@Async` execution. Multiple scheduled tasks run in parallel. Frontend shows cached data.

9. **Cardinal Direction Mapping**: `WeatherForecastMapper` converts degrees (0-360) to cardinal directions (N, NE, E, SE, S, SW, W, NW) with ±22.5° tolerance.

10. **AI Analysis**:
    - Disabled by default via feature flag
    - Language-specific services: `AiServiceEn` and `AiServicePl`
    - Streams content with Spring AI ChatClient
    - Supports optional per-spot context via `SpotInfo.llmComment`, taken from the
      `SpotInfo` matching the analysis language (`spotInfo` / `spotInfoPL`)
    - Professional kitesurfing analyst with kite size recommendations
    - 15s timeout, 3 retries, 1s delay between stream chunks

11. **Error Handling**: Uses `@Retryable` with exponential backoff, `@Recover` fallback methods, and reactive error operators (`onErrorResume`, `onErrorReturn`).

12. **Generated Frontend Assets**: Do not edit `.html`, `.css`, or `.js` files inside `src/main/resources/static`; they are minified outputs generated during the build process. Source files are in `src/frontend/`.

13. **Metrics & Monitoring**:
    - Prometheus metrics export at `/actuator/prometheus`
    - Custom metrics endpoint at `/api/v1/metrics` (password-protected via `ANALYTICS_PASSWORD`)
    - Logs endpoint at `/api/v1/logs` (password-protected via `ANALYTICS_PASSWORD`)
    - Metrics history with rolling window via `MetricsHistoryService`
    - Custom metrics classes: `AggregatorServiceMetrics`, `SpotsControllerMetrics`, `HttpClientMetricsEventListener`

14. **Logs System**:
    - In-memory log buffer via `LogsService` (last 1000 entries)
    - `InMemoryLogAppender` captures application logs
    - Level filtering: ERROR, WARN, INFO, DEBUG, TRACE
    - Logs are lost on application restart (intentional)

15. **Health History**:
    - `HealthHistoryService` tracks health check results
    - 90 data points (rolling window)
    - 1-minute check intervals
    - Provides uptime percentage and average latency

16. **Session Cookie Authentication**:
    - All `/api/v1/**` endpoints (except `/api/v1/health`) require a valid `SESSION` cookie
    - Visitors who load the frontend get a session cookie automatically (page visits initialize the session)
    - Requests without a valid session receive HTTP 401 with an empty body
    - Exempt paths: `/api/v1/health`, `/actuator/**`, static assets
    - Cookie config: httpOnly, sameSite=Lax, 24h maxAge (configurable via `app.session.max-age-seconds`)
    - Runs as a `WebFilter` before Spring Security authentication (metrics/logs still require HTTP Basic on top)

17. **Cache Busting** (updates visible without waiting for the Cloudflare cache):
    - CSS, JS and the logo are content-hashed at build time into `/assets/<name>.<hash>.<ext>` (`build.ts`)
    - Spot photos get a `?v=<content hash>` suffix (`AggregatorService.loadSpotPhotoPath`)
    - `CacheControlFilter` (`WebFilter`, highest precedence) sets headers on every response:
      - `/assets/**` and any `?v=`-versioned URL: `public, max-age=31536000, immutable`
      - unversioned images and root static files: `public, max-age=300, must-revalidate`
      - HTML: `no-cache, must-revalidate` (a fresh document is what makes new asset URLs reachable)
      - `/api/**`, `/actuator/**`, `/mcp/**`, `/llms/**`: `no-store`
      - a `Cache-Control` header already set by a handler is never overwritten
    - `deployment.sh` purges the Cloudflare cache after a successful deploy when
      `CLOUDFLARE_ZONE_ID` and `CLOUDFLARE_API_TOKEN` are set (skipped otherwise)

## Adding New Kite Spots

**Automated Method (Recommended)**:
Use the specialized `kite-spot-creator` agent available in `.claude/agents/kite-spot-creator.md`. This agent automates the entire process of researching, validating, and generating properly formatted spot entries with both English and Polish translations.

To trigger the agent, users can simply request:
- "Add [location name] as a new kite spot"
- "I want to add [spot name] to the spots list"

The agent handles all research, validation, and JSON generation automatically.

**Manual Method**:
When adding a new kite spot manually, follow this process:

1. Research the spot (Windguru URL, coordinates, conditions)
2. Generate valid JSON following the schema from existing spots in `src/main/resources/spots.json`
3. Validate all URLs are real and accessible
4. Include both English (`spotInfo`) and Polish (`spotInfoPL`) translations
5. Add the new spot to `src/main/resources/spots.json`
6. Test the application to ensure it loads correctly

**Important**:
- All fields must be filled (use "" for optional URLs if unavailable)
- Windguru URLs must be real (search windguru.cz for actual station IDs)
- Location URLs should point to the exact launch area
- Water temperature and best wind directions must be accurate
- Always provide Polish translations in `spotInfoPL`

## Related Documentation

For comprehensive project documentation, refer to these additional files:

- **README.md**: User-facing project description, feature list, build instructions, deployment guide, and CI/CD setup
- **docs/BACKEND.md**: Detailed backend architecture with ASCII diagrams, system flow visualization, data model specifications, caching strategy, concurrency patterns, and API endpoint reference
- **docs/FRONTEND.md**: Complete frontend architecture documentation including component structure, routing strategy, state management, styling patterns, i18n implementation, performance optimizations, and build process
- **AGENTS.md**: AI coding assistant context with detailed technical specifications, development guidelines, and implementation notes (alternative to this file for different AI tools)

**When to reference each document:**
- **README.md** - For project overview, getting started, building, running, and deploying
- **docs/BACKEND.md** - For understanding backend system architecture, data flow, service interactions, and concurrency model
- **docs/FRONTEND.md** - For frontend development, UI components, JavaScript architecture, styling, and client-side features
- **AGENTS.md** - For AI assistants needing structured context about the entire stack

## Contact & Contributing

This is a personal project by @pwittchen. For issues or feature requests, use GitHub Issues.
