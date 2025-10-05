package com.github.pwittchen.varun.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class AiService {

    ChatClient chatClient;

    public AiService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public Mono<String> ai() {
        return chatClient
                .prompt()
                .user("tell me a joke")
                .stream()
                .content()
                .collectList()
                .map(list -> String.join("", list));
    }
}
