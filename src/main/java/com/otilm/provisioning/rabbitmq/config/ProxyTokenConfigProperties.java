package com.otilm.provisioning.rabbitmq.config;

import io.jsonwebtoken.security.Keys;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.core.io.Resource;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

@ConfigurationProperties(prefix = "app.proxy.token-config")
public record ProxyTokenConfigProperties(
        SecretKey signingKey,
        String subject,
        int version,
        Resource configTemplate
) {
    @ConstructorBinding
    public ProxyTokenConfigProperties(String signingKey, String subject, int version, Resource configTemplate) {
        this(toSecretKey(signingKey), subject, version, configTemplate);
    }

    private static SecretKey toSecretKey(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) return null;
        byte[] keyBytes = rawKey.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalArgumentException(
                "app.proxy.token-config.signing-key must be at least 32 characters (256 bits) for HMAC-SHA256");
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
