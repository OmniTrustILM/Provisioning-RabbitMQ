package com.otilm.provisioning.rabbitmq.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProxyTokenConfigPropertiesTest {

    private static final String VALID_KEY = "test_signing_key_test_signing_key";
    private static final Resource TEMPLATE =
            new ClassPathResource("templates/rabbitmq.proxy_config.template");

    private static ProxyTokenConfigProperties properties(String signingKey) {
        return new ProxyTokenConfigProperties(signingKey, "proxy-config", 1, TEMPLATE);
    }

    @Test
    void signingKeyOfAtLeast32Characters_isAccepted() {
        var properties = properties(VALID_KEY);

        assertThat(properties.signingKey()).isNotNull();
        assertThat(properties.signingKey().getAlgorithm()).isEqualTo("HmacSHA256");
        assertThat(properties.subject()).isEqualTo("proxy-config");
        assertThat(properties.version()).isEqualTo(1);
        assertThat(properties.configTemplate()).isEqualTo(TEMPLATE);
    }

    @Test
    void shorterSigningKey_isRejected() {
        assertThatThrownBy(() -> properties("too_short"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 32 characters");
    }

    @Test
    void nullSigningKey_leavesTokensUnsigned() {
        assertThat(properties(null).signingKey()).isNull();
    }

    @Test
    void blankSigningKey_leavesTokensUnsigned() {
        assertThat(properties("   ").signingKey()).isNull();
    }
}
