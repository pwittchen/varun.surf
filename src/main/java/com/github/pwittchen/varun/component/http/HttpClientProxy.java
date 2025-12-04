package com.github.pwittchen.varun.component.http;

import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Component
public class HttpClientProxy {

    private final OkHttpClient client;

    public HttpClientProxy() {
        client = new OkHttpClient
                .Builder()
                .connectTimeout(Duration.ofSeconds(10))
                .readTimeout(Duration.ofSeconds(30))
                .writeTimeout(Duration.ofSeconds(10))
                .callTimeout(Duration.ofSeconds(45))
                .connectionPool(new ConnectionPool(200, 5, TimeUnit.MINUTES))
                .followRedirects(false)
                .followSslRedirects(false)
                .retryOnConnectionFailure(true)
                .build();
    }

    public OkHttpClient getHttpClient() {
        return client;
    }

}
