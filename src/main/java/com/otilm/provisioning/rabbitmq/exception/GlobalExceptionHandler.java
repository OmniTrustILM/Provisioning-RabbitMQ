package com.otilm.provisioning.rabbitmq.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String ERROR_FIELD = "error";

    @ExceptionHandler(QueueNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleQueueNotFound(QueueNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of(ERROR_FIELD, ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of(ERROR_FIELD, ex.getMessage()));
    }

    @ExceptionHandler(QueueAlreadyExistsException.class)
    public ResponseEntity<Map<String, String>> handleQueueAlreadyExists(QueueAlreadyExistsException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of(ERROR_FIELD, ex.getMessage()));
    }
}
