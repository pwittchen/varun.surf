# AGENTS.md - AI Coding Assistant Context

## Project Overview

**varun.surf** is a weather forecast and real-time wind conditions dashboard designed specifically for kitesurfers. The application aggregates forecast data and live wind conditions for multiple kite spots worldwide, presenting them in a unified, easy-to-browse interface.

**Live URL**: https://varun.surf

**Project Type**: Spring Boot REST API with reactive programming, serving a single-page vanilla JavaScript frontend.

## Tech Stack

- **Backend Framework**: Spring Boot 3.5.5 (Reactive WebFlux)
- **Language**: Java 24 with preview features enabled
- **Build System**: Gradle
- **Key Dependencies**:
  - Spring WebFlux (reactive, non-blocking I/O)
  - Spring AI (OpenAI & Ollama integration)
  - OkHttp 4.12.0 (HTTP client library)
  - Gson (JSON parsing and serialization)
  - JavaTuples (tuple data structures)
- **Containerization**: Docker, deployed to GitHub Container Registry (GHCR)
- **Frontend**: Vanilla JavaScript, HTML, CSS (no framework)
- **Testing Framework**: JUnit 5, Truth assertions library, MockWebServer

## System Architecture

### Request Flow

```
User Browser (static/index.html)
    ↓ HTTP GET
REST API Controller (/api/v1/spots)
    ↓
AggregatorService (orchestrates data fetching)
    ├─→ ForecastService ─→ Windguru API
    ├─→ CurrentConditionsService ─→ Weather Stations
    └─→ AiService ─→ LLM Provider (OpenAI/Ollama)
```

### Core Services

#### 1. AggregatorService (`service/AggregatorService.java`)
**Purpose**: Central data orchestration and caching layer.
- Scheduled to fetch forecast data every 3 hours
- Scheduled to fetch current conditions every 1 minute
- Maintains three in-memory caches:
  - Forecast cache (Map<Integer, List<Forecast>>)
  - Current conditions cache (Map<Integer, CurrentConditions>)
  - AI analysis cache (Map<Integer, String>)
- Coordinates parallel data fetching operations

#### 2. ForecastService (`service/ForecastService.java`)
**Purpose**: Fetch and parse weather forecast data.
- Fetches from Windguru micro API (text-based format)
- Uses regex patterns to extract weather data
- Returns structured hourly forecast data
- Data includes: wind speed/direction, temperature, precipitation, gusts

#### 3. CurrentConditionsService (`service/CurrentConditionsService.java`)
**Purpose**: Fetch real-time wind conditions.
- Implements strategy pattern for multiple weather station providers
- Current providers:
  - WiatrKadyny (Polish stations)
  - Kiteriders (Austrian stations)
- Scrapes and parses real-time data from station websites
- Returns current wind speed, gusts, direction, temperature

#### 4. AiService (`service/AiService.java`)
**Purpose**: Generate AI-powered forecast summaries (optional feature).
- Uses Spring AI ChatClient interface
- Supports two providers:
  - OpenAI (gpt-4o-mini model)
  - Ollama (local LLM, smollm2:135m model)
- Disabled by default (can be enabled via configuration)
- Generates natural language forecast analysis

#### 5. SpotsController (`controller/SpotsController.java`)
**Purpose**: REST API endpoints.
- `GET /api/v1/spots` - Returns all spots with enriched data
- `GET /api/v1/spots/{id}` - Returns single spot details
- Returns reactive types: `Flux<Spot>` and `Mono<Spot>`
- Handles CORS, error responses, logging

### Data Models

#### Spot (`model/Spot.java`)
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

#### Forecast (`model/Forecast.java`)
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

#### CurrentConditions (`model/CurrentConditions.java`)
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

#### SpotInfo (`model/SpotInfo.java`)
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
- **Size**: ~74 kite spots worldwide
- **Coverage**: Poland, Austria, Denmark, Spain, Portugal, Italy, Brazil, Germany, Netherlands, and more
- **Content**: Each spot contains:
  - Basic info (id, name, country, coordinates)
  - URLs (Windguru, Windfinder, ICM, webcam)
  - Spot info (water type, best wind directions, hazards, best season)
- **Loading**: Parsed on startup by `JsonSpotsDataProvider`

### External APIs
- **micro.windguru.cz**: Text-based weather forecast exports (parsed via regex)
- **wiatrkadyny.pl**: Polish weather station network (multiple locations)
- **kiteriders.at**: Austrian weather stations (Podersdorf, Neusiedler See)

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
  ai:
    provider: ollama              # AI provider: "openai" or "ollama"

spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}  # Set via environment variable
      chat:
        options:
          model: gpt-4o-mini       # Model for OpenAI provider
          temperature: 0.7
    ollama:
      base-url: http://localhost:11434
      chat:
        options:
          model: smollm2:135m      # Model for Ollama provider
```

### Environment Variables
- `OPENAI_API_KEY`: Required if using OpenAI provider
- `OLLAMA_BASE_URL`: Ollama server URL (default: http://localhost:11434)

## Build & Run Commands

### Local Development
```bash
# Build only
./build.sh

# Build and run locally
./build.sh --run

# Run tests
./gradlew test

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
- `./gradlew test` - Run tests
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
- **Coverage Target**: Minimum 80% code coverage
- **Test Structure**: Given-When-Then or Arrange-Act-Assert
- **Test Naming**: `methodName_scenario_expectedBehavior()`

### Code Organization
```
src/main/java/com/github/pwittchen/varun/
├── Application.java                 # Spring Boot entry point
├── config/                          # Configuration classes
│   ├── GsonConfig.java             # Gson bean configuration
│   ├── LLMConfig.java              # Spring AI ChatClient configuration
│   ├── NettyConfig.java            # WebFlux Netty tuning
│   └── LoggingFilter.java          # Request/response logging
├── controller/                      # REST controllers
│   └── SpotsController.java        # /api/v1/spots endpoints
├── exception/                       # Custom exceptions
│   ├── SpotNotFoundException.java
│   └── ExternalApiException.java
├── mapper/                          # Data transformation
│   └── WeatherForecastMapper.java  # Degrees → cardinal directions
├── model/                           # Domain models (records)
│   ├── Spot.java
│   ├── Forecast.java
│   ├── CurrentConditions.java
│   └── SpotInfo.java
├── provider/                        # Data providers
│   └── JsonSpotsDataProvider.java  # Loads spots.json
└── service/                         # Business logic
    ├── AggregatorService.java       # Main orchestrator
    ├── ForecastService.java         # Forecast fetching
    ├── CurrentConditionsService.java # Current conditions
    ├── AiService.java               # LLM integration
    └── strategy/                    # Strategy pattern implementations
        ├── CurrentConditionsStrategy.java        # Interface
        ├── WiatrKadynyStrategy.java             # Polish stations
        └── KiteridersPodersdorfStrategy.java    # Austrian stations
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
- AI forecast analysis (experimental, disabled by default)

## AI Analysis Feature Details

**Status**: Experimental, disabled by default

**Rationale for default-off**:
1. **Limited value**: Weather data is already clear and numeric
2. **Quality issues**: Small local LLMs (smollm) can produce inconsistent outputs
3. **Cost consideration**: OpenAI gpt-4o-mini costs ~$0.01 per 74 spots (31k tokens)
4. **Monthly cost estimate**: ~$1.20/month at 6-hour intervals (reasonable but optional)

**How to enable**:
```yaml
app:
  feature:
    ai:
      forecast:
        analysis:
          enabled: true
```

**Providers**:
- **OpenAI**: Production-ready, consistent output, costs money
- **Ollama**: Free, runs locally, may require fine-tuning for best results

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
- Never call `.block()` or `.blockFirst()` in service code
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
- AI analysis: Cached indefinitely (until next forecast refresh)

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
void testMethodName_givenScenario_shouldExpectation() {
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

## Related Documentation

- **README.md**: User-facing documentation, project description, build instructions
- **ARCH.md**: Detailed ASCII architecture diagrams, system flow visualization
- **CLAUDE.md**: Context documentation for Claude AI assistant

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
1. Edit `src/main/resources/spots.json`
2. Add spot object with required fields (id, wgId, name, country, urls, spotInfo)
3. Restart application

**Enable AI analysis**:
1. Set `app.feature.ai.forecast.analysis.enabled: true` in `application.yml`
2. Choose provider: `app.ai.provider: openai` or `ollama`
3. For OpenAI: Set `OPENAI_API_KEY` environment variable
4. For Ollama: Ensure Ollama server is running locally

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

**Test API endpoints**:
```bash
# Get all spots
curl http://localhost:8080/api/v1/spots

# Get single spot
curl http://localhost:8080/api/v1/spots/1
```

**Check cache state**:
Add debug logging in `AggregatorService.java` to inspect cache contents.

**Test external API calls**:
Use unit tests with `MockWebServer` to simulate API responses.

---

**Note**: This documentation is optimized for AI coding assistants (GitHub Copilot, OpenAI Codex, Cursor, etc.). It provides structured context for code generation, refactoring, and debugging tasks.