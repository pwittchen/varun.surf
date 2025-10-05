package com.github.pwittchen.varun.service;

import com.github.pwittchen.varun.model.Spot;
import com.google.gson.Gson;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Service
public class AiService {
    private final static String SYSTEM_PROMPT = """
            You are a kitesurfing weather assistant. \s
            Analyze the forecast and output a **3-line summary**, each starting with a keyword. \s
            Keep your answer short (under 40 words total), objective, and formatted exactly like this:
            
            Wind: <speed + direction + gusts> \s
            Conditions: <temperature, water/wave state, safety> \s
            Recommendation: <good / moderate / bad for kitesurfing + short reason>
            
            Do NOT write explanations or extra text. \s
            Do NOT include greetings or paragraphs. \s
            Spot name: %s, country: %s
            Forecast data in JSON format:
            %s
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
                .user(String.format(SYSTEM_PROMPT, spot.name(), spot.country(), gson.toJson(spot.forecast())))
                .stream()
                .content()
                .delayElements(Duration.ofSeconds(1))
                .timeout(Duration.ofSeconds(15))
                .retry(3)
                .collectList()
                .map(list -> String.join("", list));
    }
}
