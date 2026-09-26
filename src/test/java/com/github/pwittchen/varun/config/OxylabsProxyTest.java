package com.github.pwittchen.varun.config;

import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.Proxy;
import java.util.Map;

import static com.google.common.truth.Truth.assertThat;

class OxylabsProxyTest {

    private MockWebServer proxyServer;

    @BeforeEach
    void setUp() throws IOException {
        proxyServer = new MockWebServer();
        proxyServer.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        proxyServer.shutdown();
    }

    @Test
    void shouldBeDisabledForEveryTargetByDefault() {
        OxylabsProxy proxy = proxy("", "", "", false, false, false);

        for (OxylabsProxy.Target target : OxylabsProxy.Target.values()) {
            assertThat(proxy.isEnabled(target)).isFalse();
            assertThat(proxy.isProxied(target)).isFalse();
        }
        assertThat(proxy.isConfigured()).isFalse();
    }

    @Test
    void shouldProxyOnlyTheEnabledTargets() {
        OxylabsProxy proxy = proxy("user", "secret", "", true, false, false);

        assertThat(proxy.isProxied(OxylabsProxy.Target.WINDGURU)).isTrue();
        assertThat(proxy.isProxied(OxylabsProxy.Target.LIVE_STATIONS)).isFalse();
        assertThat(proxy.isProxied(OxylabsProxy.Target.OTHER)).isFalse();
    }

    @Test
    void shouldGoDirectWhenEnabledWithoutCredentials() {
        OxylabsProxy proxy = proxy("user", "", "", true, true, true);

        assertThat(proxy.isEnabled(OxylabsProxy.Target.WINDGURU)).isTrue();
        assertThat(proxy.isProxied(OxylabsProxy.Target.WINDGURU)).isFalse();
        OkHttpClient client = proxy.apply(new OkHttpClient.Builder(), OxylabsProxy.Target.WINDGURU).build();
        assertThat(client.proxy()).isEqualTo(Proxy.NO_PROXY);
    }

    @Test
    void shouldOverrideTheProxyInheritedFromTheParentClient() {
        OxylabsProxy proxy = proxy("user", "secret", "", false, false, true);
        OkHttpClient parent = proxy.apply(new OkHttpClient.Builder(), OxylabsProxy.Target.OTHER).build();

        OkHttpClient derived = proxy.apply(parent.newBuilder(), OxylabsProxy.Target.WINDGURU).build();

        assertThat(parent.proxy().type()).isEqualTo(Proxy.Type.HTTP);
        assertThat(derived.proxy()).isEqualTo(Proxy.NO_PROXY);
    }

    @Test
    void shouldPrefixTheUsernameAndAppendTheCountry() {
        assertThat(proxy("user", "secret", "", true, false, false).proxyUsername())
                .isEqualTo("customer-user");
        assertThat(proxy("customer-user", "secret", "pl", true, false, false).proxyUsername())
                .isEqualTo("customer-user-cc-PL");
    }

    @Test
    void shouldAnswerTheProxyChallengeWithCredentials() throws Exception {
        OxylabsProxy proxy = proxy("user", "secret", "pl", true, false, false);
        OkHttpClient client = proxy.apply(new OkHttpClient.Builder(), OxylabsProxy.Target.WINDGURU).build();
        proxyServer.enqueue(new MockResponse().setResponseCode(407).addHeader("Proxy-Authenticate", "Basic realm=\"oxylabs\""));
        proxyServer.enqueue(new MockResponse().setBody("forecast"));

        try (Response response = client.newCall(new Request.Builder().url("http://micro.windguru.cz/?s=1").build()).execute()) {
            assertThat(response.code()).isEqualTo(200);
            assertThat(response.body().string()).isEqualTo("forecast");
        }

        RecordedRequest challenged = proxyServer.takeRequest();
        RecordedRequest authorized = proxyServer.takeRequest();
        assertThat(challenged.getRequestLine()).contains("http://micro.windguru.cz/?s=1");
        assertThat(challenged.getHeader("Proxy-Authorization")).isNull();
        assertThat(authorized.getHeader("Proxy-Authorization"))
                .isEqualTo(Credentials.basic("customer-user-cc-PL", "secret"));
    }

    @Test
    void shouldGiveUpWhenTheCredentialsAreRejected() throws IOException {
        OxylabsProxy proxy = proxy("user", "wrong", "", true, false, false);
        OkHttpClient client = proxy.apply(new OkHttpClient.Builder(), OxylabsProxy.Target.WINDGURU).build();
        proxyServer.enqueue(new MockResponse().setResponseCode(407));
        proxyServer.enqueue(new MockResponse().setResponseCode(407));
        proxyServer.enqueue(new MockResponse().setResponseCode(407));

        try (Response response = client.newCall(new Request.Builder().url("http://micro.windguru.cz/").build()).execute()) {
            assertThat(response.code()).isEqualTo(407);
        }

        assertThat(proxyServer.getRequestCount()).isEqualTo(2);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldReportStatusWithoutCredentials() {
        OxylabsProxy proxy = proxy("user", "secret", "de", false, true, false);

        Map<String, Object> status = proxy.status();

        assertThat(status).containsEntry("provider", "Oxylabs");
        assertThat(status).containsEntry("configured", true);
        assertThat(status).containsEntry("country", "DE");
        assertThat(status.toString()).doesNotContain("secret");
        assertThat(status.toString()).doesNotContain("user");
        Map<String, Object> targets = (Map<String, Object>) status.get("targets");
        assertThat(targets.keySet()).containsExactly("windguru", "liveStations", "other").inOrder();
        assertThat(targets.get("liveStations")).isEqualTo(Map.of("enabled", true, "proxied", true));
    }

    private OxylabsProxy proxy(String username, String password, String country,
                               boolean windguru, boolean liveStations, boolean other) {
        return new OxylabsProxy(proxyServer.getHostName(), proxyServer.getPort(),
                username, password, country, windguru, liveStations, other);
    }
}
