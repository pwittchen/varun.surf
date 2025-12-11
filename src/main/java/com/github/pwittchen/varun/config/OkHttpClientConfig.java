package com.github.pwittchen.varun.config;

import com.github.pwittchen.varun.metrics.HttpClientMetricsEventListener;
import io.micrometer.core.instrument.MeterRegistry;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
public class OkHttpClientConfig {

    @Bean
    public HttpClientMetricsEventListener httpClientMetricsEventListener(MeterRegistry meterRegistry) {
        return new HttpClientMetricsEventListener(meterRegistry);
    }

    @Bean
    public OkHttpClient okHttpClient(HttpClientMetricsEventListener metricsEventListener) {
        return new OkHttpClient
                .Builder()
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
