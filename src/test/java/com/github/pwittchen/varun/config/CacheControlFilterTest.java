package com.github.pwittchen.varun.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static com.google.common.truth.Truth.assertThat;

class CacheControlFilterTest {

    private CacheControlFilter filter;

    @BeforeEach
    void setUp() {
        filter = new CacheControlFilter();
    }

    @Test
    void shouldCacheHashedAssetsForever() {
        assertThat(filter.cacheControlFor("/assets/styles.a1b2c3d4.css", false, null))
                .isEqualTo(CacheControlFilter.IMMUTABLE);
        assertThat(filter.cacheControlFor("/assets/index.a1b2c3d4.js", false, null))
                .isEqualTo(CacheControlFilter.IMMUTABLE);
        assertThat(filter.cacheControlFor("/assets/logo.a1b2c3d4.png", false, null))
                .isEqualTo(CacheControlFilter.IMMUTABLE);
    }

    @Test
    void shouldCacheVersionedImagesForever() {
        assertThat(filter.cacheControlFor("/images/spots/48009.jpg", true, null))
                .isEqualTo(CacheControlFilter.IMMUTABLE);
    }

    @Test
    void shouldCacheUnversionedImagesBriefly() {
        assertThat(filter.cacheControlFor("/images/spots/48009.jpg", false, null))
                .isEqualTo(CacheControlFilter.SHORT_LIVED);
        assertThat(filter.cacheControlFor("/logo.png", false, null))
                .isEqualTo(CacheControlFilter.SHORT_LIVED);
        assertThat(filter.cacheControlFor("/robots.txt", false, null))
                .isEqualTo(CacheControlFilter.SHORT_LIVED);
    }

    @Test
    void shouldRevalidateHtml() {
        assertThat(filter.cacheControlFor("/", false, MediaType.TEXT_HTML))
                .isEqualTo(CacheControlFilter.REVALIDATE);
        assertThat(filter.cacheControlFor("/spot/48009", false, MediaType.TEXT_HTML))
                .isEqualTo(CacheControlFilter.REVALIDATE);
        assertThat(filter.cacheControlFor("/index.html", false, MediaType.TEXT_HTML))
                .isEqualTo(CacheControlFilter.REVALIDATE);
    }

    @Test
    void shouldRevalidateHtmlWithCharset() {
        MediaType htmlWithCharset = MediaType.parseMediaType("text/html;charset=UTF-8");
        assertThat(filter.cacheControlFor("/mcp", false, htmlWithCharset))
                .isEqualTo(CacheControlFilter.REVALIDATE);
    }

    @Test
    void shouldNotStoreApiResponses() {
        assertThat(filter.cacheControlFor("/api/v1/spots", false, MediaType.APPLICATION_JSON))
                .isEqualTo(CacheControlFilter.NO_STORE);
        assertThat(filter.cacheControlFor("/actuator/health", false, MediaType.APPLICATION_JSON))
                .isEqualTo(CacheControlFilter.NO_STORE);
        assertThat(filter.cacheControlFor("/mcp/sse", false, MediaType.TEXT_EVENT_STREAM))
                .isEqualTo(CacheControlFilter.NO_STORE);
    }

    @Test
    void shouldNotSetCacheControlForUnknownContent() {
        assertThat(filter.cacheControlFor("/something", false, MediaType.TEXT_PLAIN)).isNull();
    }

    @Test
    void shouldSetHeaderOnResponse() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/assets/styles.a1b2c3d4.css"));

        StepVerifier
                .create(filter.filter(exchange, e -> e.getResponse().setComplete()))
                .verifyComplete();

        assertThat(exchange.getResponse().getHeaders().getCacheControl())
                .isEqualTo(CacheControlFilter.IMMUTABLE);
    }

    @Test
    void shouldSetHeaderBasedOnResponseContentType() {
        ServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/"));

        StepVerifier
                .create(filter.filter(exchange, e -> {
                    e.getResponse().getHeaders().setContentType(MediaType.TEXT_HTML);
                    return e.getResponse().setComplete();
                }))
                .verifyComplete();

        assertThat(exchange.getResponse().getHeaders().getCacheControl())
                .isEqualTo(CacheControlFilter.REVALIDATE);
    }

    @Test
    void shouldTreatQueryParameterAsVersion() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/images/spots/48009.jpg?v=a1b2c3d4"));

        StepVerifier
                .create(filter.filter(exchange, e -> e.getResponse().setComplete()))
                .verifyComplete();

        assertThat(exchange.getResponse().getHeaders().getCacheControl())
                .isEqualTo(CacheControlFilter.IMMUTABLE);
    }

    @Test
    void shouldKeepCacheControlSetByHandler() {
        ServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/"));

        StepVerifier
                .create(filter.filter(exchange, e -> {
                    e.getResponse().getHeaders().setContentType(MediaType.TEXT_HTML);
                    e.getResponse().getHeaders().setCacheControl("private, max-age=60");
                    return e.getResponse().setComplete();
                }))
                .verifyComplete();

        assertThat(exchange.getResponse().getHeaders().getCacheControl())
                .isEqualTo("private, max-age=60");
    }

    @Test
    void shouldNotBreakTheFilterChain() {
        ServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/"));

        StepVerifier
                .create(filter.filter(exchange, _ -> Mono.empty()))
                .verifyComplete();
    }
}
