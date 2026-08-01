package com.otilm.provisioning.rabbitmq.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void queueNotFound_mapsToNotFound() {
        var response = handler.handleQueueNotFound(new QueueNotFoundException("MY_PROXY"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("error", "Queue not found for proxy code: MY_PROXY");
    }

    @Test
    void queueAlreadyExists_mapsToConflict() {
        var response = handler.handleQueueAlreadyExists(new QueueAlreadyExistsException("MY_PROXY"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody())
                .containsEntry("error", "Queue already exists with a conflicting definition: MY_PROXY");
    }

    @Test
    void illegalArgument_mapsToBadRequest() {
        var response = handler.handleIllegalArgument(new IllegalArgumentException("Format 'zip' is not supported"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("error", "Format 'zip' is not supported");
    }
}
