package com.otilm.provisioning.rabbitmq.controller;

import com.otilm.provisioning.rabbitmq.api.QueueProvisioningApi;
import com.otilm.provisioning.rabbitmq.model.QueueRequest;
import com.otilm.provisioning.rabbitmq.service.QueueProvisioningService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class QueueProvisioningController implements QueueProvisioningApi {

    private final QueueProvisioningService queueProvisioningService;

    public QueueProvisioningController(QueueProvisioningService queueProvisioningService) {
        this.queueProvisioningService = queueProvisioningService;
    }

    @Override
    public ResponseEntity<Void> provisionQueue(QueueRequest queueRequest) {
        queueProvisioningService.provisionQueue(queueRequest);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @Override
    public ResponseEntity<Void> deleteQueue(String name) {
        queueProvisioningService.deleteQueue(name);
        return ResponseEntity.noContent().build();
    }
}
