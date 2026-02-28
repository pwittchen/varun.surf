package com.github.pwittchen.varun.config;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;

public class SessionAuthenticationFilter implements WebFilter {

    public static final String SESSION_INITIALIZED_ATTR = "session.initialized";

    private static final List<String> EXEMPT_PATHS = List.of(
            "/api/v1/health",
            "/api/v1/session",
            "/actuator"
    );

    private static final List<String> STATIC_ASSET_EXTENSIONS = List.of(
            ".js", ".css", ".png", ".ico", ".svg", ".webp",
            ".woff2", ".txt", ".xml", ".webmanifest", ".html", ".json"
    );

    private static final List<String> STATIC_ASSET_PATHS = List.of(
            "/assets/", "/images/"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        if (isExempt(path)) {
            return chain.filter(exchange);
        }

        if (path.startsWith("/api/v1/")) {
            return exchange.getSession().flatMap(session -> {
                Boolean initialized = session.getAttribute(SESSION_INITIALIZED_ATTR);
                if (Boolean.TRUE.equals(initialized)) {
                    return chain.filter(exchange);
                }
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            });
        }

        return exchange.getSession().flatMap(session -> {
            if (session.getAttribute(SESSION_INITIALIZED_ATTR) == null) {
                session.getAttributes().put(SESSION_INITIALIZED_ATTR, true);
            }
            return chain.filter(exchange);
        });
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
