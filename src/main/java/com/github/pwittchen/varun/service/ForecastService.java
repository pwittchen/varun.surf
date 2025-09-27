package com.github.pwittchen.varun.service;

import com.github.pwittchen.varun.mapper.WeatherForecastMapper;
import com.github.pwittchen.varun.model.Forecast;
import com.github.pwittchen.varun.model.ForecastModel;
import com.github.pwittchen.varun.model.ForecastWg;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class ForecastService {
    private static final String URL = "https://micro.windguru.cz";
    private final OkHttpClient httpClient;
    private final WeatherForecastMapper mapper;

    public ForecastService(WeatherForecastMapper mapper) {
        this.httpClient = new OkHttpClient();
        this.mapper = mapper;
    }

    public Flux<Forecast> getForecast(int spotId, ForecastModel model) {
        final HttpUrl httpUrl = HttpUrl.parse(URL);
        if (httpUrl == null) return Flux.empty();
        return executeHttpRequest(new Request
                .Builder()
                .url(httpUrl
                        .newBuilder()
                        .addQueryParameter("s", String.valueOf(spotId))
                        .addQueryParameter("m", model.toString())
                        .build()
                        .toString())
                .get()
                .build())
                .map(this::retrieveWgForecasts)
                .map(mapper::toWeatherForecastsFlux)
                .flatMapMany(Function.identity());
    }

    private Mono<String> executeHttpRequest(final Request request) {
        return Mono.<String>create(sink -> {
            Call call = httpClient.newCall(request);
            sink.onCancel(call::cancel);
            call.enqueue(new Callback() {
                @Override
                public void onFailure(@NotNull Call call, @NotNull IOException e) {
                    sink.error(e);
                }

                @Override
                public void onResponse(@NotNull Call call, @NotNull Response response) {
                    try (response) {
                        if (!response.isSuccessful()) {
                            sink.error(new IOException("HTTP " + response.code() + ": " + response.message()));
                            return;
                        }
                        ResponseBody body = response.body();
                        sink.success(body != null ? body.string() : "");
                    } catch (Exception e) {
                        sink.error(e);
                    }
                }
            });
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private List<ForecastWg> retrieveWgForecasts(final String microText) {
        String[] lines = microText.split("\\r?\\n");
        Pattern row = Pattern.compile(
                "^(Mon|Tue|Wed|Thu|Fri|Sat|Sun)\\s+\\d{1,2}\\.\\s+\\d{2}h\\s+" +    // date label groups: Ddd dd. Hhh
                        "(-?\\d+)?\\s+" +                                           // WSPD
                        "(-?\\d+)?\\s+" +                                           // GUST
                        "([A-Z]{1,3})\\s+" +                                        // WDIRN
                        "(-?\\d+)?\\s+" +                                           // WDEG
                        "(-?\\d+)?\\s+" +                                           // TMP
                        "(-?\\d+)?\\s+" +                                           // SLP
                        "(-|\\d{1,3})\\s+" +                                        // HCLD %
                        "(-|\\d{1,3})\\s+" +                                        // MCLD %
                        "(-|\\d{1,3})\\s+" +                                        // LCLD %
                        "(-|\\d+(?:\\.\\d+)?)\\s+" +                                // APCP mm/3h or '-'
                        "(-|\\d+(?:\\.\\d+)?)\\s+" +                                // APCP1 mm/1h or '-'
                        "(-|\\d{1,3})$"                                             // RH %
        );

        return Arrays
                .stream(lines)
                .map(line -> parseLineToForecast(line, row))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toCollection(LinkedList::new));
    }

    private Optional<ForecastWg> parseLineToForecast(String line, Pattern row) {
        line = line.trim().replace('\u00A0', ' '); // non-breaking spaces → space
        Matcher m = row.matcher(line);
        if (m.find()) return Optional.of(createForecast(line, m));
        return Optional.empty();
    }

    private ForecastWg createForecast(String line, Matcher m) {
        return new ForecastWg(
                line.substring(0, line.indexOf('h') + 1),
                parseNumber(m.group(2)).intValue(),
                parseNumber(m.group(3)).intValue(),
                m.group(4),
                parseNumber(m.group(5)).intValue(),
                parseNumber(m.group(6)).intValue(),
                parseNumber(m.group(7)).intValue(),
                parseNumber(m.group(8)).intValue(),
                parseNumber(m.group(9)).intValue(),
                parseNumber(m.group(10)).intValue(),
                parseNumber(m.group(11)).intValue(),
                parseNumber(m.group(12)).intValue(),
                parseNumber(m.group(13)).intValue()
        );
    }

    private Number parseNumber(String s) {
        if (s == null || s.equals("-")) return 0;
        try {
            if (s.contains(".")) return Double.parseDouble(s);
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
