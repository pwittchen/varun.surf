# CLAUDE.md - AI Agent Context

## Project Overview

**varun.surf** is a weather forecast and real-time wind conditions dashboard designed specifically for kitesurfers. The application aggregates forecast data and live wind conditions for multiple kite spots worldwide, presenting them in a unified, easy-to-browse interface.

**Live URL**: https://varun.surf

## Tech Stack

- **Backend**: Spring Boot 3.5.5 (Reactive WebFlux)
- **Java**: Version 24 with preview features enabled
- **Build Tool**: Gradle
- **Dependencies**:
  - Spring WebFlux (reactive, non-blocking)
  - Spring AI (OpenAI & Ollama integration for forecast analysis)
  - OkHttp 4.12.0 (HTTP client)
  - Gson (JSON serialization)
  - JavaTuples
- **Containerization**: Docker with GHCR deployment
- **Frontend**: Vanilla JavaScript (static/index.html)
- **Testing**: JUnit 5, Truth assertions, MockWebServer

## Architecture Overview

### High-Level Flow

```
Browser Frontend (static/index.html)
    ↓ (HTTP REST)
Spring Boot Backend API (/api/v1/*)
    ├─→ /api/v1/spots (all spots with forecasts)
    ├─→ /api/v1/spots/{id} (single spot, triggers IFS fetch)
    ├─→ /api/v1/spots/{id}/{model} (single spot with GFS or IFS forecast)
    └─→ /api/v1/sponsors (sponsors and main sponsors)
    ↓
AggregatorService (core orchestrator with Java 24 StructuredTaskScope)
    ├─→ ForecastService → Windguru micro API (GFS & IFS models)
    ├─→ CurrentConditionsService → Multiple station providers (strategy pattern)
    ├─→ GoogleMapsService → Google Maps (URL resolver, embeds)
    └─→ AiService → LLM (OpenAI/Ollama via Spring AI ChatClient)
```

### Key Components

1. **AggregatorService** (`service/AggregatorService.java`)
   - Central orchestrator with multiple scheduled tasks:
     - Forecasts: every 3 hours (GFS model, daily + hourly)
     - Current conditions: every 1 minute
     - AI analysis: every 8 hours (if enabled)
   - Uses Java 24 StructuredTaskScope with virtual threads for concurrent execution
   - Semaphore-based rate limiting (32 forecasts, 32 conditions, 16 AI)
   - Maintains 6 in-memory caches (ConcurrentHashMap):
     - forecastCache: Map<Integer, ForecastData(daily, hourlyGfs, hourlyIfs)>
     - currentConditions: Map<Integer, CurrentConditions>
     - aiAnalysis: Map<Integer, String>
     - embeddedMaps: Map<Integer, String> (lazy-loaded)
     - hourlyForecastCacheTimestamps: Map<Integer, Long> (3h TTL)
     - spots: AtomicReference<List<Spot>> (loaded at startup)
   - On-demand IFS model fetching when single spot is accessed

2. **ForecastService** (`service/ForecastService.java`)
   - Fetches weather forecasts from Windguru micro API
   - Supports multiple forecast models:
     - GFS (Global Forecast System - NOAA)
     - IFS (Integrated Forecast System - ECMWF)
   - Parses text-based exports using regex patterns
   - Returns ForecastData with daily and hourly forecasts
   - Data includes: wind speed/gust, direction (deg + cardinal), temperature, precipitation

3. **CurrentConditionsService** (`service/CurrentConditionsService.java`)
   - Uses strategy pattern for different weather station providers
   - Providers: WiatrKadyny (Poland), Kiteriders (Austria)
   - Scrapes/parses real-time wind data from station websites
   - Filters empty conditions (not cached)
   - Returns current wind speed, gusts, direction, temperature, timestamp

4. **AiService** (`service/AiService.java`)
   - Optional feature (disabled by default via feature flag)
   - Generates AI-powered forecast summaries using Spring AI ChatClient
   - Supports two providers: OpenAI (gpt-4o-mini) or Ollama (smollm2:135m)
   - Professional kitesurfing analyst prompt with kite size recommendations:
     - Below 8 kts: not rideable
     - 8-11 kts: foil only
     - 12-14 kts: large kite (12-17 m²)
     - 15-18 kts: medium kite (11-12 m²)
     - 19-25 kts: small kite (9-10 m²)
     - 28+ kts: very small kite (5-7 m²)
   - Streams responses with 15s timeout and 3 retries
   - Supports spot-specific LLM context via SpotInfo.llmComment

5. **GoogleMapsService** (`service/GoogleMapsService.java`)
   - Converts location URLs to embeddable Google Maps iframes
   - Unshortens goo.gl and maps.app.goo.gl URLs (max 5 redirects)
   - Extracts coordinates from @lat,lng format
   - Generates iframe HTML with satellite view (z=13&t=k)
   - Lazy-loaded on first spot access, then cached

6. **SpotsController** (`controller/SpotsController.java`)
   - REST API endpoints:
     - `GET /api/v1/spots` - all spots with cached data
     - `GET /api/v1/spots/{id}` - single spot (GFS, triggers async IFS fetch)
     - `GET /api/v1/spots/{id}/{model}` - single spot with model selection (gfs/ifs)
     - `GET /api/v1/health` - health check
   - Returns reactive types: `Flux<Spot>` and `Mono<Spot>`
   - Enriches spots with cached forecasts, conditions, AI analysis, maps

7. **SponsorsController** (`controller/SponsorsController.java`)
   - REST API endpoints:
     - `GET /api/v1/sponsors` - main sponsors only (isMain=true)
     - `GET /api/v1/sponsors/{id}` - single sponsor
   - Loads from sponsors.json at startup

### Data Model

**Spot** (`model/Spot.java`)
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

**Forecast** (`model/Forecast.java`)
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

**CurrentConditions** (`model/CurrentConditions.java`)
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
- Contains ~74 kite spots worldwide (Poland, Austria, Denmark, Spain, Portugal, Italy, Brazil, etc.)
- Each spot includes: location, URLs (Windguru, Windfinder, ICM, webcam), spot info (water type, best wind, hazards, season)
- Loaded on startup by `JsonSpotsDataProvider`

### External APIs
- **micro.windguru.cz**: Text-based forecast exports (parsed with regex)
- **wiatrkadyny.pl**: Polish wind stations (multiple locations)
- **kiteriders.at**: Austrian weather stations (Podersdorf)

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
  ai:
    provider: ollama           # or openai

spring:
  ai:
    openai:
      api-key: YOUR_API_KEY
      chat:
        options:
          model: gpt-4o-mini
    ollama:
      base-url: http://localhost:11434
      chat:
        options:
          model: smollm2:135m
```

## Build & Run

```bash
# Build
./gradlew build

# Run
./gradlew bootRun

# Test
./gradlew test

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
- Test coverage for services, controllers, mappers, and strategies
- Test coverage should be at least 80%

### Code Organization
```
src/main/java/com/github/pwittchen/varun/
├── Application.java
├── config/           # GsonConfig, LLMConfig, NettyConfig, LoggingFilter
├── controller/       # SpotsController
├── exception/        # Custom exceptions
├── mapper/           # WeatherForecastMapper (cardinal directions)
├── model/            # Spot, Forecast, CurrentConditions, SpotInfo
├── provider/         # JsonSpotsDataProvider
└── service/          # Core business logic
    ├── AggregatorService.java
    ├── ForecastService.java
    ├── CurrentConditionsService.java
    ├── AiService.java
    └── strategy/     # Current conditions fetching strategies
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
- [x] AI forecast analysis (optional, disabled by default)

## AI Analysis Feature (Experimental)

The AI forecast analysis is disabled by default because:
1. Limited value for this specific use case
2. Small local LLMs (like smollm) sometimes produce invalid outputs
3. Cost consideration: OpenAI gpt-4o-mini costs ~$0.01 per 74 spots (31k tokens)
4. Estimated monthly cost at 6-hour intervals: ~$1.20/month (reasonable but not essential)

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
    - Streams content with Spring AI ChatClient
    - Supports spot-specific context via SpotInfo.llmComment
    - Professional kitesurfing analyst with kite size recommendations
    - 15s timeout, 3 retries, 1s delay between stream chunks

11. **Error Handling**: Uses `@Retryable` with exponential backoff, `@Recover` fallback methods, and reactive error operators (`onErrorResume`, `onErrorReturn`).

12. **Generated Frontend Assets**: Do not edit `.html`, `.css`, or `.js` files inside `src/main/java/resources`; they are minified outputs generated during the build process.

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
- **ARCH.md**: Detailed backend architecture with ASCII diagrams, system flow visualization, data model specifications, caching strategy, concurrency patterns, and API endpoint reference
- **FRONTEND.md**: Complete frontend architecture documentation including component structure, routing strategy, state management, styling patterns, i18n implementation, performance optimizations, and build process
- **AGENTS.md**: AI coding assistant context with detailed technical specifications, development guidelines, and implementation notes (alternative to this file for different AI tools)

**When to reference each document:**
- **README.md** - For project overview, getting started, building, running, and deploying
- **ARCH.md** - For understanding backend system architecture, data flow, service interactions, and concurrency model
- **FRONTEND.md** - For frontend development, UI components, JavaScript architecture, styling, and client-side features
- **AGENTS.md** - For AI assistants needing structured context about the entire stack

## Contact & Contributing

This is a personal project by @pwittchen. For issues or feature requests, use GitHub Issues.
