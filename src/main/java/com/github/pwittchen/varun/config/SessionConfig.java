package com.github.pwittchen.varun.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.server.session.CookieWebSessionIdResolver;
import org.springframework.web.server.session.WebSessionIdResolver;

import java.time.Duration;

@Configuration
public class SessionConfig {

    @Value("${app.session.max-age-seconds:86400}")
    private int maxAgeSeconds;

    @Bean
    public WebSessionIdResolver webSessionIdResolver() {
        CookieWebSessionIdResolver resolver = new CookieWebSessionIdResolver();
        resolver.setCookieName("SESSION");
        resolver.setCookieMaxAge(Duration.ofSeconds(maxAgeSeconds));
        resolver.addCookieInitializer(builder -> builder
                .httpOnly(true)
                .sameSite("Lax")
                .path("/"));
        return resolver;
    }
}
