## varun.surf 🏄

[![CI](https://github.com/pwittchen/varun.surf/actions/workflows/ci.yml/badge.svg)](https://github.com/pwittchen/varun.surf/actions/workflows/ci.yml)
[![CD](https://github.com/pwittchen/varun.surf/actions/workflows/cd.yml/badge.svg)](https://github.com/pwittchen/varun.surf/actions/workflows/cd.yml)
[![DOCKER](https://github.com/pwittchen/varun.surf/actions/workflows/docker.yml/badge.svg)](https://github.com/pwittchen/varun.surf/actions/workflows/docker.yml)
[![COVERAGE](https://github.com/pwittchen/varun.surf/actions/workflows/coverage.yml/badge.svg)](https://github.com/pwittchen/varun.surf/actions/workflows/coverage.yml)

weather forecast and real-time wind conditions dashboard for kitesurfers

see it online at: https://varun.surf

## building

```
./build.sh
```

## running

```
./build.sh --run
```

## testing

```
./gradlew test
```

## docker

```
docker build -t varun-surf .
docker run -p 8080:8080 varun-surf
```

## ghcr

docker image is automatically deployed to the registry at ghcr.io via `docker.yml` github action from the `master` branch

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

After each push to the master or PR, new build is triggered with tests and test coverage report.
It's done automatically via github actions `ci.yml` and `coverage.yml`

## continuous delivery

After each tag push with `v` prefix, `cd.yml` github action is triggered, 
and this action deploys the latest version of the app to the VPS.

## deployment

On the VPS, you can use `deployment.sh` script, which is deployment helper script.
Just copy it on the server, make it executable and set valid GitHub PAT (Personal Access Token) in the script.

To view all its functions just type:

```
./deployment.sh --help
```

## monitoring

Add `health.sh` script to the cron, so it will monitor the app and send an email if it's down or recovered.

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
Another interesting thing is the fact, that performing 74 calls to OpenAI with gpt-4o-mini model
used around 31k tokens and costs $0.01, so If I would like to trigger AI analysis
for my current configuration with this AI provider every six hours
(4 times in 24h = 120 times in 30 days = 8880 req. / month), I'd spent around \$1.2 (~4.35 PLN)
for monthly OpenAI usage, which is reasonable price because coffee in my local coffee shop costs more.
Nevertheless, more advanced analysis, more tokens or stronger model, should increase the price.

## architecture

see → [ARCH.md](ARCH.md) file

## ai coding agents configuration

- Claude → see: [CLAUDE.md](CLAUDE.md) file
- Codex → see: [AGENTS.md](AGENTS.md) file

## features

- showing all kite spots with forecasts and live conditions on the single page without switching between tabs or windows
- browsing forecasts for multiple kite spots
- watching live wind conditions in the selected spots
- refreshing live wind every one minute on the backend (requires page refresh on the frontend)
- refreshing forecasts every 3 hours in the backend (requires page refresh on the frontend)
- browsing details regarding different spots like description, windguru, windfinder and ICM forecast links, location and webcam
- filtering spots by country
- searching spots
- possibility to add spots to favorites
- organizing spots in the custom order with drag and drop mechanism
- dark/light theme
- possibility to switch between 2-columns view and 3-columns view
- mobile-friendly UI
- kite and board size calculator
- AI forecast analysis
