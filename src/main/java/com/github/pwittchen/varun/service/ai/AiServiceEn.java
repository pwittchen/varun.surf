package com.github.pwittchen.varun.service.ai;

import com.google.gson.Gson;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class AiServiceEn extends AiService {
    private static final String PROMPT_TEMPLATE = """
            SYSTEM:
            You are a professional kitesurfing weather analyst.
            You analyze wind, waves, temperature and forecast data for kitesurfers.
            Your task is to write a short and accurate 2–3 sentence summary of the forecast conditions.
            Always mention:
            - wind strength (in knots, kts)
            - wind direction (using compass letters: N, NE, E, SE, S, SW, W, NW)
            - general rideability
            - any risks or highlights (e.g., gusts, rain, temperature)
            - recommended kite sizes or equipment
            
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
            Forecast data (JSON):
            %s
            
            Using only the data above,
            describe the current and upcoming kitesurfing conditions at this spot in 2–3 sentences.
            Do not invent numbers or details. Use kts, °C, and compass directions as appropriate.
            """;

    private static final String PROMPT_PART_ADDITIONAL_CONTEXT = "\n\nADDITIONAL SPOT-SPECIFIC CONTEXT:\n%s\n";

    public AiServiceEn(ChatClient chatClient, Gson gson) {
        super(chatClient, gson);
    }

    @Override
    public String createPromptTemplate() {
        return PROMPT_TEMPLATE;
    }

    @Override
    public String createPromptPartForAdditionalContext() {
        return PROMPT_PART_ADDITIONAL_CONTEXT;
    }
}
