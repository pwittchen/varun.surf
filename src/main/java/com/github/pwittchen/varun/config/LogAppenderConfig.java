package com.github.pwittchen.varun.config;

import com.github.pwittchen.varun.service.logs.InMemoryLogAppender;
import jakarta.annotation.PostConstruct;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LogAppenderConfig {

    private final ApplicationContext applicationContext;

    public LogAppenderConfig(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @PostConstruct
    public void init() {
        InMemoryLogAppender.setApplicationContext(applicationContext);
    }
}
