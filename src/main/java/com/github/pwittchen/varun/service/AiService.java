package com.github.pwittchen.varun.service;

import com.github.pwittchen.varun.model.Spot;
import com.google.gson.Gson;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Service
public class AiService {
    private final static String PROMPT = """
            SYSTEM:
            You are a professional kitesurfing weather analyst.\s
            You analyze wind, waves, temperature and forecast data for kitesurfers.
            Your task is to write a short and accurate 2–3 sentence summary of the forecast conditions.
            Always mention the wind strength (in knots), direction (using compass letters like N, NE, E, SE, S, SW, W, NW),
            general rideability, and any risks or highlights. Suggest what sizes of kites to take.
            Be objective and concise — avoid emojis and filler words.

            USER:
            Spot name: %s
            Country: %s
            Forecast data (JSON):
            %s

            Using only the data above, describe the current and upcoming kitesurfing conditions at this spot in 2–3 sentences.
            Do not invent numbers or details. Use kts, °C, and compass directions as appropriate.
            """;

    private final ChatClient chatClient;
    private final Gson gson;

    public AiService(ChatClient chatClient, Gson gson) {
        this.chatClient = chatClient;
        this.gson = gson;
    }

    public Mono<String> fetchAiAnalysis(Spot spot) {
        if (spot.name().isEmpty() || spot.country().isEmpty() || spot.forecast().isEmpty()) {
            return Mono.empty();
        }
        return chatClient
                .prompt()
                .user(String.format(PROMPT, spot.name(), spot.country(), gson.toJson(spot.forecast())))
                .stream()
                .content()
                .delayElements(Duration.ofSeconds(1))
                .timeout(Duration.ofSeconds(15))
                .retry(3)
                .collectList()
                .map(list -> String.join("", list));
    }
}
