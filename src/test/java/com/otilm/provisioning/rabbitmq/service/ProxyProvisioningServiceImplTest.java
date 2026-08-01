package com.otilm.provisioning.rabbitmq.service;

import com.otilm.provisioning.rabbitmq.config.ProxyConfigProperties;
import com.otilm.provisioning.rabbitmq.exception.QueueNotFoundException;
import com.samskivert.mustache.Mustache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProxyProvisioningServiceImplTest {

    private static final String EXCHANGE = "ilm-proxy";

    @Mock
    private RabbitAdminSupport rabbitAdminSupport;

    @Mock
    private ProxyConfigTokenGenerator tokenGenerator;

    private ProxyProvisioningServiceImpl service;

    @BeforeEach
    void setUp() throws IOException {
        var template = new ByteArrayResource(
                "helm install proxy --set token=\"{{token}}\"".getBytes(StandardCharsets.UTF_8));
        var proxyConfig = new ProxyConfigProperties(
                "amqp://rabbitmq:5672",
                "proxy",
                "secret",
                EXCHANGE,
                "core",
                "coremessage.",
                "proxymessage.",
                template);

        service = new ProxyProvisioningServiceImpl(
                rabbitAdminSupport, tokenGenerator, Mustache.compiler(), proxyConfig);
    }

    @Test
    void provisionQueueDeclaresQueueAndBothBindings() {
        service.provisionQueue("proxy-1");

        verify(rabbitAdminSupport).declareQueue("proxy-1", null);
        verify(rabbitAdminSupport).declareBinding("proxy-1", EXCHANGE, "coremessage.proxy-1");
        verify(rabbitAdminSupport).declareBinding("core", EXCHANGE, "proxymessage.proxy-1");
    }

    @Test
    void decommissionQueueDeletesProxyQueue() {
        service.decommissionQueue("proxy-1");

        verify(rabbitAdminSupport).deleteQueue("proxy-1");
    }

    @Test
    void getInstallationInstructionsRejectsUnsupportedFormat() {
        assertThatThrownBy(() -> service.getInstallationInstructions("proxy-1", "docker-compose"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("docker-compose");
        verifyNoInteractions(rabbitAdminSupport, tokenGenerator);
    }

    @Test
    void getInstallationInstructionsRejectsMissingQueue() {
        when(rabbitAdminSupport.queueExists("proxy-1")).thenReturn(false);

        assertThatThrownBy(() -> service.getInstallationInstructions("proxy-1", "helm"))
                .isInstanceOf(QueueNotFoundException.class)
                .hasMessageContaining("proxy-1");
        verifyNoInteractions(tokenGenerator);
    }

    @Test
    void getInstallationInstructionsRendersGeneratedToken() {
        var expectedConfig = new ProxyConfigData(
                "amqp://rabbitmq:5672", "proxy", "secret", "proxy-1", EXCHANGE);
        when(rabbitAdminSupport.queueExists("proxy-1")).thenReturn(true);
        when(tokenGenerator.generateToken(expectedConfig)).thenReturn("generated-token");

        var result = service.getInstallationInstructions("proxy-1", "helm");

        assertThat(result.getCommand().getShell())
                .isEqualTo("helm install proxy --set token=\"generated-token\"");
    }
}
