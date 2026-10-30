package com.github.pwittchen.varun.config;

import com.github.pwittchen.varun.metrics.HttpClientMetricsEventListener;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import org.junit.jupiter.api.Test;

import static com.google.common.truth.Truth.assertThat;

class OkHttpClientConfigTest {

    @Test
    void shouldKeepWindguruRequestsOffHttp2() {
        OkHttpClientConfig config = new OkHttpClientConfig();
        OxylabsProxy proxy = new OxylabsProxy("pr.oxylabs.io", 7777, "", "", "", false, false, false);
        OkHttpClient client = config.okHttpClient(new HttpClientMetricsEventListener(new SimpleMeterRegistry()), proxy);

        assertThat(config.windguruHttpClient(client, proxy).protocols()).containsExactly(Protocol.HTTP_1_1);
        assertThat(config.liveStationsHttpClient(client, proxy).protocols()).contains(Protocol.HTTP_2);
    }
}
