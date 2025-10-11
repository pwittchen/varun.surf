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
    ↓
AggregatorService (core orchestrator)
    ├─→ ForecastService → Windguru micro API
    ├─→ CurrentConditionsService → Multiple station providers
    └─→ AiService → LLM (OpenAI/Ollama)
```

### Key Components

1. **AggregatorService** (`service/AggregatorService.java`)
   - Central orchestrator scheduled to fetch data every 3 hours
   - Maintains in-memory caches for: forecasts, current conditions, AI analysis
   - Coordinates all data fetching operations

2. **ForecastService** (`service/ForecastService.java`)
   - Fetches hourly weather forecasts from Windguru micro API
   - Parses text-based exports using regex patterns
   - Returns structured forecast data (wind speed, direction, temperature, precipitation)

3. **CurrentConditionsService** (`service/CurrentConditionsService.java`)
   - Uses strategy pattern for different weather station providers
   - Providers include: WiatrKadyny, Kiteriders (Austria)
   - Scrapes/parses real-time wind data

4. **AiService** (`service/AiService.java`)
   - Optional feature (disabled by default)
   - Generates AI-powered forecast summaries using Spring AI ChatClient
   - Supports both OpenAI and Ollama providers

5. **SpotsController** (`controller/SpotsController.java`)
   - REST API endpoint: `GET /api/v1/spots`
   - Returns reactive `Flux<Spot>` with enriched data
   - Additional endpoint: `GET /api/v1/spots/{id}` for individual spot details

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
./build.sh

# Build and run
./build.sh --run

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

1. **Reactive Code**: This project uses Spring WebFlux. Avoid blocking operations. Use `Mono`, `Flux`, and reactive operators.
2. **No Database**: All data is cached in-memory. State is not persisted between restarts.
3. **Preview Features**: Java 24 preview features are enabled. Ensure compiler args include `--enable-preview`.
4. **Immutable Data**: Spot data from `spots.json` is read-only. Current conditions and forecasts are fetched and cached dynamically.
5. **External Dependencies**: Code relies on external APIs (Windguru, weather stations). Network failures are expected and handled gracefully.
6. **Scheduling**: Data fetching is automated via `@Scheduled` annotations. Frontend requires manual refresh to see updates.
7. **Cardinal Direction Mapping**: `WeatherForecastMapper` converts degrees to cardinal directions (N, NE, E, etc.).

## Related Documentation

- **README.md**: User-facing documentation, build instructions, deployment
- **ARCH.md**: Detailed ASCII architecture diagrams and system flow

## Contact & Contributing

This is a personal project by @pwittchen. For issues or feature requests, use GitHub Issues.