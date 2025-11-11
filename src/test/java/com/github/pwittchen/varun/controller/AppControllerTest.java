package com.github.pwittchen.varun.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Map;

import static com.google.common.truth.Truth.assertThat;

@ExtendWith(MockitoExtension.class)
public class AppControllerTest {

    private AppController controller;

    @BeforeEach
    void setUp() {
        controller = new AppController();
    }

    @Test
    void shouldReturnHealthStatusUp() {
        Mono<Map<String, String>> result = controller.health();

        StepVerifier.create(result)
                .assertNext(health -> {
                    assertThat(health).isNotNull();
                    assertThat(health).containsEntry("status", "UP");
                })
                .verifyComplete();
    }
}
