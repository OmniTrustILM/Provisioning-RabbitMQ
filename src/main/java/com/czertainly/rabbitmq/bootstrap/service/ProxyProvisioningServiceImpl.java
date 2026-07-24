package com.czertainly.rabbitmq.bootstrap.service;

import com.czertainly.rabbitmq.bootstrap.config.ProxyConfigProperties;
import com.czertainly.rabbitmq.bootstrap.exception.QueueNotFoundException;
import com.czertainly.rabbitmq.bootstrap.model.Command;
import com.czertainly.rabbitmq.bootstrap.model.InstallationInstructions;
import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.Template;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

@Service
public class ProxyProvisioningServiceImpl implements ProxyProvisioningService {

    private static final Logger log = LoggerFactory.getLogger(ProxyProvisioningServiceImpl.class);

    private final RabbitAdminSupport rabbitAdminSupport;
    private final ProxyConfigTokenGenerator tokenGenerator;
    private final ProxyConfigProperties proxyConfig;
    private final Template helmInstallTemplate;

    public ProxyProvisioningServiceImpl(
            RabbitAdminSupport rabbitAdminSupport,
            ProxyConfigTokenGenerator tokenGenerator,
            Mustache.Compiler mustacheCompiler,
            ProxyConfigProperties proxyConfig) throws IOException {
        this.rabbitAdminSupport = rabbitAdminSupport;
        this.tokenGenerator = tokenGenerator;
        this.proxyConfig = proxyConfig;

        try (var reader = new InputStreamReader(
                proxyConfig.helmInstallTemplate().getInputStream(), StandardCharsets.UTF_8)) {
            this.helmInstallTemplate = mustacheCompiler.compile(reader);
        }
    }

    @Override
    public void provisionQueue(String proxyCode) {
        rabbitAdminSupport.declareQueue(proxyCode, null);

        var requestRoutingKey = proxyConfig.requestRoutingKeyPrefix() + proxyCode;
        rabbitAdminSupport.declareBinding(proxyCode, proxyConfig.exchange(), requestRoutingKey);
        log.info("Provisioned queue '{}' with binding to '{}' (routing key: '{}')",
                proxyCode, proxyConfig.exchange(), requestRoutingKey);

        var responseRoutingKey = proxyConfig.responseRoutingKeyPrefix() + proxyCode;
        rabbitAdminSupport.declareBinding(proxyConfig.responseQueue(), proxyConfig.exchange(), responseRoutingKey);
        log.info("Provisioned response binding for queue '{}' to exchange '{}' with routing key '{}'",
                proxyConfig.responseQueue(), proxyConfig.exchange(), responseRoutingKey);
    }

    @Override
    public void decommissionQueue(String proxyCode) {
        rabbitAdminSupport.deleteQueue(proxyCode);
        log.info("Decommissioned queue '{}'", proxyCode);
    }

    @Override
    public InstallationInstructions getInstallationInstructions(String proxyCode, String format) {
        if (!"helm".equals(format)) {
            throw new IllegalArgumentException("Format '%s' is not supported".formatted(format));
        }

        if (!rabbitAdminSupport.queueExists(proxyCode)) {
            throw new QueueNotFoundException(proxyCode);
        }

        var configData = new ProxyConfigData(
                proxyConfig.amqpUrl(),
                proxyConfig.username(),
                proxyConfig.password(),
                proxyCode,
                proxyConfig.exchange());

        String token = tokenGenerator.generateToken(configData);
        String command = helmInstallTemplate.execute(new HelmInstallData(token));
        return new InstallationInstructions().command(new Command().shell(command));
    }
}
