package com.github.pwittchen.varun.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class LLMConfig {

    @Bean
    @Primary
    public ChatClient routingChatClient(
            ObjectProvider<OpenAiChatModel> openAi
    ) {
        var model = openAi.getIfAvailable();

        if (model == null) {
            throw new IllegalStateException("No AI model available. Check build config and properties.");
        }

        return ChatClient
                .builder(model)
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .build();
    }
}
