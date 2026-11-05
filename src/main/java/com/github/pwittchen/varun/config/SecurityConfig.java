package com.github.pwittchen.varun.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.security.web.server.header.ReferrerPolicyServerHttpHeadersWriter.ReferrerPolicy;
import org.springframework.security.web.server.savedrequest.NoOpServerRequestCache;
import org.springframework.security.web.server.util.matcher.PathPatternParserServerWebExchangeMatcher;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    // The WebFlux default is no-referrer, and tile.openstreetmap.org answers a browser
    // tile request without a Referer with its "403 blocked" tile (its usage policy
    // requires one). This sends only the origin to other sites, never the path.
    private static final ReferrerPolicy TILE_FRIENDLY_REFERRER_POLICY =
            ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN;

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
                .headers(headers -> headers
                        .frameOptions(ServerHttpSecurity.HeaderSpec.FrameOptionsSpec::disable)
                        .referrerPolicy(referrer -> referrer.policy(TILE_FRIENDLY_REFERRER_POLICY)))
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
                // token, verified on every request. Left on
                // the default, Spring Security would load a WebSession per request,
                // read our token as an unknown session id and answer with a
                // cookie-clearing Set-Cookie that logs the visitor straight back out.
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                // Same reason: the default request cache stores the pre-login request
                // in a WebSession, which it touches on every single request. There is
                // no login here at all, so it only ever cost us a session lookup and
                // the cleared cookie that follows a failed one.
                .requestCache(cache -> cache.requestCache(NoOpServerRequestCache.getInstance()))
                .headers(headers -> headers.referrerPolicy(referrer -> referrer.policy(TILE_FRIENDLY_REFERRER_POLICY)))
                .authorizeExchange(exchanges -> exchanges.anyExchange().permitAll())
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .addFilterBefore(new SessionAuthenticationFilter(sessionTokenService), SecurityWebFiltersOrder.AUTHENTICATION)
                .build();
    }
}
