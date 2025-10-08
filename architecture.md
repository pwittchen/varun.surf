# Project Architecture

### High-level System Overview (ASCII)
```
+---------------------+            HTTP (REST)                +---------------------------+
|  Browser Frontend   |  <------------------------------>     |  Spring Boot Backend API  |
|  (static/index.html)|  GET /api/v1/spots, /health           |  /api/v1/* (JSON)         |
+----------+----------+                                       +-------------+-------------+
           |                                                                |
           |                                                                |
           |                                   +----------------------------v----------------------------+
           |                                   |             AggregatorService (core orchestrator)       |
           |                                   |  - schedules fetching (every 3h)                       |
           |                                   |  - caches spots, forecasts, current conditions, AI txt |
           |                                   +-----------+--------------------+------------------------+
           |                                               |                    |
           |                                               |                    |
           |                         +---------------------v----+     +---------v----------------+
           |                         |     ForecastService      |     |  CurrentConditionsService|
           |                         |  (Windguru micro API)    |     |  (station providers)     |
           |                         +-----------+--------------+     +---------------+----------+
           |                                     |                                    |
           |                                     |                                    |
           |                  +------------------v------------------+   +-------------v----------------------+
           |                  | External: micro.windguru.cz (HTTP) |   | External: WiatrKadyny, Kiteriders  |
           |                  +-------------------------------------+   +------------------------------------+
           |                                                                
           |                                   +----------------------------+
           |                                   |        AiService           |
           |                                   | (Spring AI ChatClient)     |
           |                                   +-------------+--------------+
           |                                                 |
           |                       +-------------------------v-----------------------+
           |                       |  LLM provider via Spring AI (configured via env)|
           |                       +-----------------------------------------------+
```

### Request/Update Flow
```
[Scheduler @ AggregatorService]
  every 3h -> fetchForecasts() ----------------------------------------------------.
                                                                                   |
[SpotsDataProvider] loads spots.json (on startup)                                  |
  -> AggregatorService.spots[]                                                     |
                                                                                   |
[AggregatorService]
  -> for each Spot.wgId -> ForecastService.getForecast(id) -> Windguru micro API ---'
  -> CurrentConditionsService via strategies -> station providers
  -> (optional) AiService.fetchAiAnalysis(spot) using Spring AI
  -> stores in in-memory maps: forecasts{}, currentConditions{}, aiAnalysis{}

[Client Browser]
  -> GET /api/v1/spots
  -> SpotsController returns Flux<Spot> (spots enriched with current data)
  -> Frontend renders tiles/cards
```

### Data Model (simplified)
```
Spot
├─ id (int) / wgId (int) / name / country / windguruUrl
├─ forecast : List<Forecast>
├─ currentConditions : CurrentConditions
└─ aiAnalysis : String (optional)

Forecast
├─ time (hourly)  ├─ windSpeed (kts)  ├─ gust (kts)
├─ directionDeg   ├─ directionCardinal (via WeatherForecastMapper)
├─ tempC          └─ precipMm

CurrentConditions
├─ windSpeed (kts) ├─ gust (kts)
├─ directionDeg    ├─ directionCardinal
└─ tempC / updatedAt
```

### External Integrations
```
- micro.windguru.cz (text export)  -> parsed with regex lines in ForecastService
- wiatrkadyny.pl (multiple stations) & kiteriders.at -> scraped/parsed in strategies
- Spring AI ChatClient -> LLM provider for short forecast summary
```

### Deployment/Build
```
Gradle (build.gradle) -> Spring Boot jar
Dockerfile -> containerized service
application.properties -> feature flags (e.g., app.feature.ai.forecast.analysis.enabled)
```

### Legend
- Rectangles: components in your codebase
- Rounded rectangles: external services
- Solid arrows: synchronous calls (HTTP or method)
- Dashed arrows: scheduled/background processes
