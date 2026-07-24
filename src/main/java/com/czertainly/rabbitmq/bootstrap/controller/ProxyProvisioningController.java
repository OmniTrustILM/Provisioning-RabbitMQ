package com.czertainly.rabbitmq.bootstrap.controller;

import com.czertainly.rabbitmq.bootstrap.api.ProxyProvisioningApi;
import com.czertainly.rabbitmq.bootstrap.model.InstallationInstructions;
import com.czertainly.rabbitmq.bootstrap.model.ProxyProvisioningRequest;
import com.czertainly.rabbitmq.bootstrap.service.ProxyProvisioningService;
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
