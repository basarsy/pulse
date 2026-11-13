package com.pulse.connected.messaging;

/**
 * Thrown when a message could not be published to Kafka. Propagating it keeps the corresponding
 * outbox publication incomplete, so it is resubmitted later.
 */
public class KafkaPublishException extends RuntimeException {
    public KafkaPublishException(String message, Throwable cause) {
        super(message, cause);
    }
}
