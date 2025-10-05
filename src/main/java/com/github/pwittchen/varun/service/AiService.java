package com.github.pwittchen.varun.service;

import com.github.pwittchen.varun.model.Spot;
import com.google.gson.Gson;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class AiService {
    private final static String SYSTEM_PROMPT = "You are Meteorologist. " +
            "Your task is to comment the weather forecast for kitesurfers and give advice " +
            "for kitesurfing on the kite spot %s located in %s. " +
            "The weather forecast is provided in the JSON format as follows:\n" +
            "%s";

    private final ChatClient chatClient;
    private final Gson gson;

    public AiService(ChatClient chatClient, Gson gson) {
        this.chatClient = chatClient;
        this.gson = gson;
    }

    public Mono<String> generateAiForecastComment(Spot spot) {
        if (spot.name().isEmpty() || spot.country().isEmpty() || spot.forecast().isEmpty()) {
            return Mono.empty();
        }
        return chatClient
                .prompt()
                .user(String.format(SYSTEM_PROMPT, spot.name(), spot.country(), gson.toJson(spot.forecast())))
                .stream()
                .content()
                .collectList()
                .map(list -> String.join("", list));
    }
}
