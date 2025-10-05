## varun.surf 🏄

[![Java CI with Gradle](https://github.com/pwittchen/varun.surf/actions/workflows/gradle.yml/badge.svg)](https://github.com/pwittchen/varun.surf/actions/workflows/gradle.yml)

weather forecast and real-time wind conditions dashboard for kitesurfers

see it online at: https://varun.surf

## building & running

```
./run.sh
```

## testing

```
./gradlew test
```

## AI

If you want to use AI in the app, configure ollama or openai in the `application.properties`.

In case of using ollama, start it as a separate service as follows:

```
ollama serve
```

> **NOTE:** I added this feature as an experiment, but it does not really add any big value to this particular project,
so I disabled it by default. Moreover, small local LLMs like smollm where returning strange, invalid outputs 
and during local tests sometimes it got stuck, so not all the spots received analysis.