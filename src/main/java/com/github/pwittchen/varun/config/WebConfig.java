package com.github.pwittchen.varun.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.config.WebFluxConfigurer;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RequestPredicates.HEAD;

@Configuration
public class WebConfig implements WebFluxConfigurer {

    static final URI ANDROID_APP_URL =
            URI.create("https://play.google.com/store/apps/details?id=surf.varun.app");

    // NOTE: GET /spot/{id} is handled by SeoController, which injects per-spot
    // SEO metadata into the spot.html template before serving it.

    @Bean
    public RouterFunction<ServerResponse> tvRouter() {
        return RouterFunctions.route(
                GET("/spot/{id}/tv"),
                _ -> {
                    Resource tvHtml = new ClassPathResource("static/tv.html");
                    return ServerResponse
                            .ok()
                            .contentType(MediaType.TEXT_HTML)
                            .bodyValue(tvHtml);
                }
        );
    }

    // NOTE: GET /country/{countryName} is handled by SeoController, which injects
    // per-country SEO metadata into the index.html template before serving it.

    @Bean
    public RouterFunction<ServerResponse> starredRouter() {
        return RouterFunctions.route(
                GET("/starred"),
                _ -> {
                    Resource indexHtml = new ClassPathResource("static/index.html");
                    return ServerResponse
                            .ok()
                            .contentType(MediaType.TEXT_HTML)
                            .bodyValue(indexHtml);
                }
        );
    }

    @Bean
    public RouterFunction<ServerResponse> embedRouter() {
        return RouterFunctions.route(
                GET("/embed"),
                _ -> {
                    Resource embedHtml = new ClassPathResource("static/embed.html");
                    return ServerResponse
                            .ok()
                            .contentType(MediaType.TEXT_HTML)
                            .bodyValue(embedHtml);
                }
        );
    }

    @Bean
    public RouterFunction<ServerResponse> mapRouter() {
        return RouterFunctions.route(
                GET("/map"),
                _ -> {
                    Resource indexHtml = new ClassPathResource("static/index.html");
                    return ServerResponse
                            .ok()
                            .contentType(MediaType.TEXT_HTML)
                            .bodyValue(indexHtml);
                }
        );
    }

    @Bean
    public RouterFunction<ServerResponse> globeRouter() {
        return RouterFunctions.route(
                GET("/globe"),
                _ -> {
                    Resource indexHtml = new ClassPathResource("static/index.html");
                    return ServerResponse
                            .ok()
                            .contentType(MediaType.TEXT_HTML)
                            .bodyValue(indexHtml);
                }
        );
    }

    @Bean
    public RouterFunction<ServerResponse> statusRouter() {
        return RouterFunctions.route(
                GET("/status"),
                _ -> {
                    Resource statusHtml = new ClassPathResource("static/status.html");
                    return ServerResponse
                            .ok()
                            .contentType(MediaType.TEXT_HTML)
                            .bodyValue(statusHtml);
                }
        );
    }

    @Bean
    public RouterFunction<ServerResponse> sourcesRouter() {
        return RouterFunctions.route(
                GET("/sources"),
                _ -> {
                    Resource sourcesHtml = new ClassPathResource("static/sources.html");
                    return ServerResponse
                            .ok()
                            .contentType(MediaType.TEXT_HTML)
                            .bodyValue(sourcesHtml);
                }
        );
    }

    // NOTE: only the exact /mcp path serves this page; the MCP server itself is
    // exposed under /mcp/sse and /mcp/message by Spring AI.
    @Bean
    public RouterFunction<ServerResponse> mcpPageRouter() {
        return RouterFunctions.route(
                GET("/mcp"),
                _ -> {
                    Resource mcpHtml = new ClassPathResource("static/mcp.html");
                    return ServerResponse
                            .ok()
                            .contentType(MediaType.TEXT_HTML)
                            .bodyValue(mcpHtml);
                }
        );
    }

    // NOTE: only the exact /cli path serves this page; the installer it points to
    // lives at /cli/install.sh (see cliInstallScriptRouter).
    @Bean
    public RouterFunction<ServerResponse> cliPageRouter() {
        return RouterFunctions.route(
                GET("/cli"),
                _ -> {
                    Resource cliHtml = new ClassPathResource("static/cli.html");
                    return ServerResponse
                            .ok()
                            .contentType(MediaType.TEXT_HTML)
                            .bodyValue(cliHtml);
                }
        );
    }

    // Linked from the Play Store listing and the Android app, so it has to stay
    // public and at this exact path
    @Bean
    public RouterFunction<ServerResponse> privacyRouter() {
        return RouterFunctions.route(
                GET("/privacy"),
                _ -> {
                    Resource privacyHtml = new ClassPathResource("static/privacy.html");
                    return ServerResponse
                            .ok()
                            .contentType(MediaType.TEXT_HTML)
                            .bodyValue(privacyHtml);
                }
        );
    }

    // Short link to the Android app, temporary so it can be repointed without browsers
    // holding on to the old target. HEAD as well, for link checkers and previews
    @Bean
    public RouterFunction<ServerResponse> androidRouter() {
        return RouterFunctions.route(
                GET("/android").or(HEAD("/android")),
                _ -> ServerResponse.temporaryRedirect(ANDROID_APP_URL).build()
        );
    }

    // The CLI installer (curl -fsSL https://varun.surf/cli/install.sh | sh). Served as
    // plain text rather than the application/x-sh a static resource would get, so
    // opening the URL in a browser shows the script for review instead of downloading it
    @Bean
    public RouterFunction<ServerResponse> cliInstallScriptRouter() {
        return RouterFunctions.route(
                GET("/cli/install.sh"),
                _ -> {
                    Resource installScript = new ClassPathResource("static/cli/install.sh");
                    return ServerResponse
                            .ok()
                            .contentType(new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8))
                            .bodyValue(installScript);
                }
        );
    }

    @Bean
    public RouterFunction<ServerResponse> metricsRouter() {
        return RouterFunctions.route(
                GET("/metrics"),
                _ -> {
                    Resource metricsHtml = new ClassPathResource("static/metrics.html");
                    return ServerResponse
                            .ok()
                            .contentType(MediaType.TEXT_HTML)
                            .bodyValue(metricsHtml);
                }
        ).andRoute(
                GET("/metrics/"),
                _ -> {
                    Resource metricsHtml = new ClassPathResource("static/metrics.html");
                    return ServerResponse
                            .ok()
                            .contentType(MediaType.TEXT_HTML)
                            .bodyValue(metricsHtml);
                }
        );
    }

    @Bean
    public RouterFunction<ServerResponse> logsRouter() {
        return RouterFunctions.route(
                GET("/logs"),
                _ -> {
                    Resource logsHtml = new ClassPathResource("static/logs.html");
                    return ServerResponse
                            .ok()
                            .contentType(MediaType.TEXT_HTML)
                            .bodyValue(logsHtml);
                }
        ).andRoute(
                GET("/logs/"),
                _ -> {
                    Resource logsHtml = new ClassPathResource("static/logs.html");
                    return ServerResponse
                            .ok()
                            .contentType(MediaType.TEXT_HTML)
                            .bodyValue(logsHtml);
                }
        );
    }
}
