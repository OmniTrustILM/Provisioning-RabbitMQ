package com.otilm.provisioning.rabbitmq.service;

import com.otilm.provisioning.rabbitmq.model.InstallationInstructions;

public interface ProxyProvisioningService {

    void provisionQueue(String proxyCode);

    void decommissionQueue(String proxyCode);

    InstallationInstructions getInstallationInstructions(String proxyCode, String format);
}
