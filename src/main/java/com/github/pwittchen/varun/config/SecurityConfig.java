package com.github.pwittchen.varun.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.security.web.server.savedrequest.NoOpServerRequestCache;
import org.springframework.security.web.server.util.matcher.PathPatternParserServerWebExchangeMatcher;
import reactor.core.publisher.Mono;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Value("${app.analytics.password:}")
    private String analyticsPassword;

    @Bean
    @Order(1)
    public SecurityWebFilterChain embedSecurityFilterChain(
            ServerHttpSecurity http,
            SessionTokenService sessionTokenService) {
        return http
                .securityMatcher(new PathPatternParserServerWebExchangeMatcher("/embed"))
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .requestCache(cache -> cache.requestCache(NoOpServerRequestCache.getInstance()))
                .headers(headers -> headers.frameOptions(ServerHttpSecurity.HeaderSpec.FrameOptionsSpec::disable))
                .authorizeExchange(exchanges -> exchanges.anyExchange().permitAll())
                .addFilterBefore(new SessionAuthenticationFilter(sessionTokenService), SecurityWebFiltersOrder.AUTHENTICATION)
                .build();
    }

    @Bean
    @Order(2)
    public SecurityWebFilterChain securityWebFilterChain(
            ServerHttpSecurity http,
            SessionTokenService sessionTokenService) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                // Nothing here keeps server-side state: the SESSION cookie is a signed
                // token and basic auth sends its credentials on every request. Left on
                // the default, Spring Security would load a WebSession per request,
                // read our token as an unknown session id and answer with a
                // cookie-clearing Set-Cookie that logs the visitor straight back out.
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                // Same reason: the default request cache stores the pre-login request
                // in a WebSession, which it touches on every single request. There is
                // no login redirect to come back from here, so it only ever cost us a
                // session lookup and the cleared cookie that follows a failed one.
                .requestCache(cache -> cache.requestCache(NoOpServerRequestCache.getInstance()))
                .authorizeExchange(exchanges -> {
                    if (analyticsPassword != null && !analyticsPassword.isBlank()) {
                        exchanges.pathMatchers("/api/v1/logs/**").authenticated();
                    } else {
                        // No password configured means there is no way to authenticate,
                        // so the logs stay shut. Falling open here would publish them to
                        // anyone holding a session cookie, which every visitor gets.
                        exchanges.pathMatchers("/api/v1/logs/**").denyAll();
                    }
                    exchanges.anyExchange().permitAll();
                })
                .httpBasic(httpBasic -> httpBasic.authenticationEntryPoint(noPopupAuthenticationEntryPoint()))
                .addFilterBefore(new SessionAuthenticationFilter(sessionTokenService), SecurityWebFiltersOrder.AUTHENTICATION)
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
