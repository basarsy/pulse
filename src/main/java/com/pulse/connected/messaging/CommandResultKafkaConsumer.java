package com.pulse.connected.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pulse.connected.command.CommandStatus;
import com.pulse.connected.command.CommandService;
import com.pulse.connected.messaging.dto.CommandResultEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class CommandResultKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(CommandResultKafkaConsumer.class);

    private final ObjectMapper objectMapper;
    private final CommandService commandService;

    public CommandResultKafkaConsumer(ObjectMapper objectMapper, CommandService commandService) {
        this.objectMapper = objectMapper;
        this.commandService = commandService;
    }

    @KafkaListener(topics = KafkaTopicConfig.TOPIC_VEHICLE_COMMAND_RESULTS, groupId = "backend-service-group")
    public void consumeCommandResult(String message) {
        try {
            CommandResultEvent event = objectMapper.readValue(message, CommandResultEvent.class);
            log.info("Received command result for command [{}] status [{}] vehicle [{}]",
                    event.getCommandId(), event.getStatus(), event.getVehicleId());

            commandService.processCommandResult(event.getCommandId(), event.getStatus(), event.getResultMessage());
        } catch (Exception e) {
            log.error("Failed to process command result message: {}", message, e);
        }
    }
}
