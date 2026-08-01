package com.otilm.provisioning.rabbitmq.service;

import com.otilm.provisioning.rabbitmq.exception.QueueAlreadyExistsException;
import com.otilm.provisioning.rabbitmq.model.QueueRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.AmqpIOException;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QueueProvisioningServiceImplTest {

    @Mock
    private RabbitAdminSupport rabbitAdminSupport;

    @InjectMocks
    private QueueProvisioningServiceImpl queueProvisioningService;

    @Test
    void provisionQueue_declaresDurableQueueWithArguments() {
        var request = new QueueRequest()
                .name("core-0")
                .exchange("ilm-proxy")
                .routingKey("proxymessage.*.core-0")
                .properties(Map.of("x-expires", 1800000));

        queueProvisioningService.provisionQueue(request);

        verify(rabbitAdminSupport).declareQueue("core-0", Map.of("x-expires", 1800000));
    }

    @Test
    void provisionQueue_declaresBindingWithRoutingKeyAndExchange() {
        var request = new QueueRequest()
                .name("core-0")
                .exchange("ilm-proxy")
                .routingKey("proxymessage.*.core-0");

        queueProvisioningService.provisionQueue(request);

        verify(rabbitAdminSupport).declareBinding("core-0", "ilm-proxy", "proxymessage.*.core-0");
    }

    @Test
    void provisionQueue_withNoPropertiesSet_passesEmptyMapToHelper() {
        var request = new QueueRequest()
                .name("core-0")
                .exchange("ilm-proxy")
                .routingKey("proxymessage.*.core-0");

        queueProvisioningService.provisionQueue(request);

        // The generated model defaults properties to an empty map.
        verify(rabbitAdminSupport).declareQueue("core-0", Map.of());
    }

    @Test
    void provisionQueue_throwsQueueAlreadyExistsException_onPreconditionFailed() {
        var request = new QueueRequest()
                .name("core-0")
                .exchange("ilm-proxy")
                .routingKey("proxymessage.*.core-0");
        doThrow(new AmqpIOException(new IOException("PRECONDITION_FAILED - inequivalent arg 'x-expires'")))
                .when(rabbitAdminSupport).declareQueue(any(), any());

        assertThatThrownBy(() -> queueProvisioningService.provisionQueue(request))
                .isInstanceOf(QueueAlreadyExistsException.class)
                .hasMessageContaining("core-0");
        verify(rabbitAdminSupport, never()).declareBinding(any(), any(), any());
    }

    @Test
    void provisionQueue_throwsQueueAlreadyExistsException_whenTopLevelMessageContainsPreconditionFailed() {
        var request = new QueueRequest()
                .name("core-0")
                .exchange("ilm-proxy")
                .routingKey("proxymessage.*.core-0");
        doThrow(new AmqpException("PRECONDITION_FAILED - inequivalent arg"))
                .when(rabbitAdminSupport).declareQueue(any(), any());

        assertThatThrownBy(() -> queueProvisioningService.provisionQueue(request))
                .isInstanceOf(QueueAlreadyExistsException.class);
    }

    @Test
    void provisionQueue_rethrowsOtherAmqpExceptions() {
        var request = new QueueRequest()
                .name("core-0")
                .exchange("ilm-proxy")
                .routingKey("proxymessage.*.core-0");
        doThrow(new AmqpIOException(new IOException("CONNECTION_REFUSED")))
                .when(rabbitAdminSupport).declareQueue(any(), any());

        assertThatThrownBy(() -> queueProvisioningService.provisionQueue(request))
                .isInstanceOf(AmqpIOException.class);
    }

    @Test
    void provisionQueue_addsContextToBindingFailureWithoutDeletingQueue() {
        var request = new QueueRequest()
                .name("core-0")
                .exchange("missing-exchange")
                .routingKey("proxymessage.*.core-0");
        var failure = new AmqpException("NOT_FOUND - no exchange 'missing-exchange'");
        doThrow(failure).when(rabbitAdminSupport)
                .declareBinding("core-0", "missing-exchange", "proxymessage.*.core-0");

        assertThatThrownBy(() -> queueProvisioningService.provisionQueue(request))
                .isInstanceOf(AmqpException.class)
                .hasMessage("Failed to bind queue 'core-0' to exchange 'missing-exchange' " +
                        "with routing key 'proxymessage.*.core-0'; queue may exist without the requested binding")
                .hasCause(failure);
        verify(rabbitAdminSupport, never()).deleteQueue(any());
    }

    @Test
    void deleteQueue_delegatesToRabbitAdminSupport() {
        queueProvisioningService.deleteQueue("core-0");
        verify(rabbitAdminSupport).deleteQueue("core-0");
    }

    @Test
    void deleteQueue_succeedsEvenWhenQueueNotFound() {
        assertThatNoException().isThrownBy(() -> queueProvisioningService.deleteQueue("nonexistent"));
    }
}
