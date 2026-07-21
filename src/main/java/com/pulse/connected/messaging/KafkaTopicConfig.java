package com.pulse.connected.messaging;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    public static final String TOPIC_VEHICLE_COMMANDS = "vehicle.commands";
    public static final String TOPIC_VEHICLE_COMMAND_RESULTS = "vehicle.command-results";
    public static final String TOPIC_VEHICLE_HEARTBEAT = "vehicle.heartbeat";

    @Bean
    public NewTopic vehicleCommandsTopic() {
        return TopicBuilder.name(TOPIC_VEHICLE_COMMANDS)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic vehicleCommandResultsTopic() {
        return TopicBuilder.name(TOPIC_VEHICLE_COMMAND_RESULTS)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic vehicleHeartbeatTopic() {
        return TopicBuilder.name(TOPIC_VEHICLE_HEARTBEAT)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
