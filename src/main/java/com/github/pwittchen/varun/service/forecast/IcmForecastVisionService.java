package com.github.pwittchen.varun.service.forecast;

import com.github.pwittchen.varun.model.forecast.Forecast;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Optional;

@Service
public class IcmForecastVisionService {

    private static final Logger log = LoggerFactory.getLogger(IcmForecastVisionService.class);

    private static final String VISION_PROMPT = """
            You are analyzing a Polish ICM UM 4km weather forecast meteogram image from meteo.pl.

            The meteogram contains panels stacked vertically. Focus on:
            - WIND PANEL: Shows wind speed (colored area) and gusts (blue dashes above) in m/s,
              with direction arrows below the chart
            - TEMPERATURE PANEL: Shows temperature in degrees Celsius
            - PRECIPITATION PANEL: Shows rainfall/snow in mm

            Extract data at every 3-hour interval visible on the x-axis.

            For each time point extract:
            1. Date/time from x-axis labels (format: "DayAbbr DD MonAbbr YYYY HH:mm", e.g. "Mon 03 Mar 2026 06:00")
            2. Wind speed - read in m/s, convert to knots (multiply by 1.94384), round to 1 decimal
            3. Wind gusts - read in m/s, convert to knots, round to 1 decimal
            4. Wind direction from arrows as cardinal (N, NE, E, SE, S, SW, W, NW)
            5. Temperature in degrees Celsius, round to 1 decimal
            6. Precipitation in mm, round to 1 decimal

            Return ONLY a valid JSON array:
            [{"date":"Mon 03 Mar 2026 06:00","wind":12.5,"gusts":18.0,"direction":"NW","temp":5.0,"precipitation":0.0},...]
            No markdown, no explanation, only JSON.
            """;

    private static final Type FORECAST_LIST_TYPE = new TypeToken<List<Forecast>>() {}.getType();

    private final ChatClient chatClient;
    private final OkHttpClient httpClient;
    private final Gson gson;

    public IcmForecastVisionService(ChatClient chatClient, OkHttpClient httpClient, Gson gson) {
        this.chatClient = chatClient;
        this.httpClient = httpClient;
        this.gson = gson;
    }

    public Optional<List<Forecast>> extractForecastFromMeteogram(String icmUrl) {
        try {
            byte[] imageBytes = downloadImage(icmUrl);
            if (imageBytes == null || imageBytes.length == 0) {
                log.warn("Failed to download ICM meteogram from {}", icmUrl);
                return Optional.empty();
            }

            String response = chatClient
                    .prompt()
                    .user(u -> u
                            .text(VISION_PROMPT)
                            .media(MimeTypeUtils.IMAGE_PNG, new ByteArrayResource(imageBytes)))
                    .call()
                    .content();

            if (response == null || response.isBlank()) {
                log.warn("Empty response from vision API for ICM meteogram");
                return Optional.empty();
            }

            List<Forecast> forecasts = gson.fromJson(response.strip(), FORECAST_LIST_TYPE);
            if (forecasts == null || forecasts.isEmpty()) {
                log.warn("Parsed empty forecast list from vision API response");
                return Optional.empty();
            }

            log.info("Extracted {} forecast entries from ICM meteogram", forecasts.size());
            return Optional.of(forecasts);
        } catch (JsonSyntaxException e) {
            log.warn("Failed to parse vision API response as JSON: {}", e.getMessage());
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Failed to extract forecast from ICM meteogram: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private byte[] downloadImage(String url) {
        Request request = new Request.Builder().url(url).get().build();
        try (var response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                return response.body().bytes();
            }
            log.warn("ICM image download failed with status {}", response.code());
            return null;
        } catch (IOException e) {
            log.warn("ICM image download failed: {}", e.getMessage());
            return null;
        }
    }
}
