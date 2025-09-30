## varun.surf

weather forecast and real-time wind conditions dashboard for kitesurfers

see it online at: https://varun.surf

## building

backend:

```
./gradlew clean bootJar
```

frontend:

minimize and copy `prototype/frontend/index.full.html` file to the spring boot static resources
(required only after editing `index.full.html` file)

```
cd prototype/frontend && ./build.sh && cd -
```

## running

```
java --enable-preview -jar build/libs/*.jar
```

## testing

```
./gradlew test
```
