package com.czertainly.rabbitmq.bootstrap.service;

import com.czertainly.rabbitmq.bootstrap.model.QueueRequest;

public interface QueueProvisioningService {

    void provisionQueue(QueueRequest request);

    void deleteQueue(String name);
}
