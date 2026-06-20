package com.github.pwittchen.varun.service.map;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class GoogleMapsService {
    private static final Logger log = LoggerFactory.getLogger(GoogleMapsService.class);
    private static final int MAX_REDIRECTS = 5;

    // matches the "@lat,lng" form used in the map path, e.g. /maps/@54.82750,18.08778,14z
    private static final Pattern AT_COORDINATES = Pattern.compile(
            "@(-?\\d+(?:\\.\\d+)?),(-?\\d+(?:\\.\\d+)?)");
    // matches coordinates passed as a query parameter, e.g. ?q=-33.093,18.028 or ?query=37.083,-8.321
    // also handles URL-encoded comma (%2C)
    private static final Pattern QUERY_COORDINATES = Pattern.compile(
            "[?&](?:q|query|ll|sll|center|destination)=(-?\\d+(?:\\.\\d+)?)(?:,|%2C)(-?\\d+(?:\\.\\d+)?)",
            Pattern.CASE_INSENSITIVE);

    private final OkHttpClient httpClient;

    GoogleMapsService(OkHttpClient httpClient) {
        this.httpClient = httpClient;
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
        Coordinates coordinates = parseCoordinates(expandedUrl);
        if (coordinates != null) {
            log.debug("Extracted coordinates from URL: lat={}, lon={}", coordinates.lat(), coordinates.lon());
            return Mono.just(coordinates);
        }

        log.debug("No coordinates found in URL: {}", expandedUrl);
        return Mono.empty();
    }

    /**
     * Extracts coordinates from a resolved Google Maps URL, supporting both the
     * "@lat,lng" path form (e.g. /maps/@54.82,18.08,14z) and the query-parameter
     * forms (e.g. ?q=lat,lng, ?query=lat,lng, ?ll=lat,lng). Returns null when no
     * coordinates can be parsed.
     */
    static Coordinates parseCoordinates(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }

        Coordinates fromQuery = matchCoordinates(QUERY_COORDINATES, url);
        if (fromQuery != null) {
            return fromQuery;
        }

        return matchCoordinates(AT_COORDINATES, url);
    }

    private static Coordinates matchCoordinates(Pattern pattern, String url) {
        Matcher matcher = pattern.matcher(url);
        if (matcher.find()) {
            try {
                double lat = Double.parseDouble(matcher.group(1));
                double lon = Double.parseDouble(matcher.group(2));
                return new Coordinates(lat, lon);
            } catch (NumberFormatException e) {
                log.warn("Failed to parse coordinates from URL: {}", url, e);
            }
        }
        return null;
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

        for (int redirectCount = 0; redirectCount < MAX_REDIRECTS; redirectCount++) {
            Request request = new Request.Builder()
                    .url(currentUrl)
                    .head()
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!isRedirect(response.code())) {
                    return currentUrl;
                }

                String location = response.header("Location");
                if (location == null || location.isEmpty()) {
                    log.warn("Redirect without Location header for URL: {}", currentUrl);
                    return currentUrl;
                }

                HttpUrl resolved = response.request().url().resolve(location);
                String nextUrl = resolved != null ? resolved.toString() : location;
                log.debug("Redirect {} -> {}", currentUrl, nextUrl);
                currentUrl = nextUrl;
            }
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
