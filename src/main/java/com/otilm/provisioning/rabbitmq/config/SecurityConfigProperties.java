package com.otilm.provisioning.rabbitmq.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app.security")
public record SecurityConfigProperties(
        boolean apiKeyEnabled,
        String apiKey,
        String apiKeyHeader,
        List<String> publicPaths
) {}