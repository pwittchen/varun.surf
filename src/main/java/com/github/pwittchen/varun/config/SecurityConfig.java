package com.github.pwittchen.varun.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.util.matcher.PathPatternParserServerWebExchangeMatcher;
import reactor.core.publisher.Mono;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Value("${app.analytics.password:}")
    private String analyticsPassword;

    @Bean
    @Order(1)
    public SecurityWebFilterChain embedSecurityFilterChain(ServerHttpSecurity http) {
        return http
                .securityMatcher(new PathPatternParserServerWebExchangeMatcher("/embed"))
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .headers(headers -> headers.frameOptions(ServerHttpSecurity.HeaderSpec.FrameOptionsSpec::disable))
                .authorizeExchange(exchanges -> exchanges.anyExchange().permitAll())
                .build();
    }

    @Bean
    @Order(2)
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchanges -> {
                    if (analyticsPassword != null && !analyticsPassword.isBlank()) {
                        exchanges
                                .pathMatchers("/api/v1/metrics/**", "/api/v1/logs/**").authenticated()
                                .anyExchange().permitAll();
                    } else {
                        exchanges.anyExchange().permitAll();
                    }
                })
                .httpBasic(httpBasic -> httpBasic.authenticationEntryPoint(noPopupAuthenticationEntryPoint()))
                .build();
    }

    private ServerAuthenticationEntryPoint noPopupAuthenticationEntryPoint() {
        return (exchange, _) -> {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return Mono.empty();
        };
    }

    @Bean
    public ReactiveUserDetailsService userDetailsService(PasswordEncoder passwordEncoder) {
        if (analyticsPassword == null || analyticsPassword.isBlank()) {
            return _ -> Mono.empty();
        }

        var user = User
                .builder()
                .username("admin")
                .password(passwordEncoder.encode(analyticsPassword))
                .roles("ADMIN")
                .build();

        return new MapReactiveUserDetailsService(user);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}