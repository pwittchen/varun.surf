package com.github.pwittchen.varun.service.map;

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

    public GoogleMapsService() {
        this(new OkHttpClient.Builder()
                .followRedirects(false)
                .followSslRedirects(false)
                .build());
    }

    GoogleMapsService(OkHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public Mono<String> getEmbeddedMapCode(Spot spot) {
        String locationUrl = spot.locationUrl();

        if (locationUrl == null || locationUrl.isEmpty()) {
            log.warn("Location URL is empty for spot {}", spot.name());
            return Mono.just("");
        }

        return convertToEmbedUrl(locationUrl)
                .map(embedUrl -> String.format(
                        "<iframe src=\"%s\" width=\"600\" height=\"450\" style=\"border:0;\" allowfullscreen=\"\" loading=\"lazy\" referrerpolicy=\"no-referrer-when-downgrade\"></iframe>",
                        embedUrl
                ))
                .defaultIfEmpty("");
    }

    private Mono<String> convertToEmbedUrl(String locationUrl) {
        if (locationUrl.contains("maps.app.goo.gl") || locationUrl.contains("goo.gl")) {
            return unshortenUrl(locationUrl)
                    .flatMap(expandedUrl -> {
                        if (expandedUrl == null || expandedUrl.isEmpty()) {
                            log.warn("Failed to unshorten URL: {}", locationUrl);
                            return Mono.just("");
                        }
                        return Mono.just(processExpandedUrl(expandedUrl));
                    })
                    .onErrorResume(error -> {
                        log.error("Error unshortening URL: {}", locationUrl, error);
                        return Mono.just("");
                    });
        }

        return Mono.just(processExpandedUrl(locationUrl));
    }

    private String processExpandedUrl(String expandedUrl) {
        if (expandedUrl.contains("@")) {
            String[] parts = expandedUrl.split("@");
            if (parts.length > 1) {
                String[] coordParts = parts[1].split(",");
                if (coordParts.length >= 2) {
                    String coords = coordParts[0] + "," + coordParts[1];
                    return "https://maps.google.com/maps?q=" + coords + "&z=13&t=k&output=embed";
                }
            }
        }

        if (expandedUrl.contains("/place/")) {
            String[] parts = expandedUrl.split("/place/");
            if (parts.length > 1) {
                String placeInfo = parts[1].split("/@")[0].split("/")[0];
                return "https://maps.google.com/maps?q=" + placeInfo + "&output=embed";
            }
        }

        if (expandedUrl.contains("?q=")) {
            return expandedUrl.replace("?q=", "?output=embed&q=");
        }

        String separator = expandedUrl.contains("?") ? "&" : "?";
        return expandedUrl + separator + "output=embed";
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
