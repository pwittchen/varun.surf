package com.github.pwittchen.varun.http;

import okhttp3.OkHttpClient;
import org.springframework.stereotype.Component;

@Component
public class HttpClientProvider {

    private final OkHttpClient client;

    public HttpClientProvider() {
        client = new OkHttpClient
                .Builder()
                .followRedirects(false)
                .followSslRedirects(false)
                .build();
    }

    public OkHttpClient getHttpClient() {
        return client;
    }

}
