package com.otilm.provisioning.rabbitmq.service;

import com.otilm.provisioning.rabbitmq.config.ProxyTokenConfigProperties;
import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.Template;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

/**
 * Generates JWT configuration tokens for proxy instances.
 * <p>
 * Renders {@code templates/rabbitmq.proxy_config.template} via Mustache with the supplied
 * {@link ProxyConfigData}, parses the resulting YAML into the {@code config} JWT claim,
 * and signs the token with HMAC-SHA256.
 * <p>
 * Configure via {@code app.token.signing-key} (min. 32 characters / 256 bits).
 */
@Service
public class ProxyConfigTokenGenerator {

    private final ProxyTokenConfigProperties proxyTokenConfigProperties;
    private final Template proxyConfigTemplate;

    public ProxyConfigTokenGenerator(
        ProxyTokenConfigProperties proxyTokenConfigProperties,
        Mustache.Compiler mustacheCompiler) throws IOException {

        this.proxyTokenConfigProperties = proxyTokenConfigProperties;

        try (var reader = new InputStreamReader(proxyTokenConfigProperties.configTemplate().getInputStream(), StandardCharsets.UTF_8)) {
            this.proxyConfigTemplate = mustacheCompiler.compile(reader);
        }
    }

    /**
     * Generates a non-expiring JWT config token.
     */
    public String generateToken(ProxyConfigData data) {
        return generateToken(data, Duration.ZERO);
    }

    /**
     * Generates a JWT config token. Pass {@link Duration#ZERO} or {@code null}
     * for a token without expiration.
     */
    // JJWT 0.13 accepts only java.util.Date for the expiration claim, so the java.time
    // instant computed here has to be converted at the library boundary.
    @SuppressWarnings("java:S2143")
    public String generateToken(ProxyConfigData data, Duration expiresIn) {
        String renderedYaml = proxyConfigTemplate.execute(data);

        Map<String, Object> configMap = new Yaml().load(renderedYaml);

        JwtBuilder builder = Jwts.builder()
            .subject(proxyTokenConfigProperties.subject())
            .claim("v", proxyTokenConfigProperties.version())
            .claim("config", configMap);

        if (proxyTokenConfigProperties.signingKey() != null) {
            builder.signWith(proxyTokenConfigProperties.signingKey());
        }

        if (expiresIn != null && !expiresIn.isZero()) {
            builder.expiration(Date.from(Instant.now().plus(expiresIn)));
        }

        return builder.compact();
    }
}
