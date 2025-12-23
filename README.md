## varun.surf 🏄

[![CI](https://github.com/pwittchen/varun.surf/actions/workflows/ci.yml/badge.svg)](https://github.com/pwittchen/varun.surf/actions/workflows/ci.yml)
[![CD](https://github.com/pwittchen/varun.surf/actions/workflows/cd.yml/badge.svg)](https://github.com/pwittchen/varun.surf/actions/workflows/cd.yml)
[![DOCKER](https://github.com/pwittchen/varun.surf/actions/workflows/docker.yml/badge.svg)](https://github.com/pwittchen/varun.surf/actions/workflows/docker.yml)
[![RELEASE](https://github.com/pwittchen/varun.surf/actions/workflows/release.yml/badge.svg)](https://github.com/pwittchen/varun.surf/actions/workflows/release.yml)

kite spots database and weather forecast for kitesurfers on the web

see it online at: https://varun.surf

## building

```
./gradlew build
```

## running

```
./gradlew bootRun
```

## testing

unit testing:

```
./gradlew test
```

e2e testing:

```
./gradlew testE2e
```

e2e testing with visible browser:

```
./gradlew testE2eNoHeadless
```

## docker

```
docker build -t varun-surf .
docker run -p 8080:8080 varun-surf
```

## docker compose (local)

```
./deployment.sh dev
```

for prod setup, check [continuous delivery](#continuous-delivery) and [zero-downtime deployment](#zero-downtime-deployment) sections.

## docker container registry

docker image is automatically deployed to the registry at ghcr.io via `docker.yml` GitHub action from the `master` branch

- configure PAT (Personal Access Token) here: https://github.com/settings/tokens
- set permissions: `write:packages`, `read:packages`
- remember, you need to refresh the token in the future, once it will become outdated
- copy your access token to the clipboard

now, login into docker registry:

```
PAT=YOUR_ACCESS_TOKEN
echo $PAT | docker login ghcr.io -u pwittchen --password-stdin
```

pull image and run the container:

```
docker pull ghcr.io/pwittchen/varun.surf
docker run -p 8080:8080 ghcr.io/pwittchen/varun.surf:latest
```

## continuous integration

After each push to the master or PR, a new build is triggered with tests and test coverage report.
It's done automatically via GitHub actions `ci.yml` and `coverage.yml`

## continuous delivery

After each tag push with `v` prefix, `cd.yml` GitHub action is triggered,
and this action deploys the latest version of the app to the VPS.

## zero-downtime deployment

Deployment of the app is configured with the bash, docker, and docker compose scripts.
With these scripts, we can perform zero-downtime (blue/green) deployment with nginx server as a proxy.
To do that, follow the instructions below.

- Copy `deployment.sh`, `docker-compose.prod.yml`, `.env`, and `./nginx/nginx.conf` files to the single directory on the VPS.
- In the `deployment.sh` and `docker-compose.prod.yml` files adjust server paths if needed
- In the `.env` file, configure the environment variables basing on the `.env.example` file.
- Run `./deployment.sh prod` script to deploy the app with the nginx proxy.
- Run the same command again to perform the update with a zero-downtime and the latest docker image.
- If you want to test the deployment locally, run `./deployment.sh dev` script.
- To stop everything, run: `docker stop varun-app-blue-live varun-app-green-live varun-nginx`

## monitoring

We can view system status, by visiting [/status](https://varun.surf/status) page.

We can enable application and JVM metrics in the `application.yml` file and then use `/actuator/prometheus` endpoint to view metrics.

## ai forecast analysis

It's possible to enable AI/LLM in the app, so the forecast for each spot will get an AI-generated comment.
If you want to use AI in the app, configure ollama or openai in the `application.properties`.
In case of using ollama, start it as a separate service as follows:

```
ollama serve
```

An exemplary docker command to run the app with enabled AI analysis and OpenAI provider:

```
docker run -p 8080:8080 varun-surf \
    --app.feature.ai.forecast.analysis.enabled=true \
    --app.ai.provider=openai \
    --spring.ai.openai.api-key=your-api-key-here
```

> **NOTE:** I added this feature as an experiment, but it does not really add any big value to this particular project,
so I disabled it by default. Moreover, small local LLMs like smollm where returning strange, invalid outputs
and during local tests sometimes it got stuck, so not all the spots received analysis.
Another interesting thing is the fact that performing 74 calls to OpenAI with gpt-4o-mini model
used around 31k tokens and costs $0.01, so If I would like to trigger AI analysis
for my current configuration with this AI provider every six hours
(4 times in 24h = 120 times in 30 days = 8880 req. / month), I'd spent around \$1.2 (~4.35 PLN)
for monthly OpenAI usage, which is reasonable price because coffee in my local coffee shop costs more.
Nevertheless, more advanced analysis, more tokens or stronger model, should increase the price.

## architecture

- **Backend Architecture** → see [ARCH.md](ARCH.md) file
- **Frontend Architecture** → see [FRONTEND.md](FRONTEND.md) file

## ai coding agents configuration

- Claude → see: [CLAUDE.md](CLAUDE.md) file
- Codex → see: [AGENTS.md](AGENTS.md) file

### custom agent triggers

The project includes specialized Claude Code agents that can be triggered using shortcuts:

| Trigger | Agent | Purpose |
|---------|-------|---------|
| `@new-kite-spot [location]` | kite-spot-creator | Research and add a new kite spot to spots.json |
| `@new-weather-station [url]` | weather-station-strategy | Create a new weather station integration strategy |

**Examples:**

```
@new-kite-spot Tarifa, Spain
@new-weather-station https://holfuy.com/en/weather/1234
```

Agent definitions are located in `.claude/agents/`.

Remember that you can also trigger agents by natural language according to Claude Code guidelines.

## features

- showing all kite spots with forecasts and live conditions on the single page without switching between tabs or windows
- browsing forecasts for multiple kite spots
- browsing all kite spots on the map (Open Street Maps)
- watching live wind conditions in the selected spots
- refreshing live wind every one minute on the backend (requires page refresh on the frontend)
- refreshing forecasts every 3 hours in the backend (requires page refresh on the frontend)
- browsing details regarding different spots like description, windguru, windfinder and ICM forecast links, location and webcam
- filtering spots by country
- searching spots
- possibility to add spots to favorites
- organizing spots in the custom order with a drag and drop mechanism
- dark/light theme
- possibility to switch between a list view and a grid view
- mobile-friendly UI
- kite and board size calculator
- AI forecast analysis
- single spot view with hourly forecast (in horizontal and vertical view)
- additional TV-friendly view for the single spot
- map of the spot (provided by Open Street Maps and Windy)
- link to the navigation app (Google Maps)
- displaying a photo of the spot (if available)
- possibility to switch a weather forecast model for the spot (currently available models: GFS, IFS)
- embeddable HTML widget with current conditions and forecast for the spot
