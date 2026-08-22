package com.github.pwittchen.varun.service.ai;

import com.github.pwittchen.varun.model.forecast.Forecast;
import com.github.pwittchen.varun.model.forecast.HourlyForecast;
import com.github.pwittchen.varun.model.spot.Spot;
import com.github.pwittchen.varun.model.spot.SpotInfo;
import org.springframework.ai.chat.client.ChatClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public abstract class AiService {

    // Hours sent one row per hour. This is the range close enough that the exact
    // hour decides whether a session happens.
    private static final int DETAILED_HOURS = 48;

    // Beyond that, every third hour. Windguru itself steps to three-hourly after
    // about three days, so the hours in between are held-forward copies - sending
    // them would spend tokens repeating values the forecast never made.
    private static final int COARSE_STRIDE = 3;

    private final ChatClient chatClient;

    public AiService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /**
     * Summarise a spot's conditions.
     *
     * @param spot   the spot, for its name, country and LLM context
     * @param hourly the spot's hourly forecast (as served by /api/v1/forecast/{wgId}) -
     *               the only forecast data the prompt carries, so a spot without it
     *               gets no analysis at all rather than one written from a name
     */
    public Mono<String> fetchAiAnalysis(Spot spot, HourlyForecast hourly) {
        if (spot.name().isEmpty() || spot.country().isEmpty()) {
            return Mono.empty();
        }

        List<Forecast> rows = selectRows(hourly);
        if (rows.isEmpty()) {
            return Mono.empty();
        }

        String prompt = buildPrompt(spot, rows);

        return chatClient
                .prompt()
                .user(prompt)
                .stream()
                .content()
                .delayElements(Duration.ofSeconds(1))
                .timeout(Duration.ofSeconds(15))
                .retry(3)
                .collectList()
                .map(list -> String.join("", list));
    }

    protected String buildPrompt(Spot spot, List<Forecast> rows) {
        boolean withWaves = hasWaveData(rows);
        return String.format(
                createPromptTemplate(),
                buildCustomContext(spot),
                spot.name(),
                spot.country(),
                withWaves ? createColumnsWithWaves() : createColumnsWithoutWaves(),
                transformToToon(rows, withWaves)
        );
    }

    /**
     * The spot's optional per-spot prompt fragment ({@code llmComment} in
     * spots.json), taken from the {@link SpotInfo} written in this service's own
     * language so the analysis is not steered by a comment in another one.
     */
    protected String buildCustomContext(Spot spot) {
        String comment = llmComment(spotInfoForLanguage(spot));
        if (comment.isEmpty()) {
            comment = llmComment(spot.spotInfo());
        }
        if (comment.isEmpty()) {
            return "";
        }
        return String.format(createPromptPartForAdditionalContext(), comment);
    }

    private String llmComment(SpotInfo spotInfo) {
        if (spotInfo == null || spotInfo.llmComment() == null) {
            return "";
        }
        return spotInfo.llmComment().trim();
    }

    /**
     * Thin the aligned forecast down to the rows the prompt carries: every hour
     * while the exact hour still decides a session, every third hour after that.
     *
     * The forecast is already aligned to whole hours from now, so index and hour
     * are the same thing and the stride can be applied by position.
     */
    protected List<Forecast> selectRows(HourlyForecast hourly) {
        if (hourly == null || hourly.isEmpty()) {
            return List.of();
        }

        List<Forecast> hours = hourly.hours();
        List<Forecast> selected = new ArrayList<>();
        for (int hour = 0; hour < hours.size(); hour += hour < DETAILED_HOURS ? 1 : COARSE_STRIDE) {
            selected.add(hours.get(hour));
        }
        return selected;
    }

    /**
     * Transforms an aligned hourly forecast to TOON (Token-Optimized Object
     * Notation) rows.
     *
     * Format: time|wind|gust|dir|temp|rain|cloud|pressure (plus |wave|period|waveDir
     * when the spot has wave data)
     * Example: Sat 14:00|12|18|NE|21|0.0|40|1013|0.4|4|SW
     */
    protected String transformToToon(List<Forecast> rows, boolean withWaves) {
        StringBuilder toon = new StringBuilder();

        for (Forecast row : rows) {
            if (!toon.isEmpty()) {
                toon.append('\n');
            }
            toon.append(shortenHour(row.date()))
                    .append('|').append(Math.round(row.wind()))
                    .append('|').append(Math.round(row.gusts()))
                    .append('|').append(row.direction() == null || row.direction().isEmpty() ? "-" : row.direction())
                    .append('|').append(Math.round(row.temp()))
                    .append('|').append(String.format(Locale.US, "%.1f", row.precipitation()))
                    .append('|').append(Math.round(row.cloudCoverPercent()))
                    .append('|').append(Math.round(row.pressureHpa()));

            if (withWaves) {
                toon.append('|').append(row.wave() == null ? "-" : String.format(Locale.US, "%.1f", row.wave()))
                        .append('|').append(row.wavePeriod() == null ? "-" : Math.round(row.wavePeriod()))
                        .append('|').append(row.waveDirection() == null ? "-" : row.waveDirection());
            }
        }

        return toon.toString();
    }

    /**
     * Whether any row carries a wave height. Inland spots never do, and three
     * columns of dashes on every row of every lake is a lot of tokens spent
     * saying nothing.
     */
    protected boolean hasWaveData(List<Forecast> rows) {
        return rows.stream().anyMatch(row -> row.wave() != null);
    }

    /**
     * "Sat 22 Aug 2026 14:00" -> "Sat 14:00". The grid is shorter than a week, so
     * a weekday and an hour name the row unambiguously, and the tokens saved are
     * spent on every row of every spot.
     */
    private String shortenHour(String hour) {
        String[] parts = hour.split(" ");
        return parts.length >= 5 ? parts[0] + " " + parts[4] : hour;
    }

    /**
     * The {@link SpotInfo} carrying this service's language, so the per-spot
     * comment reaches the prompt in the language the analysis is written in.
     * Falls back to the English one when the spot has no translation.
     */
    protected abstract SpotInfo spotInfoForLanguage(Spot spot);

    public abstract String createPromptTemplate();

    public abstract String createPromptPartForAdditionalContext();

    /**
     * Column list for a spot with wave data.
     */
    public abstract String createColumnsWithWaves();

    /**
     * Column list for a spot without it, so the header never promises a column
     * the rows don't carry.
     */
    public abstract String createColumnsWithoutWaves();
}
