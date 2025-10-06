package com.github.pwittchen.varun.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoggingFilterTest {

    private final LoggingFilter loggingFilter = new LoggingFilter();

    @Test
    void shouldCreateWebFilter() {
        // when
        var filter = loggingFilter.indexAccessLogFilter();

        // then
        assertThat(filter).isNotNull();
    }

    @Test
    void shouldLogRequestAndContinueChain() {
        // given
        var filter = loggingFilter.indexAccessLogFilter();
        var exchange = createMockExchange("/api/spots", "GET", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0.0.0 Safari/537.36");
        var chain = mock(WebFilterChain.class);
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        // when
        var result = filter.filter(exchange, chain);

        // then
        StepVerifier.create(result)
                .verifyComplete();
        verify(chain).filter(exchange);
    }

    @Test
    void shouldHandleNullUserAgent() {
        // given
        var filter = loggingFilter.indexAccessLogFilter();
        var exchange = createMockExchange("/", "GET", null);
        var chain = mock(WebFilterChain.class);
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        // when
        var result = filter.filter(exchange, chain);

        // then
        StepVerifier.create(result)
                .verifyComplete();
        verify(chain).filter(exchange);
    }

    @Test
    void shouldHandleEmptyUserAgent() {
        // given
        var filter = loggingFilter.indexAccessLogFilter();
        var exchange = createMockExchange("/", "GET", "");
        var chain = mock(WebFilterChain.class);
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        // when
        var result = filter.filter(exchange, chain);

        // then
        StepVerifier.create(result)
                .verifyComplete();
        verify(chain).filter(exchange);
    }

    @Test
    void shouldHandleDifferentPaths() {
        // given
        var filter = loggingFilter.indexAccessLogFilter();
        var chain = mock(WebFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        // when/then
        String[] paths = {"/", "/api/spots", "/api/spots/123", "/health"};
        for (String path : paths) {
            var exchange = createMockExchange(path, "GET", "Chrome/120.0.0.0");
            StepVerifier.create(filter.filter(exchange, chain))
                    .verifyComplete();
        }
    }

    @Test
    void shouldHandleDifferentHttpMethods() {
        // given
        var filter = loggingFilter.indexAccessLogFilter();
        var chain = mock(WebFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        // when/then
        String[] methods = {"GET", "POST", "PUT", "DELETE", "PATCH"};
        for (String method : methods) {
            var exchange = createMockExchange("/api/test", method, "Chrome/120.0.0.0");
            StepVerifier.create(filter.filter(exchange, chain))
                    .verifyComplete();
        }
    }

    private ServerWebExchange createMockExchange(String path, String method, String userAgent) {
        var exchange = mock(ServerWebExchange.class);
        var request = mock(ServerHttpRequest.class);
        var headers = new HttpHeaders();

        if (userAgent != null) {
            headers.add("User-Agent", userAgent);
        }

        when(exchange.getRequest()).thenReturn(request);
        when(request.getURI()).thenReturn(URI.create(path));
        when(request.getMethod()).thenReturn(HttpMethod.valueOf(method));
        when(request.getHeaders()).thenReturn(headers);

        return exchange;
    }
}
