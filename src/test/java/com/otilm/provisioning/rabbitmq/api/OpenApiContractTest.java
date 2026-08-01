package com.otilm.provisioning.rabbitmq.api;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiContractTest {

    @Test
    void queueProvisioningDocumentsAdditiveBindings() throws IOException {
        String specification = loadQueueProvisioningOperation().replaceAll("\\s+", " ");

        assertThat(specification)
                .contains("Using the same queue with a different exchange or routing key adds another binding")
                .contains("existing bindings are not removed");
    }

    @Test
    void queueProvisioningDocumentsBrokerFailure() throws IOException {
        String specification = loadQueueProvisioningOperation();

        assertThat(specification)
                .contains("'500':")
                .contains("RabbitMQ operation failed while declaring the queue or binding");
    }

    private static String loadQueueProvisioningOperation() throws IOException {
        String specification = loadSpecification();
        int operationStart = specification.indexOf("  /api/v1/queues:\n");
        int operationEnd = specification.indexOf("  /api/v1/queues/{name}:\n", operationStart);
        assertThat(operationStart).isNotNegative();
        assertThat(operationEnd).isGreaterThan(operationStart);
        return specification.substring(operationStart, operationEnd);
    }

    private static String loadSpecification() throws IOException {
        try (var input = OpenApiContractTest.class.getResourceAsStream("/proxy-provisioning-api.yaml")) {
            assertThat(input).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
