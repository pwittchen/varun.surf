package com.github.pwittchen.varun.service.live.strategy;

import com.github.pwittchen.varun.model.live.CurrentConditions;
import com.github.pwittchen.varun.service.live.FetchCurrentConditions;
import com.github.pwittchen.varun.service.live.FetchCurrentConditionsStrategyBase;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Fetches current conditions for the Prasonisi spot on Rhodes (Greece) from the
 * ProCenter C. Kirschner live wind monitor available at
 * <a href="https://www.prasonisi.com/index.php/wind.html">prasonisi.com</a>.
 * The page reads live values from a {@code SimpleAjax.php} endpoint via a POST request
 * with {@code type=windmonitor}, returning a JSON object with comma-separated decimal values, e.g.:
 * {@code {"d":"307","d_text":"NW","v_kn":"16,0","v_ms":"8,2","vavg_kn":"13,6","vavg_ms":"7,0","max_kn":"22,4","max_ms":"11,5"}}.
 */
@Component
public class FetchCurrentConditionsStrategyPrasonisi extends FetchCurrentConditionsStrategyBase implements FetchCurrentConditions {

    private static final int PRASONISI_WG_ID = 805332;
    private static final String PRASONISI_WIND_MONITOR_URL = "https://www.prasonisi.com/SimpleAjax.php";
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final OkHttpClient httpClient;
    private final Gson gson;

    public FetchCurrentConditionsStrategyPrasonisi(OkHttpClient httpClient, Gson gson) {
        this.httpClient = httpClient;
        this.gson = gson;
    }

    @Override
    public boolean canProcess(int wgId) {
        return wgId == PRASONISI_WG_ID;
    }

    @Override
    public Mono<CurrentConditions> fetchCurrentConditions(int wgId) {
        var url = getUrl(wgId);
        return fetchCurrentConditions(url);
    }

    @Override
    protected Mono<CurrentConditions> fetchCurrentConditions(String url) {
        return Mono.fromCallable(() -> {
            RequestBody formBody = new FormBody.Builder()
                    .add("type", "windmonitor")
                    .build();

            Request request = new Request.Builder()
                    .url(url)
                    .post(formBody)
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

                if (json == null || !json.has("v_kn")) {
                    throw new RuntimeException("No current conditions data available");
                }

                int windSpeed = (int) Math.round(parseDecimal(json.get("v_kn").getAsString()));
                int windGust = (int) Math.round(parseDecimal(json.get("max_kn").getAsString()));
                int windDirectionDeg = (int) Math.round(parseDecimal(json.get("d").getAsString()));
                String windDirection = windDirectionDegreesToCardinal(windDirectionDeg);
                String timestamp = ZonedDateTime.now(ZoneId.of("Europe/Athens"))
                        .format(TIMESTAMP_FORMATTER);

                // The station does not expose temperature, so it is reported as 0.
                return new CurrentConditions(timestamp, windSpeed, windGust, windDirection, 0);
            }
        });
    }

    /**
     * Parses a number that may use a comma as the decimal separator (e.g. "16,0").
     */
    private double parseDecimal(String value) {
        return Double.parseDouble(value.trim().replace(',', '.'));
    }

    @Override
    protected String getUrl(int wgId) {
        return PRASONISI_WIND_MONITOR_URL;
    }

    @Override
    protected OkHttpClient getHttpClient() {
        return httpClient;
    }
}
