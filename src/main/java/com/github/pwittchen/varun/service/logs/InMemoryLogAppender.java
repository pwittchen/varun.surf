package com.github.pwittchen.varun.service.logs;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import org.springframework.context.ApplicationContext;

import java.util.Set;

public class InMemoryLogAppender extends AppenderBase<ILoggingEvent> {

    private static ApplicationContext applicationContext;
    private static LogsService logsService;

    private static final Set<String> EXCLUDED_LOGGERS = Set.of(
            "io.netty",
            "reactor.netty",
            "org.springframework.boot.devtools"
    );

    public static void setApplicationContext(ApplicationContext context) {
        applicationContext = context;
        logsService = null;
    }

    @Override
    protected void append(ILoggingEvent event) {
        if (logsService == null && applicationContext != null) {
            try {
                logsService = applicationContext.getBean(LogsService.class);
            } catch (Exception e) {
                return;
            }
        }

        if (logsService == null) {
            return;
        }

        String loggerName = event.getLoggerName();
        if (isExcludedLogger(loggerName)) {
            return;
        }

        LogEntry entry = new LogEntry(
                event.getTimeStamp(),
                event.getLevel().toString(),
                loggerName,
                event.getFormattedMessage(),
                event.getThreadName()
        );

        logsService.addLog(entry);
    }

    private boolean isExcludedLogger(String loggerName) {
        if (loggerName == null) {
            return false;
        }
        for (String excluded : EXCLUDED_LOGGERS) {
            if (loggerName.startsWith(excluded)) {
                return true;
            }
        }
        return false;
    }
}
