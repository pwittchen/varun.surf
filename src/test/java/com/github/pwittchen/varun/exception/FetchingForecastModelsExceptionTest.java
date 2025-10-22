package com.github.pwittchen.varun.exception;

import org.junit.jupiter.api.Test;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FetchingForecastModelsExceptionTest {

    @Test
    void shouldCreateExceptionWithMessage() {
        String message = "Error fetching forecast models";
        FetchingForecastModelsException exception = new FetchingForecastModelsException(message);

        assertThat(exception.getMessage()).isEqualTo(message);
    }

    @Test
    void shouldBeRuntimeException() {
        FetchingForecastModelsException exception = new FetchingForecastModelsException("test");
        assertThat(exception).isInstanceOf(RuntimeException.class);
    }

    @Test
    void shouldBeThrowable() {
        assertThrows(FetchingForecastModelsException.class, () -> {
            throw new FetchingForecastModelsException("test error");
        });
    }
}