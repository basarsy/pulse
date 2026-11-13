package com.pulse.connected.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * Error handling for all {@code @KafkaListener}s (Spring Boot applies this bean to the default
 * listener container factory). Listeners throw instead of swallowing exceptions:
 * <ul>
 *   <li>transient failures are retried with exponential backoff;</li>
 *   <li>malformed payloads are not retried;</li>
 *   <li>after the last attempt the record is published to {@code <topic>.DLT} with the exception
 *       in its headers, so nothing is silently lost and messages can be inspected or replayed.</li>
 * </ul>
 */
@Configuration
public class KafkaErrorHandlingConfig {

    @Bean
    public CommonErrorHandler kafkaErrorHandler(
            KafkaTemplate<String, String> kafkaTemplate,
            MeterRegistry meterRegistry,
            @Value("${pulse.kafka.retry.initial-interval-ms:500}") long initialIntervalMs,
            @Value("${pulse.kafka.retry.max-elapsed-ms:10000}") long maxElapsedMs) {

        DeadLetterPublishingRecoverer deadLetterPublisher = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, exception) -> new TopicPartition(KafkaTopics.deadLetter(record.topic()), record.partition()));

        ConsumerRecordRecoverer recoverer = (record, exception) -> {
            deadLetterPublisher.accept(record, exception);
            meterRegistry.counter("pulse.kafka.dead_lettered", "topic", record.topic()).increment();
        };

        ExponentialBackOff backOff = new ExponentialBackOff(initialIntervalMs, 2.0);
        backOff.setMaxElapsedTime(maxElapsedMs);

        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(JsonProcessingException.class);
        return handler;
    }
}
