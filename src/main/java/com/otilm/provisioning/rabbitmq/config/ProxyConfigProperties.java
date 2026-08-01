package com.otilm.provisioning.rabbitmq.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

/**
 * Configuration properties for proxy connectivity, bound from {@code app.proxy.*}.
 */
@ConfigurationProperties(prefix = "app.proxy")
public record ProxyConfigProperties(
        String amqpUrl,
        String username,
        String password,
        String exchange,
        String responseQueue,
        String requestRoutingKeyPrefix,
        String responseRoutingKeyPrefix,
        Resource helmInstallTemplate

) {}
