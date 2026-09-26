package com.github.pwittchen.varun.config;

import okhttp3.Authenticator;
import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Optional Oxylabs residential proxy for outgoing requests, switched on per target.
 * <p>
 * Windguru blocked the production IP for "unusual traffic", while the live stations kept
 * answering it, so each group of hosts has its own switch rather than one for everything:
 * a residential proxy is paid per gigabyte, and there is no reason to route what works
 * directly through it. OpenAI is not a target at all - Spring AI talks to it through its
 * own HTTP client, which never sees these settings.
 * <p>
 * A target asked to go through the proxy while the credentials are missing goes direct
 * and a warning is logged: failing the startup over a proxy would take the whole site down
 * to protect one data source.
 */
@Component
public class OxylabsProxy {

    private static final Logger log = LoggerFactory.getLogger(OxylabsProxy.class);

    public enum Target {
        /** micro.windguru.cz forecasts and model discovery. */
        WINDGURU("windguru"),
        /** The live weather station strategies. */
        LIVE_STATIONS("liveStations"),
        /** Everything else fetched over OkHttp: Google Maps, ICM meteo.pl, source pings. */
        OTHER("other");

        private final String key;

        Target(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }
    }

    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final String country;
    private final Map<Target, Boolean> enabled = new EnumMap<>(Target.class);

    public OxylabsProxy(
            @Value("${app.proxy.oxylabs.host:pr.oxylabs.io}") String host,
            @Value("${app.proxy.oxylabs.port:7777}") int port,
            @Value("${app.proxy.oxylabs.username:}") String username,
            @Value("${app.proxy.oxylabs.password:}") String password,
            @Value("${app.proxy.oxylabs.country:}") String country,
            @Value("${app.proxy.windguru.enabled:false}") boolean windguruEnabled,
            @Value("${app.proxy.live-stations.enabled:false}") boolean liveStationsEnabled,
            @Value("${app.proxy.other.enabled:false}") boolean otherEnabled
    ) {
        this.host = host == null ? "" : host.trim();
        this.port = port;
        this.username = username == null ? "" : username.trim();
        this.password = password == null ? "" : password;
        this.country = country == null ? "" : country.trim();
        enabled.put(Target.WINDGURU, windguruEnabled);
        enabled.put(Target.LIVE_STATIONS, liveStationsEnabled);
        enabled.put(Target.OTHER, otherEnabled);

        for (Target target : Target.values()) {
            if (isEnabled(target) && !isConfigured()) {
                log.warn("Oxylabs proxy is enabled for {} but app.proxy.oxylabs host, username or password "
                        + "is missing (OXYLABS_USERNAME / OXYLABS_PASSWORD) - its requests go direct", target.key());
            } else if (isProxied(target)) {
                log.info("Requests to {} go through the Oxylabs proxy at {}:{}", target.key(), this.host, this.port);
            }
        }
    }

    public boolean isConfigured() {
        return !host.isEmpty() && port > 0 && !username.isEmpty() && !password.isEmpty();
    }

    public boolean isEnabled(Target target) {
        return enabled.getOrDefault(target, false);
    }

    public boolean isProxied(Target target) {
        return isEnabled(target) && isConfigured();
    }

    /**
     * Sets the proxy of a builder derived from the shared client. Always set explicitly, even
     * when direct: a builder from {@link OkHttpClient#newBuilder()} carries the parent's proxy.
     */
    public OkHttpClient.Builder apply(OkHttpClient.Builder builder, Target target) {
        if (!isProxied(target)) {
            return builder
                    .proxy(Proxy.NO_PROXY)
                    .proxyAuthenticator(Authenticator.NONE);
        }
        final String credential = Credentials.basic(proxyUsername(), password);
        return builder
                .proxy(new Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved(host, port)))
                .proxyAuthenticator((_, response) -> {
                    // A 407 to a request already carrying the credential means they are wrong;
                    // answering it again would only loop. The preemptive CONNECT challenge
                    // OkHttp raises itself arrives without one, so it is answered here.
                    if (response.request().header("Proxy-Authorization") != null) {
                        return null;
                    }
                    return response.request().newBuilder()
                            .header("Proxy-Authorization", credential)
                            .build();
                });
    }

    /**
     * Oxylabs reads the options out of the username: {@code customer-<user>} for the account
     * and {@code -cc-<country>} to pick the exit country. Without a country the exit IP comes
     * from anywhere in their pool and rotates with every new connection.
     */
    String proxyUsername() {
        String user = username.startsWith("customer-") ? username : "customer-" + username;
        return country.isEmpty() ? user : user + "-cc-" + country.toUpperCase();
    }

    /** What the metrics page shows: whether each target is proxied, never the credentials. */
    public Map<String, Object> status() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("provider", "Oxylabs");
        status.put("endpoint", host.isEmpty() ? "" : host + ":" + port);
        status.put("configured", isConfigured());
        status.put("country", country.toUpperCase());
        Map<String, Object> targets = new LinkedHashMap<>();
        for (Target target : Target.values()) {
            targets.put(target.key(), Map.of(
                    "enabled", isEnabled(target),
                    "proxied", isProxied(target)
            ));
        }
        status.put("targets", targets);
        return status;
    }
}
