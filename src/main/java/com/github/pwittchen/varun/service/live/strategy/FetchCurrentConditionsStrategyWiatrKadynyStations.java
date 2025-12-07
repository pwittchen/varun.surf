package com.github.pwittchen.varun.service.live.strategy;

import com.github.pwittchen.varun.model.live.CurrentConditions;
import com.github.pwittchen.varun.service.live.FetchCurrentConditions;
import com.github.pwittchen.varun.service.live.FetchCurrentConditionsStrategyBase;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Strategy for fetching current conditions from the Polish coast near Puck Bay basing on wiatrkadyny.pl website
 */
@Component
public class FetchCurrentConditionsStrategyWiatrKadynyStations extends FetchCurrentConditionsStrategyBase implements FetchCurrentConditions {

    private static final Map<Integer, String> LIVE_CONDITIONS_URLS = Map.of(
            9153554, "https://www.wiatrkadyny.pl/wiatrkadyny.txt", // generated ID for the fallback WG ID
            509469, "https://www.wiatrkadyny.pl/kuznica/wiatrkadyny.txt",
            500760, "https://www.wiatrkadyny.pl/draga/wiatrkadyny.txt",
            4165, "https://www.wiatrkadyny.pl/rewa/wiatrkadyny.txt"
    );

    private final OkHttpClient httpClient;

    public FetchCurrentConditionsStrategyWiatrKadynyStations(OkHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public boolean canProcess(int wgId) {
        return LIVE_CONDITIONS_URLS.containsKey(wgId);
    }

    @Override
    public Mono<CurrentConditions> fetchCurrentConditions(int wgId) {
        var url = getUrl(wgId);
        return fetchCurrentConditions(url);
    }

    @Override
    protected String getUrl(int wgId) {
        return LIVE_CONDITIONS_URLS.get(wgId);
    }

    @Override
    protected OkHttpClient getHttpClient() {
        return httpClient;
    }

    @Override
    public Mono<CurrentConditions> fetchCurrentConditions(String url) {
        return Mono.fromCallable(() -> {
            Request request = new Request
                    .Builder()
                    .url(url)
                    .build();

            try (Response response = getHttpClient().newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new RuntimeException("Failed to fetch forecast: " + response);
                }

                ResponseBody responseBody = response.body();

                if (responseBody == null) {
                    throw new RuntimeException("Failed to fetch forecast: " + response);
                }

                String body = responseBody.string();
                String[] parts = body.trim().split("\\s+");

                String date = parts[0] + " " + parts[1];
                int temp = (int) Math.round(Double.parseDouble(parts[2]));
                int wind = (int) Math.round(Double.parseDouble(parts[5]));
                String direction = normalizeDirection(parts[11]);
                int gusts = (int) Math.round(Double.parseDouble(parts[6]));

                return new CurrentConditions(date, wind, gusts, direction, temp);
            }
        });
    }
}
