package com.github.pwittchen.varun.service.ai;

import com.github.pwittchen.varun.model.forecast.Forecast;
import com.github.pwittchen.varun.model.spot.Spot;
import org.springframework.ai.chat.client.ChatClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public abstract class AiService {
    private final ChatClient chatClient;

    public AiService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public Mono<String> fetchAiAnalysis(Spot spot) {
        if (spot.name().isEmpty() || spot.country().isEmpty() || spot.forecast().isEmpty()) {
            return Mono.empty();
        }

        String prompt = buildPrompt(spot);

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

    protected String buildPrompt(Spot spot) {
        return String.format(
                createPromptTemplate(),
                buildCustomContext(spot),
                spot.name(),
                spot.country(),
                transformToToon(spot.forecast())
        );
    }

    protected String buildCustomContext(Spot spot) {
        if (spot.spotInfo() != null &&
                spot.spotInfo().llmComment() != null &&
                !spot.spotInfo().llmComment().isEmpty()) {
            return String.format(createPromptPartForAdditionalContext(), spot.spotInfo().llmComment());
        }
        return "";
    }

    /**
     * Transforms forecast data to TOON (Token-Optimized Object Notation) format.
     * This compact format reduces token usage for LLM API calls.
     * Format: time|wind|gust|dir|temp|precip
     * Example: Mon 12:00|10.0|15.0|N|12.5|0.0
     */
    protected String transformToToon(List<Forecast> forecasts) {
        return forecasts.stream()
                .map(f -> String.format(Locale.US, "%s|%.1f|%.1f|%s|%.1f|%.1f",
                        f.date(),
                        f.wind(),
                        f.gusts(),
                        f.direction(),
                        f.temp(),
                        f.precipitation()))
                .collect(Collectors.joining("\n"));
    }

    public abstract String createPromptTemplate();

    public abstract String createPromptPartForAdditionalContext();
}
