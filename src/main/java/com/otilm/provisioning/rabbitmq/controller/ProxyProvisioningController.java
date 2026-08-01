package com.otilm.provisioning.rabbitmq.controller;

import com.otilm.provisioning.rabbitmq.api.ProxyProvisioningApi;
import com.otilm.provisioning.rabbitmq.model.InstallationInstructions;
import com.otilm.provisioning.rabbitmq.model.ProxyProvisioningRequest;
import com.otilm.provisioning.rabbitmq.service.ProxyProvisioningService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProxyProvisioningController implements ProxyProvisioningApi {

    private final ProxyProvisioningService provisioningService;

    public ProxyProvisioningController(ProxyProvisioningService provisioningService) {
        this.provisioningService = provisioningService;
    }

    @Override
    public ResponseEntity<Void> provisionProxy(ProxyProvisioningRequest request) {
        provisioningService.provisionQueue(request.getProxyCode());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @Override
    public ResponseEntity<Void> decommissionProxy(String proxyCode) {
        provisioningService.decommissionQueue(proxyCode);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<InstallationInstructions> getInstallationInstructions(String proxyCode, String format) {
        return ResponseEntity.ok(provisioningService.getInstallationInstructions(proxyCode, format));
    }
}
