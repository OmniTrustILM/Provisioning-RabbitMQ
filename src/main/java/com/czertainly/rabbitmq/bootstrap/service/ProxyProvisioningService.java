package com.czertainly.rabbitmq.bootstrap.service;

import com.czertainly.rabbitmq.bootstrap.model.InstallationInstructions;

public interface ProxyProvisioningService {

    void provisionQueue(String proxyCode);

    void decommissionQueue(String proxyCode);

    InstallationInstructions getInstallationInstructions(String proxyCode, String format);
}
