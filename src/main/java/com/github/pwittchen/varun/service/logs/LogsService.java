package com.github.pwittchen.varun.service.logs;

import com.google.common.collect.EvictingQueue;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class LogsService {

    private static final int MAX_LOG_ENTRIES = 1000;

    private final EvictingQueue<LogEntry> logBuffer;

    public LogsService() {
this.logBuffer = EvictingQueue.create(MAX_LOG_ENTRIES);
    }

    public void addLog(LogEntry entry) {
        if (entry == null) {
            return;
        }
        synchronized (logBuffer) {
            logBuffer.add(entry);
        }
    }

    public List<LogEntry> getLogs() {
        synchronized (logBuffer) {
            return new ArrayList<>(logBuffer);
        }
    }

    public List<LogEntry> getLogs(String level) {
        if (level == null || level.isBlank()) {
            return getLogs();
        }
        String normalizedLevel = level.toUpperCase();
        synchronized (logBuffer) {
            return logBuffer.stream()
                    .filter(entry -> normalizedLevel.equals(entry.level()))
                    .toList();
        }
    }

    public int getBufferSize() {
        synchronized (logBuffer) {
            return logBuffer.size();
        }
    }

    public void clear() {
        synchronized (logBuffer) {
            logBuffer.clear();
        }
    }
}
