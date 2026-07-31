package com.pulse.connected.messaging;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic otaInstructionsTopic() {
        return TopicBuilder.name(KafkaTopics.OTA_INSTRUCTIONS)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic otaUpdateStatusTopic() {
        return TopicBuilder.name(KafkaTopics.OTA_UPDATE_STATUS)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
