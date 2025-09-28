package com.github.pwittchen.varun.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Configuration
public class LoggingFilter {
    private static final Logger log = LoggerFactory.getLogger(LoggingFilter.class);

    @Bean
    public WebFilter indexAccessLogFilter() {
        return (ServerWebExchange exchange, WebFilterChain chain) -> {
            String path = exchange.getRequest().getURI().getPath();
            log.info("method={}, path={}, ua={}",
                    exchange.getRequest().getMethod(),
                    path,
                    shortenUserAgent(exchange.getRequest().getHeaders().getFirst("User-Agent")));
            return chain.filter(exchange);
        };
    }

    private String shortenUserAgent(String ua) {
        String shortUa = ua;
        if (ua != null) {
            Matcher m = Pattern.compile("(Chrome|Firefox|Safari|Edg|Opera)[/\\d\\.]+").matcher(ua);
            if (m.find()) {
                shortUa = m.group();
            }
        }
        return shortUa;
    }
}
