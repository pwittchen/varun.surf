package com.github.pwittchen.varun.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
    private OpenAiChatModel openAiModel;

    private final LLMConfig config = new LLMConfig();

    @Test
    void shouldUseOpenAiWhenAvailable() {
        // given
        when(openAiProvider.getIfAvailable()).thenReturn(openAiModel);

        // when
        var chatClient = config.routingChatClient(openAiProvider);

        // then
        assertThat(chatClient).isNotNull();
    }

    @Test
    void shouldThrowExceptionWhenNoModelIsAvailable() {
        // given
        when(openAiProvider.getIfAvailable()).thenReturn(null);

        // when/then
        assertThatThrownBy(() -> config.routingChatClient(openAiProvider))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No AI model available");
    }
}
