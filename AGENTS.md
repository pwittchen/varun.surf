# AGENTS.md - AI Coding Assistant Context

## Project Overview

**varun.surf** is a weather forecast and real-time wind conditions dashboard designed specifically for kitesurfers. The application aggregates forecast data and live wind conditions for multiple kite spots worldwide, presenting them in a unified, easy-to-browse interface.

**Live URL**: https://varun.surf

**Project Type**: Spring Boot REST API with reactive programming, serving a single-page vanilla JavaScript frontend.

## Tech Stack

- **Backend Framework**: Spring Boot 3.5.9 (Reactive WebFlux)
- **Language**: Java 24 with preview features enabled
- **Build System**: Gradle
- **Frontend Build**: Bun (replaces npm for faster builds)
- **Key Dependencies**:
  - Spring WebFlux (reactive, non-blocking I/O)
  - Spring Security (HTTP Basic Auth for protected endpoints, session cookie for API access)
  - Spring AI 1.0.2 (OpenAI integration)
  - Spring Actuator with Micrometer/Prometheus metrics
  - OkHttp 4.12.0 (HTTP client library)
  - Gson 2.13.2 (JSON parsing and serialization)
  - JavaTuples 1.2 (tuple data structures)
  - Guava 33.5.0-jre (EvictingQueue for metrics/logs history)
  - spring-dotenv 4.0.0 (environment variable loading)
- **Containerization**: Docker, deployed to GitHub Container Registry (GHCR)
- **Frontend**: Vanilla JavaScript, HTML, CSS (no framework)
- **Testing Framework**: JUnit 5, Truth 1.4.5, MockWebServer, Playwright 1.49.0 (E2E), Jacoco 0.8.13 (coverage)

## System Architecture

### Request Flow

```
User Browser (static/index.html)
    ↓ HTTP GET
REST API Controllers (/api/v1/*)
    ├─→ /api/v1/spots (all spots with forecasts)
    ├─→ /api/v1/spots/{id} (single spot, triggers IFS fetch)
    ├─→ /api/v1/spots/{id}/{model} (GFS or IFS)
    ├─→ /api/v1/session (session initialization, returns SESSION cookie)
    ├─→ /api/v1/sponsors (sponsors list)
    ├─→ /api/v1/status (system status, uptime, counts)
    ├─→ /api/v1/metrics (application metrics, password-protected)
    ├─→ /api/v1/logs (application logs, password-protected)
    └─→ /api/v1/health (health check)
    ↓
AggregatorService (orchestrates with Java 24 StructuredTaskScope)
    ├─→ ForecastService ─→ Windguru API (GFS & IFS models)
    ├─→ CurrentConditionsService ─→ Weather Stations (9 strategies)
    ├─→ GoogleMapsService ─→ Google Maps (coordinates)
    ├─→ AiServiceEn/AiServicePl ─→ LLM Provider (OpenAI)
    ├─→ MetricsHistoryService ─→ Prometheus metrics with history
    ├─→ LogsService ─→ In-memory log buffer (last 1000 entries)
    └─→ HealthHistoryService ─→ Health check history (90 data points)
```

### Core Services

#### 1. AggregatorService (`service/AggregatorService.java`)
**Purpose**: Central data orchestration and caching layer using Java 24 structured concurrency.

**Scheduled Tasks** (run in parallel with `@Async`):
- **Forecasts**: Every 3 hours - GFS model, daily + hourly for all 102+ spots
- **Current Conditions**: Every 1 minute - real-time wind data
- **AI Analysis**: Every 8 hours - LLM-powered summaries (if feature enabled)

**Java 24 StructuredTaskScope**:
- Uses virtual threads via `Thread.ofVirtual().factory()`
- Scoped concurrent execution with automatic cleanup
- Subtasks tracked within scopes (ShutdownOnFailure or default)
- `.block()` is allowed within StructuredTaskScope contexts

**Semaphore-based Rate Limiting**:
- `forecastLimiter`: 32 concurrent Windguru API calls
- `currentConditionsLimiter`: 32 concurrent weather station calls
- `aiLimiter`: 16 concurrent LLM API calls
- Prevents overwhelming external APIs

**In-Memory Caches** (ConcurrentHashMap):
1. `forecastCache: Map<Integer, ForecastData>` - daily, hourlyGfs, hourlyIfs
2. `currentConditions: Map<Integer, CurrentConditions>` - latest wind data
3. `currentConditionsHistory: Map<Integer, EvictingQueue<CurrentConditions>>` - 12h history
4. `aiAnalysisEn: Map<Integer, String>` - AI summaries (English)
5. `aiAnalysisPl: Map<Integer, String>` - AI summaries (Polish)
6. `hourlyForecastCacheTimestamps: Map<Integer, Long>` - 3h TTL for IFS
7. `locationCoordinates: Map<Integer, Coordinates>` - parsed GPS coordinates
8. `spotPhotos: Map<Integer, String>` - spot photo URLs
9. `spots: ConcurrentMap<Integer, Spot>` - loaded at startup

**On-Demand IFS Fetching**:
- When `/api/v1/spots/{id}` is accessed, triggers async `fetchForecastsForAllModels()`
- Fetches both GFS and IFS hourly forecasts
- Caches for 3 hours to avoid redundant API calls
- Uses synchronized locks per spot to prevent duplicate fetches

#### 2. ForecastService (`service/forecast/ForecastService.java`)
**Purpose**: Fetch and parse weather forecast data from Windguru.

**Multiple Forecast Models**:
- **GFS** (Global Forecast System - NOAA) - default model
- **IFS** (Integrated Forecast System - ECMWF) - higher resolution

**Data Format**:
- Fetches text-based exports from Windguru micro API
- Parses using regex patterns (model-specific URLs)
- Returns `ForecastData(daily, hourlyGfs, hourlyIfs)`

**Data Included**:
- Wind speed/gust (knots)
- Direction (degrees + cardinal via WeatherForecastMapper)
- Temperature (Celsius)
- Precipitation (mm)
- Timestamps (hourly or daily)

#### 3. CurrentConditionsService (`service/live/CurrentConditionsService.java`)
**Purpose**: Fetch real-time wind conditions from weather stations.

**Strategy Pattern**:
- `FetchCurrentConditions` interface
- `FetchCurrentConditionsStrategyBase` abstract class
- 9 strategy implementations for weather stations:
  - `FetchCurrentConditionsStrategyWiatrKadynyStations` (Poland - Kadyny, Puck, Mrzeżyno)
  - `FetchCurrentConditionsStrategyPodersdorf` (Austria - Neusiedler See)
  - `FetchCurrentConditionsStrategyPuck` (Poland)
  - `FetchCurrentConditionsStrategyTurawa` (Poland)
  - `FetchCurrentConditionsStrategyMB` (Poland - Mrzeżyno)
  - `FetchCurrentConditionsStrategyTarifaArteVida` (Spain - Tarifa)
  - `FetchCurrentConditionsStrategyMietkow` (Poland)
  - `FetchCurrentConditionsStrategySvencele` (Lithuania)
  - `FetchCurrentConditionsStrategyElMedano` (Spain - Tenerife)

**Process**:
- Scrapes HTML from station websites
- Parses real-time wind data
- Filters empty conditions (not cached)
- Returns CurrentConditions with timestamp

**Data Included**:
- Wind speed/gust (knots)
- Direction (degrees + cardinal)
- Temperature (Celsius)
- Updated timestamp

#### 4. AiService (`service/ai/AiService.java`, `AiServiceEn.java`, `AiServicePl.java`)
**Purpose**: Generate AI-powered forecast summaries (optional, experimental feature).

**Language-Specific Implementations**:
- `AiServiceEn` - English language summaries
- `AiServicePl` - Polish language summaries
- Both extend abstract `AiService` base class

**Configuration**:
- Disabled by default: `app.feature.ai.forecast.analysis.enabled: false`
- Provider: OpenAI (gpt-4o-mini) - costs ~$0.01 per 102 spots

**Professional Prompt Engineering**:
- System role: Professional kitesurfing weather analyst
- Kite size recommendations:
  - Below 8 kts: not rideable
  - 8-11 kts: foil only
  - 12-14 kts: large kite (12-17 m²)
  - 15-18 kts: medium kite (11-12 m²)
  - 19-25 kts: small kite (9-10 m²)
  - 28+ kts: very small kite (5-7 m²)
- Custom context: `SpotInfo.llmComment` for spot-specific instructions
- Output: 2-3 sentence objective summary (no emojis)

**Streaming Implementation**:
```java
chatClient.prompt().user(prompt)
    .stream().content()
    .delayElements(Duration.ofSeconds(1))
    .timeout(Duration.ofSeconds(15))
    .retry(3)
    .collectList()
    .map(list -> String.join("", list))
```

#### 5. GoogleMapsService (`service/map/GoogleMapsService.java`)
**Purpose**: Convert location URLs to Coordinates objects.

**URL Processing**:
- Unshortens goo.gl and maps.app.goo.gl links (max 5 redirects)
- Extracts coordinates from @lat,lng format
- Parses /place/ locations
- Returns Coordinates record with latitude/longitude

**Features**:
- Lazy-loaded on first spot access
- Coordinates cached in `locationCoordinates` map
- 5-second timeout with reactive error handling

**OkHttp Client**:
- Custom client with redirect disabled (manual handling)
- HEAD requests for efficiency
- Follows 3xx redirects manually

#### 6. SpotsController (`controller/SpotsController.java`)
**Purpose**: REST API endpoints for kite spots.

**Endpoints**:
- `GET /api/v1/spots` - All spots with cached data (Flux<Spot>)
- `GET /api/v1/spots/{id}` - Single spot with GFS forecast (Mono<Spot>)
  - Triggers async fetchForecastsForAllModels() for IFS
- `GET /api/v1/spots/{id}/{model}` - Single spot with model selection
  - model: "gfs" or "ifs"
  - Triggers async IFS fetch if not cached

**Response Processing**:
- Enriches spots with cached forecasts, conditions, AI analysis
- Returns 404 if spot not found
- Uses `doOnSuccess()` for async operations
- Uses SpotsControllerMetrics for request tracking

#### 7. StatusController (`controller/StatusController.java`)
**Purpose**: REST API endpoints for system status.

**Endpoints**:
- `GET /api/v1/health` - Simple health check (returns `{"status": "UP"}`)
- `GET /api/v1/status` - Detailed status including:
  - Application version
  - Uptime (formatted and in seconds)
  - Spots count
  - Countries count
  - Live stations count

#### 8. MetricsController (`controller/MetricsController.java`)
**Purpose**: REST API endpoints for application metrics.

**Endpoints**:
- `GET /api/v1/metrics` - Full metrics (password-protected via `X-Metrics-Password` header)
- `GET /api/v1/metrics/history` - Metrics history over time
- `POST /api/v1/metrics/auth` - Authenticate with password

**Metrics Exposed**:
- Gauges: spots total, countries total, live stations, cache sizes
- Counters: forecasts/conditions/AI fetch counts (total/success/failure)
- Timers: fetch durations
- JVM metrics: heap, threads, GC, CPU
- HTTP client metrics: requests, connections, durations

#### 9. LogsController (`controller/LogsController.java`)
**Purpose**: REST API endpoints for application logs.

**Endpoints**:
- `GET /api/v1/logs` - All logs from in-memory buffer (password-protected)
- `GET /api/v1/logs?level={level}` - Filter logs by level (ERROR, WARN, INFO, DEBUG, TRACE)

**Features**:
- Returns last 1000 log entries (oldest evicted when buffer is full)
- Level filtering via query parameter
- Auto-refresh every 5 seconds in frontend dashboard
- Text search through log messages, logger names, and thread names

**Data Source**:
- `LogsService` maintains in-memory buffer
- `InMemoryLogAppender` captures application logs
- Logs are lost on application restart (intentional)

#### 10. SponsorsController (`controller/SponsorsController.java`)
**Purpose**: REST API endpoints for sponsors.

**Endpoints**:
- `GET /api/v1/sponsors` - Main sponsors (Flux<Sponsor>)
- `GET /api/v1/sponsors/{id}` - Single sponsor by ID (Mono<Sponsor>)
  - Filters by `isMain: true` flag

**Data Source**:
- Loaded from `sponsors.json` at startup
- Managed by SponsorsService
- JsonSponsorsDataProvider handles JSON parsing

### Data Models

#### Spot (`model/spot/Spot.java`)
Primary domain object representing a kite spot.
```java
public record Spot(
    int id,
    int wgId,                       // Windguru spot ID
    String name,
    String country,
    String windguruUrl,
    List<Forecast> forecast,
    CurrentConditions currentConditions,
    String aiAnalysis,
    SpotInfo spotInfo
) {}
```

#### Forecast (`model/forecast/Forecast.java`)
Hourly weather forecast data point.
```java
public record Forecast(
    String time,                    // ISO 8601 timestamp
    double windSpeed,               // in knots
    double gust,                    // in knots
    int directionDeg,               // 0-360 degrees
    String directionCardinal,       // N, NE, E, SE, S, SW, W, NW
    double tempC,                   // temperature in Celsius
    double precipMm                 // precipitation in millimeters
) {}
```

#### CurrentConditions (`model/live/CurrentConditions.java`)
Real-time weather station data.
```java
public record CurrentConditions(
    double windSpeed,               // in knots
    double gust,                    // in knots
    int directionDeg,               // 0-360 degrees
    String directionCardinal,       // N, NE, E, SE, S, SW, W, NW
    double tempC,                   // temperature in Celsius
    String updatedAt                // ISO 8601 timestamp
) {}
```

#### SpotInfo (`model/spot/SpotInfo.java`)
Static metadata about a kite spot.
```java
public record SpotInfo(
    String description,
    String waterType,               // sea, lake, lagoon, etc.
    String bestWind,                // preferred wind directions
    String hazards,                 // rocks, shallow water, etc.
    String season                   // best season for kiting
) {}
```

## Data Sources

### Static Data: spots.json
- **Location**: `src/main/resources/spots.json`
- **Size**: ~102 kite spots worldwide
- **Coverage**: Poland, Austria, Denmark, Spain, Portugal, Italy, Brazil, Germany, Netherlands, Lithuania, and more
- **Content**: Each spot contains:
  - Basic info (id, name, country, coordinates)
  - URLs (Windguru, Windfinder, ICM, webcam)
  - Spot info (water type, best wind directions, hazards, best season)
- **Loading**: Parsed on startup by `JsonSpotsDataProvider`

### External APIs
- **micro.windguru.cz**: Text-based weather forecast exports (parsed via regex)
- **Weather stations** (9 integrations):
  - wiatrkadyny.pl (Poland - Kadyny, Puck, Mrzeżyno, etc.)
  - kiteriders.at (Austria - Podersdorf, Neusiedler See)
  - Turawa station (Poland)
  - Mietków station (Poland)
  - Svencele station (Lithuania)
  - Tarifa Arte Vida (Spain)
  - El Medano (Tenerife, Spain)

## Configuration

### application.yml
Primary configuration file located at `src/main/resources/application.yml`.

Key configuration sections:
```yaml
server:
  port: 8080

app:
  feature:
    ai:
      forecast:
        analysis:
          enabled: false           # AI analysis feature flag (default: off)
  analytics:
    password: ${ANALYTICS_PASSWORD:}  # Optional password for /api/v1/metrics and /api/v1/logs
  session:
    max-age-seconds: 86400           # SESSION cookie max age (24 hours)

spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}  # Set via environment variable
      chat:
        options:
          model: gpt-4o-mini       # Model for OpenAI provider
          temperature: 0.7

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

### Environment Variables
- `OPENAI_API_KEY`: Required when AI analysis feature is enabled
- `ANALYTICS_PASSWORD`: Optional password for protected analytics endpoints (metrics and logs)

## Build & Run Commands

### Local Development
```bash
# Build only
./gradlew build

# Build and run locally
./gradlew bootRun

# Run unit tests
./gradlew test

# Run E2E tests (headless mode for CI)
./gradlew testE2e

# Run E2E tests (visible browser for debugging)
./gradlew testE2eNoHeadless

# Run with coverage
./gradlew test jacocoTestReport

# Clean build
./gradlew clean build
```

### Docker
```bash
# Build Docker image
docker build -t varun-surf .

# Run container
docker run -p 8080:8080 varun-surf

# Run with OpenAI key
docker run -p 8080:8080 -e OPENAI_API_KEY=your_key varun-surf
```

### Gradle Tasks
- `./gradlew bootRun` - Run Spring Boot application
- `./gradlew test` - Run unit tests
- `./gradlew testE2e` - Run E2E tests (headless)
- `./gradlew testE2eNoHeadless` - Run E2E tests (visible browser)
- `./gradlew build` - Build JAR
- `./gradlew clean` - Clean build artifacts
- `./gradlew jacocoTestReport` - Generate test coverage report

## Development Guidelines

### Code Style & Conventions
1. **Java Version**: Use Java 24 with preview features enabled
2. **Reactive Programming**: Use WebFlux reactive types (`Mono`, `Flux`) throughout
3. **Immutability**: Prefer records over classes for data models
4. **Null Safety**: Use `Optional` where appropriate, avoid null returns
5. **Error Handling**: Use reactive error operators (`onErrorResume`, `onErrorReturn`)
6. **Logging**: Use SLF4J with appropriate log levels
7. **Naming**: Follow Java conventions (camelCase, descriptive names)
8. **Generated Assets**: Do not edit `.html`, `.css`, or `.js` files inside `src/main/resources/static`; they are minified outputs generated during the build. Source files are in `src/frontend/`.

### Reactive Programming Rules
- **Never block**: Avoid blocking operations in reactive chains
- **No** `block()` or `blockFirst()` calls in production code
- **Use reactive HTTP clients**: OkHttp with reactive wrappers
- **Schedule work**: Use `Schedulers` for CPU-intensive or blocking operations
- **Compose reactively**: Chain operations with `map`, `flatMap`, `filter`, etc.

### Testing Standards
- **Framework**: JUnit 5 (Jupiter)
- **Assertions**: Google Truth library (`assertThat()`)
- **Mocking**: Mockito for service mocking
- **HTTP Mocking**: MockWebServer for external API calls
- **E2E Testing**: Playwright with Chromium browser
- **Coverage Target**: Minimum 80% code coverage
- **Test Structure**: Given-When-Then or Arrange-Act-Assert
- **Test Naming**: `methodName_scenario_expectedBehavior()`

### E2E Testing
- **Framework**: Playwright with Chromium browser
- **Location**: `src/e2e/java/com/github/pwittchen/varun/e2e/`
- **Base Class**: `BaseE2eTest` - starts embedded Spring Boot app, manages Playwright lifecycle
- **Test Classes**:
  - `MainPageE2eTest` - main page functionality (spots grid, search, country filter, view toggles, theme, modals)
  - `SingleSpotE2eTest` - single spot view (forecast tabs, model dropdown, chart view, navigation)
  - `StatusPageE2eTest` - status page (system status, API endpoints, service info, refresh)
- **Commands**:
  - `./gradlew testE2e` - run in headless mode (for CI)
  - `./gradlew testE2eNoHeadless` - run with visible browser (for debugging)
- **Configuration**:
  - Tests start embedded Spring Boot server on port 8080
  - Default timeout: 60s, navigation timeout: 90s
  - Viewport: 1920x1080

### Code Organization
```
src/main/java/com/github/pwittchen/varun/
├── Application.java                 # Spring Boot entry point
├── config/                          # Configuration classes
│   ├── GsonConfig.java             # Gson bean configuration
│   ├── LLMConfig.java              # Spring AI ChatClient configuration
│   ├── NettyConfig.java            # WebFlux Netty tuning
│   ├── AsyncConfig.java            # Async executor configuration
│   ├── MetricsConfig.java          # Micrometer metrics configuration
│   ├── OkHttpClientConfig.java     # OkHttp client beans
│   ├── CorsConfig.java             # CORS configuration
│   ├── WebConfig.java              # WebFlux configuration
│   ├── SecurityConfig.java         # Spring Security (HTTP Basic Auth + session filter)
│   ├── SessionConfig.java          # SESSION cookie configuration (24h, httpOnly, Lax)
│   ├── SessionAuthenticationFilter.java # Session-based API access gating (WebFilter)
│   ├── LogAppenderConfig.java      # In-memory log appender configuration
│   └── LoggingFilter.java          # Request/response logging
├── controller/                      # REST controllers
│   ├── SpotsController.java        # /api/v1/spots endpoints
│   ├── SponsorsController.java     # /api/v1/sponsors endpoints
│   ├── SessionController.java      # /api/v1/session (session initialization)
│   ├── StatusController.java       # /api/v1/status, /api/v1/health
│   ├── MetricsController.java      # /api/v1/metrics endpoints
│   └── LogsController.java         # /api/v1/logs endpoints
├── data/provider/                   # Data providers
│   ├── spots/JsonSpotsDataProvider.java
│   └── sponsors/JsonSponsorsDataProvider.java
├── exception/                       # Custom exceptions
│   ├── FetchingForecastException.java
│   ├── FetchingCurrentConditionsException.java
│   ├── FetchingAiForecastAnalysisException.java
│   └── FetchingForecastModelsException.java
├── mapper/                          # Data transformation
│   └── WeatherForecastMapper.java  # Degrees → cardinal directions
├── metrics/                         # Micrometer metrics
│   ├── AggregatorServiceMetrics.java
│   ├── SpotsControllerMetrics.java
│   └── HttpClientMetricsEventListener.java
├── model/                           # Domain models (records)
│   ├── forecast/                   # Forecast, ForecastData, ForecastModel, IcmGrid
│   ├── spot/                       # Spot, SpotInfo
│   ├── sponsor/                    # Sponsor
│   ├── live/                       # CurrentConditions, filter/
│   ├── map/                        # Coordinates
│   └── status/                     # Uptime
└── service/                         # Business logic
    ├── AggregatorService.java       # Main orchestrator
    ├── ai/                          # AI services
    │   ├── AiService.java          # Abstract base
    │   ├── AiServiceEn.java        # English summaries
    │   └── AiServicePl.java        # Polish summaries
    ├── forecast/                    # Forecast services
    │   ├── ForecastService.java
    │   └── IcmGridMapper.java
    ├── live/                        # Current conditions
    │   ├── CurrentConditionsService.java
    │   ├── FetchCurrentConditions.java
    │   ├── FetchCurrentConditionsStrategyBase.java
    │   └── strategy/               # 9 weather station strategies
    ├── map/                         # Map services
    │   └── GoogleMapsService.java
    ├── sponsors/                    # Sponsor services
    │   └── SponsorsService.java
    ├── metrics/                     # Metrics services
    │   └── MetricsHistoryService.java
    ├── logs/                        # Logs services
    │   ├── LogsService.java        # In-memory log buffer (1000 entries)
    │   ├── InMemoryLogAppender.java # Logback appender
    │   └── LogEntry.java           # Log entry record
    └── health/                      # Health services
        ├── HealthHistoryService.java # Health check history (90 points)
        └── HealthCheckResult.java   # Health check result record
```

## Deployment

### CI/CD Pipeline
- **Platform**: GitHub Actions
- **Workflows**:
  - `gradle.yml`: Java CI with Gradle (builds, tests, coverage)
  - `docker.yml`: Build and push Docker image to GHCR
- **Triggers**: Push to master, pull requests
- **Container Registry**: ghcr.io/pwittchen/varun.surf

### Production Deployment
- **Environment**: VPS (Virtual Private Server)
- **Deployment Script**: `deployment.sh` (helper for VPS deployment)
- **Process**: Pull Docker image from GHCR, restart container
- **Monitoring**: Application logs via Docker

## Feature List

Implemented features (complete):
- Single-page view of all kite spots with forecasts and live conditions
- Live wind data refreshed every minute (auto-refresh in frontend)
- Live conditions history (12h rolling window)
- Weather forecasts updated every 3 hours
- Detailed spot information (description, water type, hazards, best season)
- External links (Windguru, Windfinder, ICM model, webcam)
- Country-based filtering
- Search functionality (by spot name)
- Favorites system (localStorage-based)
- Custom spot ordering with drag-and-drop
- Dark/light theme toggle
- 2-column / 3-column view toggle
- Mobile-responsive design
- Kite size calculator (based on weight and wind speed)
- Board size calculator
- AI forecast analysis (experimental, disabled by default, supports EN/PL)
- Prometheus metrics export (/actuator/prometheus)
- Custom metrics dashboard (/api/v1/metrics)
- Custom logs dashboard (/api/v1/logs) with level filtering and text search
- Status page with uptime and stats
- Health check history (90 data points, 1-minute intervals)
- Session cookie authentication (API access gated behind SESSION cookie)

## AI Analysis Feature Details

**Status**: Experimental, disabled by default

**Language Support**:
- English summaries via `AiServiceEn`
- Polish summaries via `AiServicePl`

**Rationale for default-off**:
1. **Limited value**: Weather data is already clear and numeric
2. **Cost consideration**: OpenAI gpt-4o-mini costs ~$0.01 per 102 spots (31k tokens)
3. **Monthly cost estimate**: ~$1.20/month at 6-hour intervals (reasonable but optional)

**How to enable**:
```yaml
app:
  feature:
    ai:
      forecast:
        analysis:
          enabled: true
```

**Provider**: OpenAI (gpt-4o-mini) - production-ready, consistent output

## Critical Implementation Notes for AI Agents

### 1. Reactive Programming (Spring WebFlux)
This is a **fully reactive** application using Project Reactor.

**DO**:
- Use `Mono<T>` for single-value async operations
- Use `Flux<T>` for multi-value async streams
- Chain operations with `.map()`, `.flatMap()`, `.filter()`, etc.
- Use `.subscribeOn()` and `.publishOn()` to control threading
- Handle errors with `.onErrorResume()`, `.onErrorReturn()`, `.doOnError()`

**DON'T**:
- Never call `.block()` or `.blockFirst()` in service code (except for tests and Virtual Threads scopes)
- Avoid blocking I/O operations (use reactive HTTP clients)
- Don't mix blocking JDBC with reactive code
- Don't return `null` from reactive chains

**Example**:
```java
// GOOD
public Mono<Spot> getSpot(int id) {
    return spotRepository.findById(id)
        .flatMap(spot -> enrichWithForecast(spot))
        .onErrorResume(e -> Mono.empty());
}

// BAD
public Spot getSpot(int id) {
    return spotRepository.findById(id).block(); // NEVER DO THIS
}
```

### 2. In-Memory Caching
**No database** is used. All dynamic data is cached in memory.

**Implications**:
- Application state is lost on restart (this is acceptable)
- Caches are rebuilt on first request after startup
- No persistence layer for forecast or condition data
- `spots.json` is the only persistent data source

**Cache Strategy**:
- Forecasts: Refreshed every 3 hours (via `@Scheduled`)
- Current conditions: Refreshed every 1 minute (via `@Scheduled`)
- Current conditions history: 12h rolling window (via `EvictingQueue`)
- AI analysis: Language-specific caches (aiAnalysisEn, aiAnalysisPl)
- Location coordinates: Cached after first access
- Metrics history: Rolling window via `MetricsHistoryService`

### 3. Java 24 Preview Features
Preview features are **enabled** in this project.

**Compiler Configuration**:
```gradle
tasks.withType(JavaCompile) {
    options.compilerArgs += ["--enable-preview"]
}
```

**Usage**:
- Pattern matching for switch expressions
- Record patterns
- String templates (if available)

**Testing**:
```gradle
tasks.withType(Test) {
    jvmArgs += ["--enable-preview"]
}
```

### 4. External API Dependencies
This application relies on **third-party APIs** that may fail, change, or rate-limit.

**Handling Strategy**:
- Graceful degradation: Return empty data instead of failing
- Timeout configuration: All HTTP calls have timeouts
- Error logging: Log external API failures but don't crash
- Fallback data: Use cached data if fetch fails

**Example**:
```java
// Fetch with fallback to cache
public Mono<List<Forecast>> getForecast(int spotId) {
    return fetchFromWindguru(spotId)
        .timeout(Duration.ofSeconds(10))
        .onErrorResume(e -> {
            log.warn("Failed to fetch forecast, using cache", e);
            return Mono.justOrEmpty(forecastCache.get(spotId));
        });
}
```

### 5. Scheduled Tasks
Data fetching is **automated** via Spring's `@Scheduled` annotation.

**Schedules**:
- Forecasts: `@Scheduled(fixedRate = 10800000)` // 3 hours
- Current conditions: `@Scheduled(fixedRate = 60000)` // 1 minute
- AI analysis: Triggered after forecast fetch

**Important**:
- Frontend shows cached data (not real-time WebSocket)
- Users must refresh page to see new data
- Scheduled tasks run in background threads

### 6. Cardinal Direction Mapping
Wind directions are converted from degrees (0-360) to cardinal directions (N, NE, E, etc.).

**Mapping** (`WeatherForecastMapper.java`):
- 0° or 360° → N (North)
- 45° → NE (Northeast)
- 90° → E (East)
- 135° → SE (Southeast)
- 180° → S (South)
- 225° → SW (Southwest)
- 270° → W (West)
- 315° → NW (Northwest)

**Tolerance**: ±22.5° around each cardinal direction

### 7. Immutable Data Models
All models use Java **records** (immutable by default).

**Implications**:
- Cannot modify spot data after creation
- Must create new instances for updates
- Thread-safe by design
- No setters available

**Example**:
```java
// GOOD
Spot updatedSpot = new Spot(
    spot.id(),
    spot.wgId(),
    spot.name(),
    spot.country(),
    spot.windguruUrl(),
    newForecast,  // updated field
    spot.currentConditions(),
    spot.aiAnalysis(),
    spot.spotInfo()
);

// BAD
spot.setForecast(newForecast);  // Doesn't exist, won't compile
```

### 8. Testing Best Practices
When writing or modifying tests:

**Structure**:
```java
@Test
void shouldMeetExpectationWhenActionAndGivenScenario() {
    // Given (arrange)
    Spot spot = createTestSpot();

    // When (act)
    Mono<Spot> result = service.enrichSpot(spot);

    // Then (assert)
    StepVerifier.create(result)
        .assertNext(enriched -> {
            assertThat(enriched.forecast()).isNotEmpty();
            assertThat(enriched.currentConditions()).isNotNull();
        })
        .verifyComplete();
}
```

**Reactive Testing**:
Use `StepVerifier` from `reactor-test` for testing reactive types.

**HTTP Mocking**:
Use `MockWebServer` from OkHttp for mocking external API calls.

### 9. Metrics & Monitoring
The application includes comprehensive metrics collection:

**Prometheus Metrics**:
- Available at `/actuator/prometheus`
- Standard Spring Boot actuator metrics
- Custom application metrics

**Custom Metrics Classes**:
- `AggregatorServiceMetrics` - tracks forecast/conditions/AI fetch operations
- `SpotsControllerMetrics` - tracks API request counts
- `HttpClientMetricsEventListener` - tracks outgoing HTTP requests

**Metrics API**:
- `GET /api/v1/metrics` - full metrics snapshot (password-protected)
- `GET /api/v1/metrics/history` - historical metrics data
- `MetricsHistoryService` - maintains rolling window of metrics snapshots

**Logs API**:
- `GET /api/v1/logs` - application logs from in-memory buffer (password-protected)
- `GET /api/v1/logs?level={level}` - filter by log level
- `LogsService` - maintains last 1000 log entries
- `InMemoryLogAppender` - captures logs from Logback
- Logs are stored in memory only and lost on restart

**Health History**:
- `HealthHistoryService` - tracks health check results
- 90 data points (rolling window, 1-minute intervals)
- Provides uptime percentage and average latency statistics

**Password Protection**:
- Set `ANALYTICS_PASSWORD` environment variable
- Uses HTTP Basic Authentication (username: admin)
- Protects both `/api/v1/metrics/**` and `/api/v1/logs/**` endpoints

### 10. Session Cookie Authentication
All `/api/v1/**` endpoints (except health and session) require a valid `SESSION` cookie.

**How It Works**:
- `SessionAuthenticationFilter` (a `WebFilter`) runs before Spring Security authentication
- Page visits (non-API paths) automatically create and initialize a session → browser gets `SESSION` cookie
- API requests check for valid initialized session → 401 if missing
- `GET /api/v1/session` endpoint allows programmatic session creation

**Exempt Paths** (no session required):
- `/api/v1/health` - monitoring/uptime checks
- `/api/v1/session` - session creation endpoint
- `/actuator/**` - Prometheus scraping
- Static assets (`.js`, `.css`, `.png`, `.ico`, `.svg`, `.webp`, `.woff2`, `.txt`, `.xml`, `.webmanifest`, `.html`, `.json`, `/assets/`, `/images/`)

**Cookie Configuration** (`SessionConfig`):
- Name: `SESSION`
- Max age: 24 hours (configurable via `app.session.max-age-seconds`)
- httpOnly: true
- sameSite: Lax
- path: /

**Layered Security**:
- Session cookie is required first (enforced by `SessionAuthenticationFilter`)
- Metrics/logs endpoints additionally require HTTP Basic Auth (enforced by Spring Security)
- Both layers must pass for authenticated endpoints

## Adding New Kite Spots

**Automated Method (Recommended)**:
Use the specialized `kite-spot-creator` agent available in `.claude/agents/kite-spot-creator.md`. This agent automates the entire process of researching, validating, and generating properly formatted spot entries with translations.

To use the agent, simply ask Claude to add a new kite spot:
- "Add [location name] as a new kite spot"
- "I want to add [spot name] to the spots list"

The agent will handle research, URL validation, coordinate lookup, and JSON generation automatically.

**Manual Method**:
When adding a new kite spot manually, follow this workflow:

**Workflow**:
1. **Research the spot**:
   - Search windguru.cz for the actual station ID (e.g., windguru.cz/12345)
   - Find coordinates on Google Maps (exact launch area)
   - Research local conditions (wind directions, water type, hazards)
   - Look up typical water temperature range for the region
2. **Generate JSON**:
   - Follow the exact schema structure from existing spots in `src/main/resources/spots.json`
   - Fill all required fields (no empty strings except for optional URLs)
   - Include accurate `spotInfo` (English) and `spotInfoPL` (Polish translation)
3. **Validate**:
   - Ensure all URLs are real and accessible
   - Verify Windguru URL points to a real station
   - Check coordinates are correct
   - Validate JSON syntax
4. **Add to spots.json**:
   - Open `src/main/resources/spots.json`
   - Append the new spot to the JSON array
   - Ensure proper JSON formatting (commas, brackets)
5. **Test**:
   - Run `./gradlew bootRun`
   - Open http://localhost:8080
   - Verify the new spot appears with forecast data

**Critical Requirements**:
- **Real Windguru URLs**: Don't invent IDs - search windguru.cz to find actual stations
- **Accurate coordinates**: locationUrl must point to the launch area (not city center)
- **Complete translations**: Always include Polish translations in `spotInfoPL`
- **Valid JSON**: Test with `cat spots.json | jq .` before running
- **Realistic data**: Water temp, best wind directions, and hazards must be accurate

**JSON Schema Reference**:
```json
{
  "name": "Spot Name",
  "country": "Country Name",
  "windguruUrl": "https://www.windguru.cz/[ID]",
  "windfinderUrl": "https://www.windfinder.com/forecast/[spot-name]",
  "icmUrl": "",
  "webcamUrl": "",
  "locationUrl": "https://maps.app.goo.gl/[shortcode]",
  "spotInfo": {
    "type": "Lagoon/Beach/Bay/etc.",
    "bestWind": "N, NE, E, SE, S, SW, W, NW",
    "waterTemp": "XX-XX°C",
    "experience": "Beginner/Intermediate/Advanced",
    "launch": "Sandy Beach/etc.",
    "hazards": "Rocks/Currents/etc.",
    "season": "Month-Month",
    "description": "2-3 sentence description."
  },
  "spotInfoPL": {
    "type": "[Polish translation]",
    "bestWind": "[same directions]",
    "waterTemp": "[same range]",
    "experience": "[Polish translation]",
    "launch": "[Polish translation]",
    "hazards": "[Polish translation]",
    "season": "[Polish months]",
    "description": "[Polish translation]"
  }
}
```

**Common Translation Examples**:
- "Lagoon" → "Laguna"
- "Beginner" → "Początkujący"
- "Intermediate" → "Średniozaawansowany"
- "Advanced" → "Zaawansowany"
- "Sandy Beach" → "Plaża piaszczysta"
- "Rocky Shore" → "Skaliste wybrzeże"
- "May-September" → "Maj-Wrzesień"

## Related Documentation

For comprehensive project documentation, refer to these additional files:

- **README.md**: User-facing project description, feature list, build instructions, Docker commands, deployment guide, monitoring setup, and CI/CD workflows
- **docs/BACKEND.md**: Detailed backend architecture with ASCII diagrams, request/update flow, data model specifications, external integrations, multi-language support, caching strategy, concurrency patterns (Java 24 StructuredTaskScope), and complete API endpoint reference
- **docs/FRONTEND.md**: Complete frontend architecture documentation including tech stack, project structure, component architecture (spot cards, weather tables, modals, Windguru view), routing strategy, state management (localStorage/sessionStorage), styling patterns (CSS variables, grid/flexbox), i18n implementation, data flow diagrams, performance optimizations, error handling, browser compatibility, build process, and accessibility guidelines
- **CLAUDE.md**: AI assistant context specifically optimized for Claude Code, with project overview, key components, important implementation notes, and agent-specific guidelines (alternative format to this file)

**When to reference each document:**
- **README.md** - Start here for project overview, getting started, building, running, deploying, and feature list
- **docs/BACKEND.md** - For deep understanding of backend system architecture, data flow, service interactions, Java 24 concurrency model, caching strategy, and external API integrations
- **docs/FRONTEND.md** - For frontend development work including UI components, JavaScript architecture, styling, client-side routing, state management, and build process
- **CLAUDE.md** - For Claude AI assistant context (condensed version of this file)

**Recommended reading order for new AI agents:**
1. Start with **AGENTS.md** (this file) or **CLAUDE.md** for high-level context
2. Reference **docs/BACKEND.md** when working on backend services, data models, or API endpoints
3. Reference **docs/FRONTEND.md** when working on UI, JavaScript, CSS, or client-side features
4. Reference **README.md** for build commands, deployment, or user-facing features

## Project Maintenance

**Owner**: @pwittchen
**License**: Check repository for license information
**Issues**: Report bugs and feature requests via GitHub Issues
**Contributing**: This is a personal project; PRs welcome but not actively solicited

## Quick Reference

### Common Tasks

**Add a new weather station provider**:
1. Create new strategy class implementing `CurrentConditionsStrategy`
2. Add station URLs to `spots.json`
3. Register strategy in `CurrentConditionsService`
4. Add unit tests for new strategy

**Add a new kite spot**:
1. Follow the schema from existing spots in `src/main/resources/spots.json`
2. Research the spot details (see "Adding New Kite Spots" section)
3. Add generated JSON to `src/main/resources/spots.json`
4. Restart application and test

**Enable AI analysis**:
1. Set `app.feature.ai.forecast.analysis.enabled: true` in `application.yml`
2. Set `OPENAI_API_KEY` environment variable

**Access metrics and logs**:
1. Set `ANALYTICS_PASSWORD` environment variable (optional but recommended)
2. Call `GET /api/v1/metrics` or `GET /api/v1/logs` with Basic Auth (admin:password)
3. View Prometheus metrics at `/actuator/prometheus`
4. Check `/api/v1/metrics/history` for historical data
5. Filter logs by level: `GET /api/v1/logs?level=ERROR`

**Modify forecast parsing**:
1. Update regex patterns in `ForecastService.java`
2. Test against real Windguru export data
3. Add unit tests with sample data

**Change refresh intervals**:
1. Update `@Scheduled` annotations in `AggregatorService.java`
2. Values are in milliseconds (60000 = 1 minute)
3. Consider API rate limits

### Debugging Tips

**View logs**:
```bash
# Local
./gradlew bootRun

# Docker
docker logs <container_id>
```

**Test API endpoints** (session cookie required):
```bash
# Get session cookie first
curl -c cookies.txt http://localhost:8080/api/v1/session

# Get all spots (with session)
curl -b cookies.txt http://localhost:8080/api/v1/spots

# Get single spot (with session)
curl -b cookies.txt http://localhost:8080/api/v1/spots/1

# Without session → 401
curl http://localhost:8080/api/v1/spots

# Health check (no session needed)
curl http://localhost:8080/api/v1/health
```

**Check cache state**:
Add debug logging in `AggregatorService.java` to inspect cache contents.

**Test external API calls**:
Use unit tests with `MockWebServer` to simulate API responses.

**View metrics** (session cookie + Basic Auth required):
```bash
# Get session cookie first
curl -c cookies.txt http://localhost:8080/api/v1/session

# Application status (with session)
curl -b cookies.txt http://localhost:8080/api/v1/status

# Full metrics (session + password)
curl -b cookies.txt -u admin:yourpassword http://localhost:8080/api/v1/metrics

# Prometheus metrics (no session needed - actuator exempt)
curl http://localhost:8080/actuator/prometheus
```

**View logs** (session cookie + Basic Auth required):
```bash
# All logs (session + password)
curl -b cookies.txt -u admin:yourpassword http://localhost:8080/api/v1/logs

# Filter by level
curl -b cookies.txt -u admin:yourpassword "http://localhost:8080/api/v1/logs?level=ERROR"
```

---

**Note**: This documentation is optimized for AI coding assistants (GitHub Copilot, OpenAI Codex, Cursor, etc.). It provides structured context for code generation, refactoring, and debugging tasks.
