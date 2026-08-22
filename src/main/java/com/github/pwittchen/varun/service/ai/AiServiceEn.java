package com.github.pwittchen.varun.service.ai;

import com.github.pwittchen.varun.model.spot.Spot;
import com.github.pwittchen.varun.model.spot.SpotInfo;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class AiServiceEn extends AiService {
    private static final String PROMPT_TEMPLATE = """
            SYSTEM:
            You are a professional kitesurfing weather analyst.
            You analyze hourly forecast data for kitesurfers.
            Your task is to write a short and accurate 3–4 sentence summary of the forecast conditions.
            Always mention:
            - wind strength (in knots, kts)
            - wind direction (using compass letters: N, NE, E, SE, S, SW, W, NW)
            - general rideability
            - when the wind is rideable, as a day and an hour range (e.g., "Saturday 13:00-18:00")
            - recommended kite sizes or equipment

            Also use, where it changes what a rider would do or bring:
            - air temperature (°C) - what to wear, and when it is cold enough to matter
            - rain (mm per hour) - only when there is any
            - wave height (m), period (s) and direction - only for spots that have them;
              say whether the water is flat, choppy or wavy
            - cloud cover (%%) - mainly as a hint of sun or of thermal wind
            - pressure (hPa) - only when it moves sharply, which foreshadows unstable,
              gusty conditions

            Do not list every variable mechanically. Lead with the wind, then add only
            what a rider would actually act on.

            The data is hourly, so read it as a curve rather than a single number:
            say how the wind builds or drops within a day, and point out a rideable
            window even on a day that is mostly light. The rows run from the current
            hour, one per hour for the first two days and every three hours after that.

            Kite size logic:
            - Below 8 kts: riding is not possible.
            - 8–11 kts: riding possible only with a foil.
            - 12–14 kts: use a large kite (12–15-17 m²).
            - 15–18 kts: use a medium kite (11-12 m²).
            - 19–25 kts: use a small kite (9–10 m²).
            - 28+ kts: use a very small kite (5–6-7 m²) or consider safety limits.

            Be objective and concise — avoid emojis and filler words.
            %s
            USER:
            Spot name: %s
            Country: %s
            Hourly forecast (TOON format: %s), from the current hour onward:
            %s

            Using only the data above,
            describe the current and upcoming kitesurfing conditions at this spot in 3–4 sentences.
            Do not invent numbers, hours or details. A "-" means the value is unknown, so
            say nothing about it. Use kts, °C, m and compass directions as appropriate.
            """;

    private static final String PROMPT_PART_ADDITIONAL_CONTEXT = "\n\nADDITIONAL SPOT-SPECIFIC CONTEXT:\n%s\n";

    private static final String COLUMNS_WITH_WAVES =
            "time|wind|gust|dir|temp|rain|cloud|pressure|wave|wavePeriod|waveDir";

    private static final String COLUMNS_WITHOUT_WAVES =
            "time|wind|gust|dir|temp|rain|cloud|pressure";

    public AiServiceEn(ChatClient chatClient) {
        super(chatClient);
    }

    @Override
    protected SpotInfo spotInfoForLanguage(Spot spot) {
        return spot.spotInfo();
    }

    @Override
    public String createPromptTemplate() {
        return PROMPT_TEMPLATE;
    }

    @Override
    public String createPromptPartForAdditionalContext() {
        return PROMPT_PART_ADDITIONAL_CONTEXT;
    }

    @Override
    public String createColumnsWithWaves() {
        return COLUMNS_WITH_WAVES;
    }

    @Override
    public String createColumnsWithoutWaves() {
        return COLUMNS_WITHOUT_WAVES;
    }
}
