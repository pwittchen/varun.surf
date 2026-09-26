package com.github.pwittchen.varun.config;

import com.github.pwittchen.varun.metrics.HttpClientMetricsEventListener;
import io.micrometer.core.instrument.MeterRegistry;
import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
public class OkHttpClientConfig {

    public static final String WINDGURU_HTTP_CLIENT = "windguruHttpClient";
    public static final String LIVE_STATIONS_HTTP_CLIENT = "liveStationsHttpClient";

    /**
     * OkHttp's dispatcher defaults are the real concurrency limit, not the semaphores in
     * AggregatorService: out of the box it runs 64 calls at once and only <b>5 per host</b>,
     * and everything else waits in an unbounded queue that the call timeout does not cover.
     * Nearly every forecast request goes to the one host (micro.windguru.cz), so a pass over
     * the whole spot list ran five calls wide however many permits the semaphore handed out -
     * around 1500 requests at a few seconds each, which is half an hour of fetching before a
     * single forecast reached the page.
     * <p>
     * Per-host is sized to the forecast and discovery semaphores (4 permits each, two requests
     * per permit - the wind export and the wave export are fetched together), so the semaphores
     * are what limit the fetch. It was 64 for a while, and Windguru's firewall answered by
     * blocking the production IP for "unusual traffic" - this cap is a guard against the
     * semaphores being raised back without anyone noticing what that means for one host.
     * The global cap leaves room for the current conditions sweep, which runs every minute
     * against a dozen other hosts and used to queue behind the forecasts.
     */
    private static final int MAX_REQUESTS = 192;
    private static final int MAX_REQUESTS_PER_HOST = 16;

    @Bean
    public HttpClientMetricsEventListener httpClientMetricsEventListener(MeterRegistry meterRegistry) {
        return new HttpClientMetricsEventListener(meterRegistry);
    }

    /**
     * The client for everything that is neither Windguru nor a live station (Google Maps,
     * ICM, the source pings), and the one the other two are derived from. Derived clients
     * share its dispatcher and connection pool, so the per-host cap above still holds across
     * all of them, and a pooled connection is never handed to the wrong client: OkHttp keys
     * connections by address, and the proxy is part of the address.
     */
    @Bean
    @Primary
    public OkHttpClient okHttpClient(HttpClientMetricsEventListener metricsEventListener, OxylabsProxy proxy) {
        final Dispatcher dispatcher = new Dispatcher();
        dispatcher.setMaxRequests(MAX_REQUESTS);
        dispatcher.setMaxRequestsPerHost(MAX_REQUESTS_PER_HOST);

        final OkHttpClient.Builder builder = new OkHttpClient
                .Builder()
                .dispatcher(dispatcher)
                .connectTimeout(Duration.ofSeconds(10))
                .readTimeout(Duration.ofSeconds(30))
                .writeTimeout(Duration.ofSeconds(10))
                .callTimeout(Duration.ofSeconds(45))
                .connectionPool(new ConnectionPool(200, 5, TimeUnit.MINUTES))
                .followRedirects(false)
                .followSslRedirects(false)
                .retryOnConnectionFailure(true)
                .eventListenerFactory(_ -> metricsEventListener);

        return proxy.apply(builder, OxylabsProxy.Target.OTHER).build();
    }

    @Bean(WINDGURU_HTTP_CLIENT)
    public OkHttpClient windguruHttpClient(OkHttpClient okHttpClient, OxylabsProxy proxy) {
        return proxy.apply(okHttpClient.newBuilder(), OxylabsProxy.Target.WINDGURU).build();
    }

    @Bean(LIVE_STATIONS_HTTP_CLIENT)
    public OkHttpClient liveStationsHttpClient(OkHttpClient okHttpClient, OxylabsProxy proxy) {
        return proxy.apply(okHttpClient.newBuilder(), OxylabsProxy.Target.LIVE_STATIONS).build();
    }
}
