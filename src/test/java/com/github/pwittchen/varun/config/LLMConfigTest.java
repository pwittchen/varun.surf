package com.github.pwittchen.varun.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LLMConfigTest {

    @Mock
    private ObjectProvider<OpenAiChatModel> openAiProvider;

    @Mock
    private ObjectProvider<OllamaChatModel> ollamaProvider;

    @Mock
    private OpenAiChatModel openAiModel;

    @Mock
    private OllamaChatModel ollamaModel;

    private final LLMConfig config = new LLMConfig();

    @Test
    void shouldUseOpenAiWhenProviderIsOpenAiAndAvailable() {
        // given
        when(openAiProvider.getIfAvailable()).thenReturn(openAiModel);
        when(openAiProvider.getObject()).thenReturn(openAiModel);

        // when
        var chatClient = config.routingChatClient(openAiProvider, ollamaProvider, "openai");

        // then
        assertThat(chatClient).isNotNull();
    }

    @Test
    void shouldUseOpenAiWhenProviderIsOpenAiWithMixedCase() {
        // given
        when(openAiProvider.getIfAvailable()).thenReturn(openAiModel);
        when(openAiProvider.getObject()).thenReturn(openAiModel);

        // when
        var chatClient = config.routingChatClient(openAiProvider, ollamaProvider, "OpenAI");

        // then
        assertThat(chatClient).isNotNull();
    }

    @Test
    void shouldUseOllamaWhenProviderIsOllama() {
        // given
        when(ollamaProvider.getIfAvailable()).thenReturn(ollamaModel);

        // when
        var chatClient = config.routingChatClient(openAiProvider, ollamaProvider, "ollama");

        // then
        assertThat(chatClient).isNotNull();
    }

    @Test
    void shouldUseOllamaWhenProviderIsUnknown() {
        // given
        when(ollamaProvider.getIfAvailable()).thenReturn(ollamaModel);

        // when
        var chatClient = config.routingChatClient(openAiProvider, ollamaProvider, "unknown");

        // then
        assertThat(chatClient).isNotNull();
    }

    @Test
    void shouldUseOllamaWhenOpenAiProviderIsOpenAiButNotAvailable() {
        // given
        when(openAiProvider.getIfAvailable()).thenReturn(null);
        when(ollamaProvider.getIfAvailable()).thenReturn(ollamaModel);

        // when
        var chatClient = config.routingChatClient(openAiProvider, ollamaProvider, "openai");

        // then
        assertThat(chatClient).isNotNull();
    }

    @Test
    void shouldThrowExceptionWhenNoModelIsAvailable() {
        // given
        when(ollamaProvider.getIfAvailable()).thenReturn(null);

        // when/then
        assertThatThrownBy(() -> config.routingChatClient(openAiProvider, ollamaProvider, "ollama"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No AI model available");
    }

    @Test
    void shouldThrowExceptionWhenOpenAiRequestedButNothingAvailable() {
        // given
        when(openAiProvider.getIfAvailable()).thenReturn(null);
        when(ollamaProvider.getIfAvailable()).thenReturn(null);

        // when/then
        assertThatThrownBy(() -> config.routingChatClient(openAiProvider, ollamaProvider, "openai"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No AI model available");
    }

    @Test
    void shouldDefaultToOllamaWhenProviderParameterIsEmpty() {
        // given
        when(ollamaProvider.getIfAvailable()).thenReturn(ollamaModel);

        // when
        var chatClient = config.routingChatClient(openAiProvider, ollamaProvider, "");

        // then
        assertThat(chatClient).isNotNull();
    }
}