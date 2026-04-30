package com.github.pwittchen.varun.config;

import com.github.pwittchen.varun.service.mcp.McpToolService;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class McpConfig {

    @Bean
    public ToolCallbackProvider varunMcpTools(McpToolService mcpToolService) {
        return MethodToolCallbackProvider
                .builder()
                .toolObjects(mcpToolService)
                .build();
    }
}
