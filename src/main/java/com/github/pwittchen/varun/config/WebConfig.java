package com.github.pwittchen.varun.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.config.ResourceHandlerRegistry;
import org.springframework.web.reactive.config.ViewResolverRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;
import org.springframework.web.reactive.result.view.ViewResolver;
import org.springframework.web.reactive.result.view.UrlBasedViewResolver;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.context.annotation.Bean;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;

@Configuration
public class WebConfig implements WebFluxConfigurer {

    @Bean
    public RouterFunction<ServerResponse> spotRouter() {
        return RouterFunctions.route(
            GET("/spot/{id}"),
            request -> {
                Resource spotHtml = new ClassPathResource("static/spot.html");
                return ServerResponse.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .bodyValue(spotHtml);
            }
        );
    }
}