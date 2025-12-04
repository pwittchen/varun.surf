package com.github.pwittchen.varun.http;

import okhttp3.OkHttpClient;
import org.springframework.stereotype.Component;

@Component
public class HttpClientProxy {

    private final OkHttpClient client;

    public HttpClientProxy() {
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
