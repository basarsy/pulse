package com.pulse.connected;

import com.pulse.connected.messaging.KafkaTopics;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Messages a consumer cannot process must not be silently dropped: they are retried where it
 * makes sense and finally parked on the topic's dead-letter topic for inspection and replay.
 */
class KafkaErrorHandlingTest extends AbstractIntegrationTest {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private EmbeddedKafkaBroker broker;

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    void malformedCommandResultIsDeadLettered() throws Exception {
        assertDeadLettered(KafkaTopics.VEHICLE_COMMAND_RESULTS);
    }

    @Test
    void malformedOtaProgressIsDeadLettered() throws Exception {
        assertDeadLettered(KafkaTopics.OTA_UPDATE_STATUS);
    }

    private void assertDeadLettered(String topic) throws Exception {
        String key = UUID.randomUUID().toString();
        String poison = "{this is not json " + key;
        double deadLetteredBefore = deadLettered(topic);
        kafkaTemplate.send(topic, key, poison).get();

        Map<String, Object> props = KafkaTestUtils.consumerProps("dlt-test-" + key, "false", broker);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        try (Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<>(
                props, new StringDeserializer(), new StringDeserializer()).createConsumer()) {
            consumer.subscribe(List.of(KafkaTopics.deadLetter(topic)));

            List<ConsumerRecord<String, String>> received = new ArrayList<>();
            await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
                KafkaTestUtils.getRecords(consumer, Duration.ofMillis(500)).forEach(received::add);
                assertThat(received).anySatisfy(record -> {
                    assertThat(record.key()).isEqualTo(key);
                    assertThat(record.value()).isEqualTo(poison);
                    assertThat(record.headers().lastHeader(KafkaHeaders.DLT_EXCEPTION_FQCN)).isNotNull();
                });
            });
        }
        // Dead-lettering is visible in metrics (alerting / Grafana).
        assertThat(deadLettered(topic)).isEqualTo(deadLetteredBefore + 1);
    }

    private double deadLettered(String topic) {
        var counter = meterRegistry.find("pulse.kafka.dead_lettered").tag("topic", topic).counter();
        return counter == null ? 0 : counter.count();
    }
}
