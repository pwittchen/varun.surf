package com.github.pwittchen.varun.config;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Emits Cache-Control headers so a deployment becomes visible immediately, instead of
 * waiting for the Cloudflare edge cache (or a browser cache) to expire on its own.
 * <p>
 * The strategy is the usual split between fingerprinted and non-fingerprinted URLs:
 * <ul>
 *   <li>{@code /assets/**} - build-time content-hashed CSS/JS/images, cached forever</li>
 *   <li>images and other root-level static files - cached forever when they carry a
 *       {@code ?v=<content hash>} version (see {@code AggregatorService#loadSpotPhotoPath}),
 *       otherwise only briefly, so replacing a file in place still propagates quickly</li>
 *   <li>HTML - always revalidated, so a page never keeps pointing at stale asset URLs</li>
 *   <li>API and actuator responses - never stored</li>
 * </ul>
 * Without a fresh HTML document the content hashes are useless, so HTML freshness is the
 * part that actually makes updates instant.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CacheControlFilter implements WebFilter {

    /** query parameter carrying the content hash of a versioned asset */
    public static final String VERSION_PARAM = "v";

    static final String IMMUTABLE = "public, max-age=31536000, immutable";
    static final String SHORT_LIVED = "public, max-age=300, must-revalidate";
    static final String REVALIDATE = "no-cache, must-revalidate";
    static final String NO_STORE = "no-store";

    private static final String HASHED_ASSETS_PATH = "/assets/";
    private static final String IMAGES_PATH = "/images/";

    private static final List<String> VERSIONABLE_EXTENSIONS = List.of(
            ".png", ".jpg", ".jpeg", ".webp", ".svg", ".ico",
            ".txt", ".xml", ".json", ".webmanifest", ".woff2"
    );

    private static final List<String> UNCACHEABLE_PATHS = List.of(
            "/api", "/actuator", "/mcp", "/llms"
    );

    @NonNull
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, @NonNull WebFilterChain chain) {
        exchange.getResponse().beforeCommit(() -> {
            applyCacheControl(exchange);
            return Mono.empty();
        });
        return chain.filter(exchange);
    }

    private void applyCacheControl(ServerWebExchange exchange) {
        var headers = exchange.getResponse().getHeaders();
        if (headers.getCacheControl() != null) {
            return;
        }

        String path = exchange.getRequest().getURI().getPath();
        boolean versioned = exchange.getRequest().getQueryParams().containsKey(VERSION_PARAM);
        String cacheControl = cacheControlFor(path, versioned, headers.getContentType());

        if (cacheControl != null) {
            headers.setCacheControl(cacheControl);
        }
    }

    @Nullable
    String cacheControlFor(String path, boolean versioned, @Nullable MediaType contentType) {
        if (path.startsWith(HASHED_ASSETS_PATH)) {
            return IMMUTABLE;
        }

        if (path.startsWith(IMAGES_PATH) || hasVersionableExtension(path)) {
            return versioned ? IMMUTABLE : SHORT_LIVED;
        }

        if (contentType != null && MediaType.TEXT_HTML.isCompatibleWith(contentType)) {
            return REVALIDATE;
        }

        if (isUncacheable(path)) {
            return NO_STORE;
        }

        return null;
    }

    private boolean hasVersionableExtension(String path) {
        for (String extension : VERSIONABLE_EXTENSIONS) {
            if (path.endsWith(extension)) {
                return true;
            }
        }
        return false;
    }

    private boolean isUncacheable(String path) {
        for (String uncacheable : UNCACHEABLE_PATHS) {
            if (path.equals(uncacheable) || path.startsWith(uncacheable + "/")) {
                return true;
            }
        }
        return false;
    }
}
