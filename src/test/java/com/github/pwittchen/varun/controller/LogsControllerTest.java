package com.github.pwittchen.varun.controller;

import com.github.pwittchen.varun.service.logs.LogEntry;
import com.github.pwittchen.varun.service.logs.LogsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LogsControllerTest {

    private LogsController controller;

    @Mock
    private LogsService logsService;

    @BeforeEach
    void setUp() {
        controller = new LogsController(logsService);
    }

    @Test
    void shouldReturnAllLogs() {
        List<LogEntry> logs = List.of(
                createLog("INFO", "Message 1"),
                createLog("ERROR", "Message 2")
        );
        when(logsService.getLogs()).thenReturn(logs);

        Mono<List<LogEntry>> result = controller.getLogs(null);

        StepVerifier.create(result)
                .assertNext(entries -> {
                    assertThat(entries).hasSize(2);
                    assertThat(entries.get(0).level()).isEqualTo("INFO");
                    assertThat(entries.get(1).level()).isEqualTo("ERROR");
                })
                .verifyComplete();
    }

    @Test
    void shouldReturnEmptyListWhenNoLogs() {
        when(logsService.getLogs()).thenReturn(List.of());

        Mono<List<LogEntry>> result = controller.getLogs(null);

        StepVerifier.create(result)
                .assertNext(entries -> assertThat(entries).isEmpty())
                .verifyComplete();
    }

    @Test
    void shouldFilterLogsByLevel() {
        List<LogEntry> errorLogs = List.of(createLog("ERROR", "Error message"));
        when(logsService.getLogs("ERROR")).thenReturn(errorLogs);

        Mono<List<LogEntry>> result = controller.getLogs("ERROR");

        StepVerifier.create(result)
                .assertNext(entries -> {
                    assertThat(entries).hasSize(1);
                    assertThat(entries.get(0).level()).isEqualTo("ERROR");
                })
                .verifyComplete();
    }

    @Test
    void shouldReturnAllLogsWhenLevelIsEmpty() {
        List<LogEntry> allLogs = List.of(
                createLog("INFO", "Info"),
                createLog("WARN", "Warn")
        );
        when(logsService.getLogs()).thenReturn(allLogs);

        Mono<List<LogEntry>> result = controller.getLogs("");

        StepVerifier.create(result)
                .assertNext(entries -> assertThat(entries).hasSize(2))
                .verifyComplete();
    }

    @Test
    void shouldReturnAllLogsWhenLevelIsBlank() {
        List<LogEntry> allLogs = List.of(
                createLog("INFO", "Info"),
                createLog("WARN", "Warn")
        );
        when(logsService.getLogs()).thenReturn(allLogs);

        Mono<List<LogEntry>> result = controller.getLogs("   ");

        StepVerifier.create(result)
                .assertNext(entries -> assertThat(entries).hasSize(2))
                .verifyComplete();
    }

    private LogEntry createLog(String level, String message) {
        return new LogEntry(
                System.currentTimeMillis(),
                level,
                "com.example.Test",
                message,
                "test-thread"
        );
    }
}
