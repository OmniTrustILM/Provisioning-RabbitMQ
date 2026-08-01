package com.otilm.provisioning.rabbitmq.service;

import com.otilm.provisioning.rabbitmq.exception.QueueAlreadyExistsException;
import com.otilm.provisioning.rabbitmq.model.QueueRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.stereotype.Service;

@Service
public class QueueProvisioningServiceImpl implements QueueProvisioningService {

    private static final Logger log = LoggerFactory.getLogger(QueueProvisioningServiceImpl.class);

    private final RabbitAdminSupport rabbitAdminSupport;

    public QueueProvisioningServiceImpl(RabbitAdminSupport rabbitAdminSupport) {
        this.rabbitAdminSupport = rabbitAdminSupport;
    }

    @Override
    public void provisionQueue(QueueRequest request) {
        try {
            rabbitAdminSupport.declareQueue(request.getName(), request.getProperties());
        } catch (AmqpException e) {
            if (containsPreconditionFailed(e)) {
                log.warn("Queue '{}' already exists or has conflicting properties; preserving original AMQP exception in logs",
                        request.getName(), e);
                throw new QueueAlreadyExistsException(request.getName());
            }
            throw e;
        }

        try {
            rabbitAdminSupport.declareBinding(request.getName(), request.getExchange(), request.getRoutingKey());
        } catch (AmqpException e) {
            throw new AmqpException(
                    ("Failed to bind queue '%s' to exchange '%s' with routing key '%s'; " +
                            "queue may exist without the requested binding")
                            .formatted(request.getName(), request.getExchange(), request.getRoutingKey()),
                    e);
        }

        log.info("Provisioned queue '{}' with binding to exchange '{}' (routing key: '{}')",
                request.getName(), request.getExchange(), request.getRoutingKey());
    }

    @Override
    public void deleteQueue(String name) {
        rabbitAdminSupport.deleteQueue(name);
        log.info("Attempted to delete queue '{}'", name);
    }

    // Walk the full cause chain: Spring AMQP can wrap the RabbitMQ channel exception
    // (e.g. ShutdownSignalException) through multiple levels before reaching AmqpIOException.
    private static boolean containsPreconditionFailed(AmqpException e) {
        Throwable t = e;
        while (t != null) {
            if (messageContains(t.getMessage(), "PRECONDITION_FAILED")) {
                return true;
            }
            t = t.getCause();
        }
        return false;
    }

    private static boolean messageContains(String message, String substring) {
        return message != null && message.contains(substring);
    }
}
