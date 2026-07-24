package com.czertainly.rabbitmq.bootstrap.service;

import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
class RabbitAdminSupport {

    private final RabbitAdmin rabbitAdmin;

    RabbitAdminSupport(RabbitAdmin rabbitAdmin) {
        this.rabbitAdmin = rabbitAdmin;
    }

    void declareQueue(String name, Map<String, Object> properties) {
        var builder = QueueBuilder.durable(name);
        if (properties != null && !properties.isEmpty()) {
            builder.withArguments(properties);
        }
        rabbitAdmin.declareQueue(builder.build());
    }

    void declareBinding(String queueName, String exchange, String routingKey) {
        rabbitAdmin.declareBinding(
                BindingBuilder.bind(new Queue(queueName))
                        .to(new TopicExchange(exchange))
                        .with(routingKey)
        );
    }

    void deleteQueue(String name) {
        rabbitAdmin.deleteQueue(name);
    }

    boolean queueExists(String name) {
        return rabbitAdmin.getQueueInfo(name) != null;
    }
}
