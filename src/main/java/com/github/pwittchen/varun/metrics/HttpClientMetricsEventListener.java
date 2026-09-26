package com.github.pwittchen.varun.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import okhttp3.Call;
import okhttp3.Connection;
import okhttp3.EventListener;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class HttpClientMetricsEventListener extends EventListener {

    private final MeterRegistry registry;
    private final AtomicInteger activeRequests = new AtomicInteger(0);
    private final ConcurrentHashMap<Call, Long> callStartTimes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Call, Long> connectStartTimes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Call, Long> dnsStartTimes = new ConcurrentHashMap<>();
    // Calls the server answered with a refusal. The call itself completes, but a 403 or 429 is a
    // request that got nothing (Windguru blocking the IP, a rate limit), so it counts as failed.
    private final ConcurrentHashMap<Call, Integer> refusedCalls = new ConcurrentHashMap<>();

    public HttpClientMetricsEventListener(MeterRegistry registry) {
        this.registry = registry;
        io.micrometer.core.instrument.Gauge
                .builder("varun.http.client.active_requests", activeRequests, AtomicInteger::get)
                .description("Number of active HTTP client requests")
                .register(registry);
    }

    @Override
    public void callStart(@NotNull Call call) {
        callStartTimes.put(call, System.nanoTime());
        activeRequests.incrementAndGet();
        registry.counter("varun.http.client.requests.total").increment();
    }

    @Override
    public void callEnd(@NotNull Call call) {
        Long startTime = callStartTimes.remove(call);
        Integer refusedStatus = refusedCalls.remove(call);
        if (startTime != null) {
            activeRequests.decrementAndGet();
            long duration = System.nanoTime() - startTime;
            Timer.builder("varun.http.client.request.duration")
                    .description("HTTP client request duration")
                    .tag("host", extractHost(call.request()))
                    .register(registry)
                    .record(Duration.ofNanos(duration));
            if (refusedStatus != null) {
                registry.counter("varun.http.client.requests.failed",
                        "exception", "HTTP " + refusedStatus).increment();
            } else {
                registry.counter("varun.http.client.requests.success").increment();
            }
        }
    }

    @Override
    public void callFailed(@NotNull Call call, @NotNull IOException e) {
        callStartTimes.remove(call);
        refusedCalls.remove(call);
        activeRequests.decrementAndGet();
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
        // Counted per connection rather than per request: a pooled connection carries many
        // requests, and through a residential proxy each one leaves from its own exit IP
        registry.counter("varun.http.client.connections.opened",
                "route", proxy.type() == Proxy.Type.DIRECT ? "direct" : "proxy").increment();
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
        if (isRefusal(response.code())) {
            refusedCalls.put(call, response.code());
        }
        registry.counter("varun.http.client.responses",
                "status", String.valueOf(response.code()),
                "host", extractHost(call.request())).increment();
    }

    private static boolean isRefusal(int statusCode) {
        return statusCode == 403 || statusCode == 429;
    }

    private String extractHost(Request request) {
        return request.url().host();
    }
}
