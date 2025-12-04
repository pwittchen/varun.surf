package com.github.pwittchen.varun.service.live.strategy;

import com.github.pwittchen.varun.http.HttpClientProxy;
import com.github.pwittchen.varun.model.live.CurrentConditions;
import com.github.pwittchen.varun.service.live.FetchCurrentConditions;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import reactor.core.publisher.Mono;

/**
 * Strategy for fetching current conditions from the Podersdorf spot in Austria basing on kiteriders.at website
 */
public class FetchCurrentConditionsStrategyPodersdorf extends FetchCurrentConditionsStrategyBase implements FetchCurrentConditions {

    private static final int PODERSDORF_WG_ID = 859182;
    private static final String KITERIDERS_LIVE_READINGS = "https://www.kiteriders.at/wind/weatherstat_kn.html";

    private final OkHttpClient httpClient;

    public FetchCurrentConditionsStrategyPodersdorf(HttpClientProxy httpClientProxy) {
        this.httpClient = httpClientProxy.getHttpClient();
    }

    @Override
    public boolean canProcess(int wgId) {
        return wgId == PODERSDORF_WG_ID;
    }

    @Override
    public Mono<CurrentConditions> fetchCurrentConditions(int wgId) {
        var url = getUrl(wgId);
        return fetchCurrentConditions(url);
    }

    @Override
    protected String getUrl(int wgId) {
        return KITERIDERS_LIVE_READINGS;
    }

    @Override
    protected OkHttpClient getHttpClient() {
        return httpClient;
    }

    @Override
    public Mono<CurrentConditions> fetchCurrentConditions(String url) {
        return Mono.fromCallable(() -> {
            Request request = new Request.Builder()
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
                String[] lines = body.split("\n");

                String dataRow = null;

                int trCount = 0;
                for (String line : lines) {
                    if (line.trim().startsWith("<tr")) {
                        trCount++;
                        if (trCount == 2) {
                            dataRow = line;
                            break;
                        }
                    }
                }

                if (dataRow == null) {
                    throw new RuntimeException("No data row found");
                }

                String[] cells = dataRow.split("</td>");

                String dateCell = extractTextFromTd(cells[0]);
                String timeCell = extractTextFromTd(cells[1]);
                String directionCell = extractTextFromTd(cells[2]);
                String windCell = extractTextFromTd(cells[4]);
                String gustCell = extractTextFromTd(cells[6]);
                String tempCell = extractTextFromTd(cells[7]);

                String date = dateCell + " " + timeCell;
                String direction = normalizeDirection(directionCell);
                int wind = (int) Math.round(parseKnots(windCell));
                int gusts = (int) Math.round(parseKnots(gustCell));
                int temp = (int) Math.round(parseTemperature(tempCell));

                return new CurrentConditions(date, wind, gusts, direction, temp);
            }
        });
    }

    private String extractTextFromTd(String tdContent) {
        String text = tdContent.replaceAll("<[^>]*>", "").trim();
        text = text.replace("&nbsp;", "");
        return text.trim();
    }

    private double parseKnots(String value) {
        return Double.parseDouble(value.replace("kn", "").trim());
    }

    private double parseTemperature(String value) {
        return Double.parseDouble((value.split(" ")[0]).trim());
    }
}
