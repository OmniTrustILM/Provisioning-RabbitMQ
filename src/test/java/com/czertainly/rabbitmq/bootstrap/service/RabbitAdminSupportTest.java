package com.czertainly.rabbitmq.bootstrap.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.rabbit.core.RabbitAdmin;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RabbitAdminSupportTest {

    @Mock
    private RabbitAdmin rabbitAdmin;

    @InjectMocks
    private RabbitAdminSupport rabbitAdminSupport;

    @Test
    void declareQueue_withProperties_passesArgumentsToQueueBuilder() {
        rabbitAdminSupport.declareQueue("core-0", Map.of("x-expires", 1800000));

        var captor = ArgumentCaptor.forClass(Queue.class);
        verify(rabbitAdmin).declareQueue(captor.capture());
        Queue q = captor.getValue();
        assertThat(q.getName()).isEqualTo("core-0");
        assertThat(q.isDurable()).isTrue();
        assertThat(q.getArguments()).containsEntry("x-expires", 1800000);
    }

    @Test
    void declareQueue_withNullProperties_declaresQueueWithoutArguments() {
        rabbitAdminSupport.declareQueue("core-0", null);

        var captor = ArgumentCaptor.forClass(Queue.class);
        verify(rabbitAdmin).declareQueue(captor.capture());
        assertThat(captor.getValue().getArguments()).isNullOrEmpty();
    }

    @Test
    void declareQueue_withEmptyProperties_declaresQueueWithoutArguments() {
        rabbitAdminSupport.declareQueue("core-0", Map.of());

        var captor = ArgumentCaptor.forClass(Queue.class);
        verify(rabbitAdmin).declareQueue(captor.capture());
        assertThat(captor.getValue().getArguments()).isNullOrEmpty();
    }

    @Test
    void declareBinding_passesCorrectDestinationExchangeAndRoutingKey() {
        rabbitAdminSupport.declareBinding("core-0", "czertainly-proxy", "proxymessage.*.core-0");

        var captor = ArgumentCaptor.forClass(Binding.class);
        verify(rabbitAdmin).declareBinding(captor.capture());
        Binding b = captor.getValue();
        assertThat(b.getDestination()).isEqualTo("core-0");
        assertThat(b.getExchange()).isEqualTo("czertainly-proxy");
        assertThat(b.getRoutingKey()).isEqualTo("proxymessage.*.core-0");
        assertThat(b.getDestinationType()).isEqualTo(Binding.DestinationType.QUEUE);
    }

    @Test
    void deleteQueue_delegatesToRabbitAdmin() {
        rabbitAdminSupport.deleteQueue("core-0");
        verify(rabbitAdmin).deleteQueue("core-0");
    }

    @Test
    void queueExists_returnsTrueWhenQueueFound() {
        when(rabbitAdmin.getQueueInfo("core-0"))
                .thenReturn(new QueueInformation("core-0", 0, 0));
        assertThat(rabbitAdminSupport.queueExists("core-0")).isTrue();
    }

    @Test
    void queueExists_returnsFalseWhenQueueNotFound() {
        when(rabbitAdmin.getQueueInfo("nonexistent")).thenReturn(null);
        assertThat(rabbitAdminSupport.queueExists("nonexistent")).isFalse();
    }
}
