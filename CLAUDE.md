# CLAUDE.md - AI Agent Context

## Project Overview

**varun.surf** is a weather forecast and real-time wind conditions dashboard designed specifically for kitesurfers. The application aggregates forecast data and live wind conditions for multiple kite spots worldwide, presenting them in a unified, easy-to-browse interface.

**Live URL**: https://varun.surf

## Tech Stack

- **Backend**: Spring Boot 3.5.9 (Reactive WebFlux)
- **Java**: Version 24 with preview features enabled
- **Build Tool**: Gradle
- **Frontend Build**: Bun (replaces npm for faster builds)
- **Dependencies**:
  - Spring WebFlux (reactive, non-blocking)
  - Spring Security (authentication for protected endpoints)
  - Spring AI 1.0.2 (OpenAI integration for forecast analysis)
  - Spring Actuator with Micrometer/Prometheus metrics
  - OkHttp 4.12.0 (HTTP client)
  - Gson 2.13.2 (JSON serialization)
  - JavaTuples 1.2
  - Guava 33.5.0-jre (EvictingQueue for metrics/logs history)
  - spring-dotenv 4.0.0 (environment variable loading)
- **Containerization**: Docker with GHCR deployment
- **Frontend**: Vanilla JavaScript (static/index.html)
- **Testing**: JUnit 5, Truth 1.4.5, MockWebServer, Playwright 1.49.0 (E2E), Jacoco 0.8.13 (coverage)

## Architecture Overview

### High-Level Flow

```
Browser Frontend (static/index.html)
    ↓ (HTTP REST)
Spring Boot Backend API (/api/v1/*)
    ├─→ /api/v1/spots (all spots with forecasts)
    ├─→ /api/v1/spots/{id} (single spot, triggers IFS fetch)
    ├─→ /api/v1/spots/{id}/{model} (single spot with GFS or IFS forecast)
    ├─→ /api/v1/session (session initialization, returns SESSION cookie)
    ├─→ /api/v1/sponsors (sponsors and main sponsors)
    ├─→ /api/v1/status (system status, uptime, counts)
    ├─→ /api/v1/metrics (application metrics, password-protected)
    ├─→ /api/v1/logs (application logs, password-protected)
    └─→ /api/v1/health (health check)
    ↓
AggregatorService (core orchestrator with Java 24 StructuredTaskScope)
    ├─→ ForecastService → Windguru micro API (GFS & IFS models)
    ├─→ CurrentConditionsService → Multiple station providers (9 strategies)
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
     - AI analysis: every 8 hours (if enabled)
   - Uses Java 24 StructuredTaskScope with virtual threads for concurrent execution
   - Semaphore-based rate limiting (32 forecasts, 32 conditions, 16 AI)
   - Maintains multiple in-memory caches (ConcurrentHashMap):
     - forecastCache: Map<Integer, ForecastData(daily, hourlyGfs, hourlyIfs)>
     - currentConditions: Map<Integer, CurrentConditions>
     - currentConditionsHistory: Map<Integer, EvictingQueue<CurrentConditions>> (12h history)
     - aiAnalysisEn/aiAnalysisPl: Map<Integer, String> (language-specific)
     - hourlyForecastCacheTimestamps: Map<Integer, Long> (3h TTL)
     - locationCoordinates: Map<Integer, Coordinates>
     - spotPhotos: Map<Integer, String>
     - spots: ConcurrentMap<Integer, Spot>
   - On-demand IFS model fetching when single spot is accessed

2. **ForecastService** (`service/forecast/ForecastService.java`)
   - Fetches weather forecasts from Windguru micro API
   - Supports multiple forecast models:
     - GFS (Global Forecast System - NOAA)
     - IFS (Integrated Forecast System - ECMWF)
   - Parses text-based exports using regex patterns
   - Returns ForecastData with daily and hourly forecasts
   - Data includes: wind speed/gust, direction (deg + cardinal), temperature, precipitation

3. **CurrentConditionsService** (`service/live/CurrentConditionsService.java`)
   - Uses strategy pattern for different weather station providers
   - 9 strategy implementations for weather stations:
     - WiatrKadynyStations (Poland - multiple locations)
     - Podersdorf (Austria - Neusiedler See)
     - Puck (Poland)
     - Turawa (Poland)
     - MB (Poland - Mrzeżyno)
     - TarifaArteVida (Spain - Tarifa)
     - Mietkow (Poland)
     - Svencele (Lithuania)
     - ElMedano (Spain - Tenerife)
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
   - Supports spot-specific LLM context via SpotInfo.llmComment

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
   - Returns reactive types: `Flux<Spot>` and `Mono<Spot>`
   - Enriches spots with cached forecasts, conditions, AI analysis
   - Uses SpotsControllerMetrics for request tracking

7. **StatusController** (`controller/StatusController.java`)
   - REST API endpoints:
     - `GET /api/v1/health` - simple health check
     - `GET /api/v1/status` - detailed status (version, uptime, spots/countries count)
   - Returns application status and statistics

8. **MetricsController** (`controller/MetricsController.java`)
   - REST API endpoints:
     - `GET /api/v1/metrics` - application metrics (password-protected)
     - `GET /api/v1/metrics/history` - metrics history over time
     - `POST /api/v1/metrics/auth` - metrics authentication
   - Exposes gauges, counters, timers, JVM metrics, HTTP client metrics

9. **LogsController** (`controller/LogsController.java`)
   - REST API endpoints:
     - `GET /api/v1/logs` - application logs (password-protected)
     - `GET /api/v1/logs?level={level}` - filter logs by level (ERROR, WARN, INFO, DEBUG, TRACE)
   - Returns last 1000 log entries from in-memory buffer
   - Auto-refresh every 5 seconds in frontend dashboard

10. **SessionController** (`controller/SessionController.java`)
    - REST API endpoint:
      - `GET /api/v1/session` - creates/initializes session, returns `{"status": "OK"}`
    - Sets `SESSION` cookie for programmatic session creation
    - Exempt from session authentication (accessible without existing session)

11. **SessionAuthenticationFilter** (`config/SessionAuthenticationFilter.java`)
    - `WebFilter` registered in Spring Security filter chain (before authentication)
    - Gates API access behind a session cookie:
      - **Exempt paths** (no session required): `/api/v1/health`, `/api/v1/session`, `/actuator/**`, static assets
      - **API paths** (`/api/v1/**`): requires valid initialized session, returns 401 without
      - **Page visits** (all other paths): automatically creates and initializes session
    - Works with `SessionConfig` for cookie configuration

12. **SessionConfig** (`config/SessionConfig.java`)
    - Configures `CookieWebSessionIdResolver` bean
    - Cookie settings: name=`SESSION`, maxAge=24h, httpOnly=true, sameSite=Lax, path=/
    - Max age configurable via `app.session.max-age-seconds` (default: 86400)

13. **SponsorsController** (`controller/SponsorsController.java`)
   - REST API endpoints:
     - `GET /api/v1/sponsors` - main sponsors only (isMain=true)
     - `GET /api/v1/sponsors/{id}` - single sponsor
   - Loads from sponsors.json at startup

### Data Model

**Spot** (`model/spot/Spot.java`)
```java
{
  id: int,
  wgId: int,                    // Windguru ID
  name: String,
  country: String,
  windguruUrl: String,
  forecast: List<Forecast>,
  currentConditions: CurrentConditions,
  aiAnalysis: String,
  spotInfo: SpotInfo
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
- Contains ~102 kite spots worldwide (Poland, Austria, Denmark, Spain, Portugal, Italy, Brazil, Lithuania, Germany, etc.)
- Each spot includes: location, URLs (Windguru, Windfinder, ICM, webcam), spot info (water type, best wind, hazards, season)
- Loaded on startup by `JsonSpotsDataProvider`

### External APIs
- **micro.windguru.cz**: Text-based forecast exports (parsed with regex)
- **Weather stations** (9 integrations):
  - wiatrkadyny.pl (Poland - Kadyny, Puck, Mrzeżyno, etc.)
  - kiteriders.at (Austria - Podersdorf)
  - Turawa station (Poland)
  - Mietków station (Poland)
  - Svencele station (Lithuania)
  - Tarifa Arte Vida (Spain)
  - El Medano (Tenerife, Spain)

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
  analytics:
    password: ${ANALYTICS_PASSWORD:}  # Optional password for /api/v1/metrics and /api/v1/logs
  session:
    max-age-seconds: 86400      # SESSION cookie max age (24 hours)

spring:
  ai:
    openai:
      api-key: YOUR_API_KEY
      chat:
        options:
          model: gpt-4o-mini

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
- Java 24 with preview features enabled
- Reactive programming with WebFlux (avoid blocking operations)
- In-memory caching (no database)
- Scheduled data fetching (every 3 hours for forecasts, every 1 minute for current conditions)
- Strategy pattern for extensible weather station providers

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
│   ├── LogAppenderConfig.java # In-memory log appender
│   └── LoggingFilter.java
├── controller/                # REST controllers
│   ├── SpotsController.java
│   ├── SponsorsController.java
│   ├── SessionController.java # /api/v1/session (session initialization)
│   ├── StatusController.java
│   ├── MetricsController.java
│   └── LogsController.java
├── data/provider/             # Data providers
│   ├── spots/JsonSpotsDataProvider.java
│   └── sponsors/JsonSponsorsDataProvider.java
├── exception/                 # Custom exceptions
├── mapper/                    # Data transformation
│   └── WeatherForecastMapper.java
├── metrics/                   # Micrometer metrics
│   ├── AggregatorServiceMetrics.java
│   ├── SpotsControllerMetrics.java
│   └── HttpClientMetricsEventListener.java
├── model/                     # Domain models (records)
│   ├── forecast/              # Forecast, ForecastData, ForecastModel, IcmGrid
│   ├── spot/                  # Spot, SpotInfo
│   ├── sponsor/               # Sponsor
│   ├── live/                  # CurrentConditions, filter/
│   ├── map/                   # Coordinates
│   └── status/                # Uptime
└── service/                   # Business logic
    ├── AggregatorService.java
    ├── ai/                    # AiService, AiServiceEn, AiServicePl
    ├── forecast/              # ForecastService, IcmGridMapper
    ├── live/                  # CurrentConditionsService
    │   └── strategy/          # 9 weather station strategies
    ├── map/                   # GoogleMapsService
    ├── sponsors/              # SponsorsService
    ├── metrics/               # MetricsHistoryService
    ├── logs/                  # LogsService, InMemoryLogAppender, LogEntry
    └── health/                # HealthHistoryService, HealthCheckResult
```

## Deployment

- **CI/CD**: GitHub Actions
  - `gradle.yml`: Java CI with Gradle
  - `docker.yml`: Docker image push to GHCR
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
- [x] Health check history (90 data points, 1-minute intervals)
- [x] Session cookie authentication (API access gated behind SESSION cookie)

## AI Analysis Feature (Experimental)

The AI forecast analysis is disabled by default because:
1. Limited value for this specific use case
2. Cost consideration: OpenAI gpt-4o-mini costs ~$0.01 per 102 spots (31k tokens)
3. Estimated monthly cost at 6-hour intervals: ~$1.20/month (reasonable but not essential)

## Important Notes for AI Assistants

1. **Reactive Code**: This project uses Spring WebFlux. Avoid blocking operations. Use `Mono`, `Flux`, and reactive operators. Exception: `.block()` is allowed within Java 24 StructuredTaskScope contexts.

2. **Java 24 StructuredTaskScope**: This project uses preview features for structured concurrency:
   - Virtual threads via `Thread.ofVirtual().factory()`
   - Scoped concurrent execution with automatic cleanup
   - Subtasks tracked within scopes (ShutdownOnFailure or default)
   - Semaphore-based rate limiting to control concurrent API calls

3. **No Database**: All data is cached in-memory using ConcurrentHashMap. State is not persisted between restarts. This is intentional for simplicity and performance.

4. **Multiple Forecast Models**:
   - GFS (default): Fetched every 3h for all spots
   - IFS: Lazy-loaded when single spot is accessed (cached for 3h)
   - ForecastData structure holds both models + daily forecasts

5. **Caching Strategy**:
   - Forecasts: 3-hour refresh cycle (scheduled)
   - Current conditions: 1-minute refresh cycle (scheduled)
   - AI analysis: 8-hour refresh cycle (if enabled)
   - Embedded maps: Lazy-loaded once, cached forever
   - IFS model: On-demand, 3-hour TTL per spot

6. **Immutable Data**: All models use Java records (immutable). To update, create new instances using `.withX()` methods or record constructors.

7. **External Dependencies**: Code relies on third-party APIs (Windguru, weather stations, Google Maps, LLMs). Network failures are expected and handled gracefully with timeouts, retries, and empty fallbacks.

8. **Scheduling**: Data fetching is automated via `@Scheduled` annotations with `@Async` execution. Multiple scheduled tasks run in parallel. Frontend shows cached data.

9. **Cardinal Direction Mapping**: `WeatherForecastMapper` converts degrees (0-360) to cardinal directions (N, NE, E, SE, S, SW, W, NW) with ±22.5° tolerance.

10. **AI Analysis**:
    - Disabled by default via feature flag
    - Language-specific services: `AiServiceEn` and `AiServicePl`
    - Streams content with Spring AI ChatClient
    - Supports spot-specific context via SpotInfo.llmComment
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
    - All `/api/v1/**` endpoints (except `/api/v1/health` and `/api/v1/session`) require a valid `SESSION` cookie
    - Visitors who load the frontend get a session cookie automatically (page visits initialize the session)
    - Programmatic access: call `GET /api/v1/session` to obtain a cookie, then include it in subsequent API calls
    - Requests without a valid session receive HTTP 401 with an empty body
    - Exempt paths: `/api/v1/health`, `/api/v1/session`, `/actuator/**`, static assets
    - Cookie config: httpOnly, sameSite=Lax, 24h maxAge (configurable via `app.session.max-age-seconds`)
    - Runs as a `WebFilter` before Spring Security authentication (metrics/logs still require HTTP Basic on top)

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
