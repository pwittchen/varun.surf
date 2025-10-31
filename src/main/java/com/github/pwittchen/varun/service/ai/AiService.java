package com.github.pwittchen.varun.service.ai;

import com.github.pwittchen.varun.model.spot.Spot;
import com.google.gson.Gson;
import org.springframework.ai.chat.client.ChatClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

public abstract class AiService {
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
                gson.toJson(spot.forecast())
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

    public abstract String createPromptTemplate();

    public abstract String createPromptPartForAdditionalContext();
}
