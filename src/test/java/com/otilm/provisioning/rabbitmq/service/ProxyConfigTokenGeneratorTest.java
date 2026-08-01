package com.otilm.provisioning.rabbitmq.service;

import com.otilm.provisioning.rabbitmq.config.ProxyTokenConfigProperties;
import com.samskivert.mustache.Mustache;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProxyConfigTokenGeneratorTest {

    private static final String TEMPLATE_CONTENT = """
            amqp:
              broker_type: "rabbitmq"
              url: "{{amqpUrl}}"
              auth_method: "sasl-plain"
              username: "{{username}}"
              password: "{{password}}"
              subscription: "{{queueName}}"
              topic_name: "{{exchange}}"
            """;

    private static final SecretKey SIGNING_KEY =
            Keys.hmacShaKeyFor("12345678901234567890123456789012".getBytes(StandardCharsets.UTF_8));

    private static final ProxyConfigData SAMPLE_DATA = new ProxyConfigData(
            "amqp://localhost:5672", "user", "pass", "my-queue", "my-exchange");

    @Mock
    private ProxyTokenConfigProperties proxyTokenConfigProperties;

    private ProxyConfigTokenGenerator buildGenerator(SecretKey key) throws Exception {
        when(proxyTokenConfigProperties.signingKey()).thenReturn(key);
        when(proxyTokenConfigProperties.subject()).thenReturn("proxy-config");
        when(proxyTokenConfigProperties.version()).thenReturn(1);
        when(proxyTokenConfigProperties.configTemplate()).thenReturn(
                new ByteArrayResource(TEMPLATE_CONTENT.getBytes(StandardCharsets.UTF_8)));
        return new ProxyConfigTokenGenerator(proxyTokenConfigProperties, Mustache.compiler());
    }

    @Test
    void generateToken_unsigned_containsExpectedClaims() throws Exception {
        var gen = buildGenerator(null);

        String token = gen.generateToken(SAMPLE_DATA);

        var claims = Jwts.parser().unsecured().build().parseUnsecuredClaims(token).getPayload();
        assertThat(claims.getSubject()).isEqualTo("proxy-config");
        assertThat(claims.get("v", Integer.class)).isEqualTo(1);

        @SuppressWarnings("unchecked")
        var amqp = (Map<String, Object>) ((Map<String, Object>) claims.get("config")).get("amqp");
        assertThat(amqp)
                .containsEntry("url", "amqp://localhost:5672")
                .containsEntry("subscription", "my-queue")
                .containsEntry("topic_name", "my-exchange");
    }

    @Test
    void generateToken_signed_verifiableWithKey() throws Exception {
        var gen = buildGenerator(SIGNING_KEY);

        String token = gen.generateToken(SAMPLE_DATA);

        var claims = Jwts.parser().verifyWith(SIGNING_KEY).build().parseSignedClaims(token).getPayload();
        assertThat(claims.getSubject()).isEqualTo("proxy-config");
        assertThat(claims.get("v", Integer.class)).isEqualTo(1);
    }

    @Test
    void generateToken_signed_throwsOnWrongKey() throws Exception {
        var gen = buildGenerator(SIGNING_KEY);

        String token = gen.generateToken(SAMPLE_DATA);

        SecretKey wrongKey = Keys.hmacShaKeyFor("wrong_key_wrong_key_wrong_key_ww".getBytes(StandardCharsets.UTF_8));
        var parser = Jwts.parser().verifyWith(wrongKey).build();

        assertThatThrownBy(() -> parser.parseSignedClaims(token))
                .isInstanceOf(SignatureException.class);
    }

    @Test
    void generateToken_withExpiration_setsExpClaim() throws Exception {
        var gen = buildGenerator(null);

        String token = gen.generateToken(SAMPLE_DATA, Duration.ofHours(1));

        var claims = Jwts.parser().unsecured().build().parseUnsecuredClaims(token).getPayload();
        assertThat(claims.getExpiration())
                .isNotNull()
                .isAfter(Date.from(Instant.now()));
    }

    @Test
    void generateToken_withZeroDuration_noExpClaim() throws Exception {
        var gen = buildGenerator(null);

        String token = gen.generateToken(SAMPLE_DATA, Duration.ZERO);

        var claims = Jwts.parser().unsecured().build().parseUnsecuredClaims(token).getPayload();
        assertThat(claims.getExpiration()).isNull();
    }

    @Test
    void generateToken_withNullDuration_noExpClaim() throws Exception {
        var gen = buildGenerator(null);

        String token = gen.generateToken(SAMPLE_DATA, null);

        var claims = Jwts.parser().unsecured().build().parseUnsecuredClaims(token).getPayload();
        assertThat(claims.getExpiration()).isNull();
    }
}
