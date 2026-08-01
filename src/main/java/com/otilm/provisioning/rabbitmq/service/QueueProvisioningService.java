package com.otilm.provisioning.rabbitmq.service;

import com.otilm.provisioning.rabbitmq.model.QueueRequest;

public interface QueueProvisioningService {

    void provisionQueue(QueueRequest request);

    void deleteQueue(String name);
}
