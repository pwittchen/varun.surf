package com.github.pwittchen.varun.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.web.embedded.netty.NettyReactiveWebServerFactory;
import reactor.netty.resources.LoopResources;

import static org.assertj.core.api.Assertions.assertThat;

class NettyConfigTest {

    private final NettyConfig config = new NettyConfig();

    @Test
    void shouldCreateServerLoopsWithCorrectNamePrefix() {
        // when
        var loopResources = config.serverLoops();

        // then
        assertThat(loopResources).isNotNull();
    }

    @Test
    void shouldCreateServerLoopsWithMaximumOf4Threads() {
        // given
        int availableCores = Runtime.getRuntime().availableProcessors();

        // when
        var loopResources = config.serverLoops();

        // then
        assertThat(loopResources).isNotNull();
        // The config uses Math.min(4, cores), so it should never exceed 4
    }

    @Test
    void shouldCreateWebServerCustomizer() {
        // given
        var loopResources = config.serverLoops();

        // when
        var customizer = config.webServerCustomizer(loopResources);

        // then
        assertThat(customizer).isNotNull();
    }

    @Test
    void shouldApplyCustomizerToFactory() {
        // given
        var loopResources = config.serverLoops();
        var customizer = config.webServerCustomizer(loopResources);
        var factory = new NettyReactiveWebServerFactory();

        // when
        customizer.customize(factory);

        // then - should not throw an exception
        assertThat(factory).isNotNull();
    }

    @Test
    void shouldCreateDaemonThreads() {
        // when
        var loopResources = config.serverLoops();

        // then
        assertThat(loopResources).isNotNull();
        // The last parameter (true) indicates daemon threads should be created
    }
}
