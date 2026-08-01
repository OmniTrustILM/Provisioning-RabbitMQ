package com.otilm.provisioning.rabbitmq.service;

/**
 * Data used to render the proxy configuration JWT token.
 * Maps to placeholders in {@code rabbitmq.proxy_config.template}.
 */
public record ProxyConfigData(
        String amqpUrl,
        String username,
        String password,
        String queueName,
        String exchange
) {}
