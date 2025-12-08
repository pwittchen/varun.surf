package com.github.pwittchen.varun.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import okhttp3.Call;
import okhttp3.Connection;
import okhttp3.ConnectionPool;
import okhttp3.EventListener;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
public class OkHttpClientConfig {

    @Bean
    public OkHttpClient okHttpClient(MeterRegistry meterRegistry) {
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
                .eventListenerFactory(_ -> new MetricsEventListener(meterRegistry))
                .build();
    }

    @Bean
    public AtomicInteger httpActiveRequests(MeterRegistry registry) {
        AtomicInteger activeRequests = new AtomicInteger(0);
        io.micrometer.core.instrument.Gauge
                .builder("varun.http.client.active_requests", activeRequests, AtomicInteger::get)
                .description("Number of active HTTP client requests")
                .register(registry);
        return activeRequests;
    }

    private static class MetricsEventListener extends EventListener {
        private final MeterRegistry registry;
        private final ConcurrentHashMap<Call, Long> callStartTimes = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<Call, Long> connectStartTimes = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<Call, Long> dnsStartTimes = new ConcurrentHashMap<>();

        MetricsEventListener(MeterRegistry registry) {
            this.registry = registry;
        }

        @Override
        public void callStart(@NotNull Call call) {
            callStartTimes.put(call, System.nanoTime());
            registry.counter("varun.http.client.requests.total").increment();
        }

        @Override
        public void callEnd(@NotNull Call call) {
            Long startTime = callStartTimes.remove(call);
            if (startTime != null) {
                long duration = System.nanoTime() - startTime;
                Timer.builder("varun.http.client.request.duration")
                        .description("HTTP client request duration")
                        .tag("host", extractHost(call.request()))
                        .register(registry)
                        .record(Duration.ofNanos(duration));
                registry.counter("varun.http.client.requests.success").increment();
            }
        }

        @Override
        public void callFailed(@NotNull Call call, @NotNull IOException e) {
            callStartTimes.remove(call);
            registry.counter("varun.http.client.requests.failed",
                    "exception", e.getClass().getSimpleName()).increment();
        }

        @Override
        public void dnsStart(@NotNull Call call, @NotNull String domainName) {
            dnsStartTimes.put(call, System.nanoTime());
        }

        @Override
        public void dnsEnd(@NotNull Call call, @NotNull String domainName, @NotNull List<InetAddress> inetAddressList) {
            Long startTime = dnsStartTimes.remove(call);
            if (startTime != null) {
                long duration = System.nanoTime() - startTime;
                Timer.builder("varun.http.client.dns.duration")
                        .description("DNS lookup duration")
                        .register(registry)
                        .record(Duration.ofNanos(duration));
            }
        }

        @Override
        public void connectStart(@NotNull Call call, @NotNull InetSocketAddress inetSocketAddress, @NotNull Proxy proxy) {
            connectStartTimes.put(call, System.nanoTime());
        }

        @Override
        public void connectEnd(@NotNull Call call, @NotNull InetSocketAddress inetSocketAddress, @NotNull Proxy proxy, @Nullable Protocol protocol) {
            Long startTime = connectStartTimes.remove(call);
            if (startTime != null) {
                long duration = System.nanoTime() - startTime;
                Timer.builder("varun.http.client.connect.duration")
                        .description("Connection establishment duration")
                        .register(registry)
                        .record(Duration.ofNanos(duration));
            }
        }

        @Override
        public void connectFailed(@NotNull Call call, @NotNull InetSocketAddress inetSocketAddress, @NotNull Proxy proxy, @Nullable Protocol protocol, @NotNull IOException e) {
            connectStartTimes.remove(call);
            registry.counter("varun.http.client.connect.failed",
                    "exception", e.getClass().getSimpleName()).increment();
        }

        @Override
        public void connectionAcquired(@NotNull Call call, @NotNull Connection connection) {
            registry.counter("varun.http.client.connections.acquired").increment();
        }

        @Override
        public void connectionReleased(@NotNull Call call, @NotNull Connection connection) {
            registry.counter("varun.http.client.connections.released").increment();
        }

        @Override
        public void responseHeadersEnd(@NotNull Call call, @NotNull Response response) {
            registry.counter("varun.http.client.responses",
                    "status", String.valueOf(response.code()),
                    "host", extractHost(call.request())).increment();
        }

        private String extractHost(Request request) {
            return request.url().host();
        }
    }
}
