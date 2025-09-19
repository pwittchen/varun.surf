package com.github.pwittchen.varun.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.time.Year;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WeatherForecast {

    private static final String MICRO_WINDGURU_BASE_URL = "https://micro.windguru.cz";
    private static final OkHttpClient HTTP_CLIENT = new OkHttpClient();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * @param model one of: "uwrftar" (WRF* 1 km Tarifa), "wrfgis" (WRF 3 km Gibraltar), "gfs", or "all"
     * @return Mono with JSON array string of forecast rows
     */
    public Mono<String> getForecast(int spotId, String model) {
        final HttpUrl httpUrl = HttpUrl.parse(MICRO_WINDGURU_BASE_URL);

        if (httpUrl == null) {
            return Mono.empty();
        }

        String url = httpUrl
                .newBuilder()
                .addQueryParameter("s", String.valueOf(spotId))       // Tarifa spot id = 43
                .addQueryParameter("m", model)      // model: uwrftar / wrfgis / gfs / all
                .build()
                .toString();

        Request req = new Request.Builder().url(url).get()
                .header("User-Agent", "OkHttp Tarifa Forecast Client")
                .build();

        return executeHttpRequest(req).map(WeatherForecast::parseMicroTableToJson);
    }

    // --- OkHttp -> Reactor bridge ---
    private Mono<String> executeHttpRequest(Request request) {
        return Mono.<String>create(sink -> {
            Call call = HTTP_CLIENT.newCall(request);
            sink.onCancel(call::cancel);
            call.enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    sink.error(e);
                }

                @Override
                public void onResponse(Call call, Response response) {
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

    // --- Parser for micro.windguru.cz text table -> JSON ---
    private static String parseMicroTableToJson(String microText) {
        // The micro output is a text table. Data rows start with: "Mon 8. 08h" etc.
        // We'll extract: dateLabel, WSPD, GUST, WDIRN, WDEG, TMP, SLP, HCLD, MCLD, LCLD, APCP, APCP1, RH.
        List<Map<String, Object>> rows = new ArrayList<>();

        // Split by lines, skip header blocks
        String[] lines = microText.split("\\r?\\n");
        // Pattern for rows like: "Mon 8. 08h      10      11       E      90      25    1016  ...  69"
        // Tolerant spacing, optional dashes (“-”) for missing values.
        Pattern row = Pattern.compile(
                // date label groups: Ddd dd. Hhh
                "^(Mon|Tue|Wed|Thu|Fri|Sat|Sun)\\s+\\d{1,2}\\.\\s+\\d{2}h\\s+" +
                        "(-?\\d+)?\\s+" +          // WSPD
                        "(-?\\d+)?\\s+" +          // GUST
                        "([A-Z]{1,3})\\s+" +       // WDIRN
                        "(-?\\d+)?\\s+" +          // WDEG
                        "(-?\\d+)?\\s+" +          // TMP
                        "(-?\\d+)?\\s+" +          // SLP
                        "(-|\\d{1,3})\\s+" +       // HCLD %
                        "(-|\\d{1,3})\\s+" +       // MCLD %
                        "(-|\\d{1,3})\\s+" +       // LCLD %
                        "(-|\\d+(?:\\.\\d+)?)\\s+" +  // APCP mm/3h or '-'
                        "(-|\\d+(?:\\.\\d+)?)\\s+" +  // APCP1 mm/1h or '-'
                        "(-|\\d{1,3})$"             // RH %
        );

        // We’ll also capture the most recent “init” line for metadata if present.
        String initModel = null;
        String initRun = null;
        Pattern initPattern = Pattern.compile("^(.*)\\(init:\\s*(\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}\\s+UTC)\\)");

        for (String line : lines) {
            line = line.trim().replace('\u00A0', ' '); // non-breaking spaces → space

            Matcher mi = initPattern.matcher(line);
            if (mi.find()) {
                initModel = mi.group(1).trim();
                initRun = mi.group(2).trim();
                continue;
            }

            Matcher m = row.matcher(line);
            if (m.find()) {
                Map<String, Object> obj = new LinkedHashMap<>();
                obj.put("label", line.substring(0, line.indexOf('h') + 1));      // e.g., "Mon 8. 08h"
                obj.put("wspd_kn", parseNum(m.group(2)));
                obj.put("gust_kn", parseNum(m.group(3)));
                obj.put("wdir_compass", m.group(4));
                obj.put("wdir_deg", parseNum(m.group(5)));
                obj.put("temp_c", parseNum(m.group(6)));
                obj.put("slp_hpa", parseNum(m.group(7)));
                obj.put("cloud_high_pct", parseNum(m.group(8)));
                obj.put("cloud_mid_pct", parseNum(m.group(9)));
                obj.put("cloud_low_pct", parseNum(m.group(10)));
                obj.put("apcp_mm_3h", parseNum(m.group(11)));
                obj.put("apcp_mm_1h", parseNum(m.group(12)));
                obj.put("rh_pct", parseNum(m.group(13)));
                rows.add(obj);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("spot", Map.of("name", "Tarifa", "id", 43));
        if (initModel != null) {
            result.put("model_info", Map.of("line", initModel, "init", initRun));
        }
        result.put("generated_year_guess", Year.now().getValue());
        result.put("rows", rows);

        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(result);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private static Number parseNum(String s) {
        if (s == null || s.equals("-")) return null;
        try {
            if (s.contains(".")) return Double.parseDouble(s);
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
