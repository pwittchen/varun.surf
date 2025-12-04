package com.github.pwittchen.varun.service.map;

import com.github.pwittchen.varun.component.http.HttpClientProxy;
import com.github.pwittchen.varun.model.map.Coordinates;
import com.github.pwittchen.varun.model.spot.Spot;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;

@Service
public class GoogleMapsService {
    private static final Logger log = LoggerFactory.getLogger(GoogleMapsService.class);
    private static final int MAX_REDIRECTS = 5;
    private final OkHttpClient httpClient;

    GoogleMapsService(HttpClientProxy httpClientProxy) {
        this.httpClient = httpClientProxy.getHttpClient();
    }

    public Mono<Coordinates> getCoordinates(Spot spot) {
        String locationUrl = spot.locationUrl();

        if (locationUrl == null || locationUrl.isEmpty()) {
            log.warn("Location URL is empty for spot {}", spot.name());
            return Mono.empty();
        }

        return extractCoordinatesFromUrl(locationUrl)
                .onErrorResume(error -> {
                    log.error("Error extracting coordinates for spot {}: {}", spot.name(), locationUrl, error);
                    return Mono.empty();
                });
    }

    private Mono<Coordinates> extractCoordinatesFromUrl(String locationUrl) {
        if (locationUrl.contains("maps.app.goo.gl") || locationUrl.contains("goo.gl")) {
            return unshortenUrl(locationUrl)
                    .flatMap(expandedUrl -> {
                        if (expandedUrl == null || expandedUrl.isEmpty()) {
                            log.warn("Failed to unshorten URL: {}", locationUrl);
                            return Mono.empty();
                        }
                        return parseCoordinatesFromExpandedUrl(expandedUrl);
                    });
        }

        return parseCoordinatesFromExpandedUrl(locationUrl);
    }

    private Mono<Coordinates> parseCoordinatesFromExpandedUrl(String expandedUrl) {
        if (expandedUrl.contains("@")) {
            String[] parts = expandedUrl.split("@");
            if (parts.length > 1) {
                String[] coordParts = parts[1].split(",");
                if (coordParts.length >= 2) {
                    try {
                        double lat = Double.parseDouble(coordParts[0].trim());
                        double lon = Double.parseDouble(coordParts[1].trim());
                        log.debug("Extracted coordinates from URL: lat={}, lon={}", lat, lon);
                        return Mono.just(new Coordinates(lat, lon));
                    } catch (NumberFormatException e) {
                        log.warn("Failed to parse coordinates from URL: {}", expandedUrl, e);
                    }
                }
            }
        }

        log.debug("No coordinates found in URL format @lat,lon: {}", expandedUrl);
        return Mono.empty();
    }

    private Mono<String> unshortenUrl(String shortenedUrl) {
        return Mono.fromCallable(() -> unshortenUrlBlocking(shortenedUrl))
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(error -> {
                    log.error("Failed to unshorten URL: {}", shortenedUrl, error);
                    return Mono.just(shortenedUrl);
                });
    }

    private String unshortenUrlBlocking(String url) throws IOException {
        String currentUrl = url;
        boolean redirected = false;

        for (int redirectCount = 0; redirectCount < MAX_REDIRECTS; redirectCount++) {
            Request request = new Request.Builder()
                    .url(currentUrl)
                    .head()
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!isRedirect(response.code())) {
                    if (redirected) {
                        log.debug("Final unshortened URL: {}", currentUrl);
                    }
                    return currentUrl;
                }

                String location = response.header("Location");
                if (location == null || location.isEmpty()) {
                    log.warn("Redirect without Location header for URL: {}", currentUrl);
                    if (redirected) {
                        log.debug("Final unshortened URL: {}", currentUrl);
                    }
                    return currentUrl;
                }

                HttpUrl resolved = response.request().url().resolve(location);
                String nextUrl = resolved != null ? resolved.toString() : location;
                log.debug("Redirect {} -> {}", currentUrl, nextUrl);
                currentUrl = nextUrl;
                redirected = true;
            }
        }

        if (redirected) {
            log.debug("Final unshortened URL: {}", currentUrl);
        }
        log.warn("Max redirects reached for URL: {}", currentUrl);
        return currentUrl;
    }

    private boolean isRedirect(int code) {
        return switch (code) {
            case 300, 301, 302, 303, 307, 308 -> true;
            default -> false;
        };
    }
}
