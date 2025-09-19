package com.github.pwittchen.varun.config;

import org.springframework.boot.web.embedded.netty.NettyReactiveWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.netty.http.server.HttpServer;
import reactor.netty.resources.LoopResources;

@Configuration
public class NettyConfig {

    @Bean
    LoopResources serverLoops() {
        final int cores = Runtime.getRuntime().availableProcessors();
        return LoopResources.create("srv", Math.min(4, cores), cores, true);
    }

    @Bean
    WebServerFactoryCustomizer<NettyReactiveWebServerFactory> webServerCustomizer(LoopResources serverLoops) {
        return factory -> factory.addServerCustomizers((HttpServer http) ->
                http.runOn(serverLoops, true)
        );
    }
}
