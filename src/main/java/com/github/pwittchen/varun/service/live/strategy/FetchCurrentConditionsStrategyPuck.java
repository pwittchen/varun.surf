package com.github.pwittchen.varun.service.live.strategy;

import com.github.pwittchen.varun.model.live.CurrentConditions;
import com.github.pwittchen.varun.service.live.FetchCurrentConditions;
import com.github.pwittchen.varun.service.live.FetchCurrentConditionsStrategyBase;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * This class is specifically designed to retrieve data for the WiatrKadyny Puck weather station located on Molo Puck.
 */
@Component
public class FetchCurrentConditionsStrategyPuck extends FetchCurrentConditionsStrategyBase implements FetchCurrentConditions {

    private static final int PUCK_WG_ID = 48009;
    private static final String PUCK_LIVE_READINGS_URL = "https://www.wiatrkadyny.pl/puck/realtimegauges.txt";

    private final OkHttpClient httpClient;
    private final Gson gson;

    public FetchCurrentConditionsStrategyPuck(OkHttpClient httpClient, Gson gson) {
        this.httpClient = httpClient;
        this.gson = gson;
    }

    @Override
    public boolean canProcess(int wgId) {
        return wgId == PUCK_WG_ID;
    }

    @Override
    public Mono<CurrentConditions> fetchCurrentConditions(int wgId) {
        var url = getUrl(wgId);
        return fetchCurrentConditions(url);
    }

    @Override
    protected Mono<CurrentConditions> fetchCurrentConditions(String url) {
        return Mono.fromCallable(() -> {
            Request request = new Request
                    .Builder()
                    .url(url)
                    .build();

            try (Response response = getHttpClient().newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new RuntimeException("Failed to fetch current conditions: " + response);
                }

                ResponseBody responseBody = response.body();
                if (responseBody == null) {
                    throw new RuntimeException("Failed to fetch current conditions: response body is null");
                }

                String body = responseBody.string();
                JsonObject json = gson.fromJson(body, JsonObject.class);

                String timestamp = parseTimestamp(json);
                int windSpeed = (int) Math.round(Double.parseDouble(json.get("wspeed").getAsString()));
                int windGust = (int) Math.round(Double.parseDouble(json.get("wgust").getAsString()));
                int windDirectionDeg = Integer.parseInt(json.get("bearing").getAsString());
                String windDirection = windDirectionDegreesToCardinal(windDirectionDeg);
                int temp = (int) Math.round(Double.parseDouble(json.get("temp").getAsString()));

                return new CurrentConditions(timestamp, windSpeed, windGust, windDirection, temp);
            }
        });
    }

    private String parseTimestamp(JsonObject json) {
        // timeUTC format: "2025,7,1,6,12,8" -> year,month,day,hour,minute,second
        String timeUTC = json.get("timeUTC").getAsString();
        String[] parts = timeUTC.split(",");
        return String.format("%s-%02d-%02d %02d:%02d:%02d",
                parts[0],
                Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2]),
                Integer.parseInt(parts[3]),
                Integer.parseInt(parts[4]),
                Integer.parseInt(parts[5]));
    }

    @Override
    protected String getUrl(int wgId) {
        return PUCK_LIVE_READINGS_URL;
    }

    @Override
    protected OkHttpClient getHttpClient() {
        return httpClient;
    }
}
