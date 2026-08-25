package com.github.pwittchen.varun.config;

import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Gates {@code /api/v1/**} behind the SESSION cookie: a page visit gets one issued,
 * an API call without one is refused. The cookie is a stateless signed token
 * ({@link SessionTokenService}), so nothing is stored server-side and a restart or a
 * blue-green swap does not invalidate what visitors already hold.
 */
public class SessionAuthenticationFilter implements WebFilter {

    public static final String SESSION_COOKIE = "SESSION";

    private static final String API_PATH_PREFIX = "/api/v1/";
    private static final String FORWARDED_PROTO_HEADER = "X-Forwarded-Proto";
    private static final String HTTPS = "https";

    private static final List<String> EXEMPT_PATHS = List.of(
            "/api/v1/health",
            "/actuator",
            "/llms",
            "/mcp"
    );

    private static final List<String> STATIC_ASSET_EXTENSIONS = List.of(
            ".js", ".css", ".png", ".ico", ".svg", ".webp",
            ".woff2", ".txt", ".xml", ".webmanifest", ".html", ".json"
    );

    private static final List<String> STATIC_ASSET_PATHS = List.of(
            "/assets/", "/images/"
    );

    private final SessionTokenService tokens;

    public SessionAuthenticationFilter(SessionTokenService tokens) {
        this.tokens = tokens;
    }

    @NonNull
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, @NonNull WebFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        if (isExempt(path)) {
            return chain.filter(exchange);
        }

        String token = readToken(exchange);

        if (path.startsWith(API_PATH_PREFIX)) {
            if (tokens.isValid(token)) {
                return chain.filter(exchange);
            }
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        if (!tokens.isValid(token) || tokens.isDueForRenewal(token)) {
            exchange.getResponse().addCookie(issueCookie(exchange));
        }

        return chain.filter(exchange);
    }

    private String readToken(ServerWebExchange exchange) {
        HttpCookie cookie = exchange.getRequest().getCookies().getFirst(SESSION_COOKIE);
        return cookie == null ? null : cookie.getValue();
    }

    private ResponseCookie issueCookie(ServerWebExchange exchange) {
        return ResponseCookie
                .from(SESSION_COOKIE, tokens.issue())
                .httpOnly(true)
                .secure(isHttps(exchange))
                .sameSite("Lax")
                .path("/")
                .maxAge(tokens.maxAge())
                .build();
    }

    /**
     * TLS ends at the edge, so the scheme this application speaks is plain http even
     * in production and only the forwarded header knows what the visitor used. The
     * flag has to follow it rather than being pinned on: a cookie marked Secure over
     * http is dropped by the browser, which would break local runs and the e2e suite.
     */
    private boolean isHttps(ServerWebExchange exchange) {
        String forwardedProto = exchange.getRequest().getHeaders().getFirst(FORWARDED_PROTO_HEADER);
        if (forwardedProto != null && !forwardedProto.isBlank()) {
            return HTTPS.equalsIgnoreCase(forwardedProto.split(",")[0].trim());
        }
        return HTTPS.equalsIgnoreCase(exchange.getRequest().getURI().getScheme());
    }

    private boolean isExempt(String path) {
        for (String exempt : EXEMPT_PATHS) {
            if (path.equals(exempt) || path.startsWith(exempt + "/")) {
                return true;
            }
        }

        for (String assetPath : STATIC_ASSET_PATHS) {
            if (path.startsWith(assetPath)) {
                return true;
            }
        }

        for (String ext : STATIC_ASSET_EXTENSIONS) {
            if (path.endsWith(ext)) {
                return true;
            }
        }

        return false;
    }
}
