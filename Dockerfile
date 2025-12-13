# Build stage
FROM gradle:8.14.3-jdk24-alpine AS build
ARG VERSION=0.0.1-SNAPSHOT
WORKDIR /app
RUN apk add --no-cache nodejs npm
COPY build.gradle settings.gradle ./
COPY gradle ./gradle
RUN gradle dependencies --no-daemon || true
COPY src ./src
COPY package.json package-lock.json vite.config.js ./
RUN gradle clean bootJar -Pversion=${VERSION} --no-daemon

# Runtime stage
FROM eclipse-temurin:24-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "--enable-preview", "-jar", "app.jar"]