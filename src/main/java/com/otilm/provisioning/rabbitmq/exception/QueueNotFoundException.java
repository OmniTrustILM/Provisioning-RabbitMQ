package com.otilm.provisioning.rabbitmq.exception;

public class QueueNotFoundException extends RuntimeException {

    public QueueNotFoundException(String proxyCode) {
        super("Queue not found for proxy code: " + proxyCode);
    }
}
