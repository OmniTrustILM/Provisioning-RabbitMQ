package com.czertainly.rabbitmq.bootstrap.exception;

public class QueueAlreadyExistsException extends RuntimeException {

    public QueueAlreadyExistsException(String name) {
        super("Queue already exists with a conflicting definition: " + name);
    }
}
