package com.pulse.connected.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pulse.connected.messaging.dto.OtaInstructionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class OtaKafkaProducer {

    private static final Logger log = LoggerFactory.getLogger(OtaKafkaProducer.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public OtaKafkaProducer(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public void dispatchOtaInstruction(OtaInstructionEvent event) {
        try {
            String jsonPayload = objectMapper.writeValueAsString(event);
            String partitionKey = event.getVehicleId().toString();

            kafkaTemplate.send(KafkaTopicConfig.TOPIC_OTA_INSTRUCTIONS, partitionKey, jsonPayload)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to send OTA instruction [{}] to vehicle [{}]: {}",
                                    event.getUpdateStatusId(), event.getVehicleId(), ex.getMessage(), ex);
                        } else {
                            log.info("Dispatched OTA instruction [{}] version [{}] to vehicle [{}] on partition [{}]",
                                    event.getUpdateStatusId(), event.getTargetVersionLabel(), event.getVehicleId(),
                                    result.getRecordMetadata().partition());
                        }
                    });
        } catch (JsonProcessingException e) {
            log.error("Error serializing OtaInstructionEvent [{}]: {}", event.getUpdateStatusId(), e.getMessage(), e);
        }
    }
}
