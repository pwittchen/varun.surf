package com.github.pwittchen.varun.config;

import com.github.pwittchen.varun.metrics.HttpClientMetricsEventListener;
import io.micrometer.core.instrument.MeterRegistry;
import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
public class OkHttpClientConfig {

    /**
     * OkHttp's dispatcher defaults are the real concurrency limit, not the semaphores in
     * AggregatorService: out of the box it runs 64 calls at once and only <b>5 per host</b>,
     * and everything else waits in an unbounded queue that the call timeout does not cover.
     * Nearly every forecast request goes to the one host (micro.windguru.cz), so a pass over
     * the whole spot list ran five calls wide however many permits the semaphore handed out -
     * around 1500 requests at a few seconds each, which is half an hour of fetching before a
     * single forecast reached the page.
     * <p>
     * Per-host is sized to the forecast semaphore (32 permits, two requests each - the wind
     * export and the wave export are fetched together), so the semaphore is what limits the
     * fetch again. The global cap leaves room for the current conditions sweep, which runs
     * every minute against a dozen other hosts and used to queue behind the forecasts.
     */
    private static final int MAX_REQUESTS = 192;
    private static final int MAX_REQUESTS_PER_HOST = 64;

    @Bean
    public HttpClientMetricsEventListener httpClientMetricsEventListener(MeterRegistry meterRegistry) {
        return new HttpClientMetricsEventListener(meterRegistry);
    }

    @Bean
    public OkHttpClient okHttpClient(HttpClientMetricsEventListener metricsEventListener) {
        final Dispatcher dispatcher = new Dispatcher();
        dispatcher.setMaxRequests(MAX_REQUESTS);
        dispatcher.setMaxRequestsPerHost(MAX_REQUESTS_PER_HOST);

        return new OkHttpClient
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
                .eventListenerFactory(_ -> metricsEventListener)
                .build();
    }
}
