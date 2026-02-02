package com.github.pwittchen.varun.service.logs;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.google.common.truth.Truth.assertThat;

class LogsServiceTest {

    private LogsService logsService;

    @BeforeEach
    void setUp() {
        logsService = new LogsService();
    }

    @Test
    void shouldReturnEmptyListInitially() {
        List<LogEntry> logs = logsService.getLogs();

        assertThat(logs).isEmpty();
    }

    @Test
    void shouldAddAndRetrieveLogs() {
        LogEntry entry = new LogEntry(
                System.currentTimeMillis(),
                "INFO",
                "com.example.Test",
                "Test message",
                "main"
        );

        logsService.addLog(entry);
        List<LogEntry> logs = logsService.getLogs();

        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).level()).isEqualTo("INFO");
        assertThat(logs.get(0).message()).isEqualTo("Test message");
    }

    @Test
    void shouldIgnoreNullEntries() {
        logsService.addLog(null);
        List<LogEntry> logs = logsService.getLogs();

        assertThat(logs).isEmpty();
    }

    @Test
    void shouldFilterLogsByLevel() {
        logsService.addLog(createLog("INFO", "Info message"));
        logsService.addLog(createLog("ERROR", "Error message"));
        logsService.addLog(createLog("WARN", "Warn message"));
        logsService.addLog(createLog("INFO", "Another info"));

        List<LogEntry> infoLogs = logsService.getLogs("INFO");
        List<LogEntry> errorLogs = logsService.getLogs("ERROR");

        assertThat(infoLogs).hasSize(2);
        assertThat(errorLogs).hasSize(1);
    }

    @Test
    void shouldReturnAllLogsWhenLevelIsNull() {
        logsService.addLog(createLog("INFO", "Info message"));
        logsService.addLog(createLog("ERROR", "Error message"));

        List<LogEntry> logs = logsService.getLogs(null);

        assertThat(logs).hasSize(2);
    }

    @Test
    void shouldReturnAllLogsWhenLevelIsEmpty() {
        logsService.addLog(createLog("INFO", "Info message"));
        logsService.addLog(createLog("ERROR", "Error message"));

        List<LogEntry> logs = logsService.getLogs("");

        assertThat(logs).hasSize(2);
    }

    @Test
    void shouldFilterCaseInsensitively() {
        logsService.addLog(createLog("INFO", "Info message"));

        List<LogEntry> logs = logsService.getLogs("info");

        assertThat(logs).hasSize(1);
    }

    @Test
    void shouldLimitBufferTo1000Entries() {
        for (int i = 0; i < 1100; i++) {
            logsService.addLog(createLog("INFO", "Message " + i));
        }

        List<LogEntry> logs = logsService.getLogs();

        assertThat(logs).hasSize(1000);
    }

    @Test
    void shouldEvictOldestEntriesWhenBufferFull() {
        for (int i = 0; i < 1100; i++) {
            logsService.addLog(createLog("INFO", "Message " + i));
        }

        List<LogEntry> logs = logsService.getLogs();

        assertThat(logs.get(0).message()).isEqualTo("Message 100");
        assertThat(logs.get(999).message()).isEqualTo("Message 1099");
    }

    @Test
    void shouldReportCorrectBufferSize() {
        logsService.addLog(createLog("INFO", "Message 1"));
        logsService.addLog(createLog("INFO", "Message 2"));
        logsService.addLog(createLog("INFO", "Message 3"));

        assertThat(logsService.getBufferSize()).isEqualTo(3);
    }

    @Test
    void shouldClearBuffer() {
        logsService.addLog(createLog("INFO", "Message 1"));
        logsService.addLog(createLog("INFO", "Message 2"));

        logsService.clear();

        assertThat(logsService.getLogs()).isEmpty();
        assertThat(logsService.getBufferSize()).isEqualTo(0);
    }

    @Test
    void shouldBeThreadSafe() throws InterruptedException {
        int threadCount = 10;
        int logsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < logsPerThread; i++) {
                        logsService.addLog(createLog("INFO", "Thread " + threadId + " Message " + i));
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(logsService.getBufferSize()).isEqualTo(threadCount * logsPerThread);
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
