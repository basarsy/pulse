package com.pulse.connected.ota;

import com.pulse.connected.messaging.KafkaTopics;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pulse.connected.messaging.dto.OtaUpdateProgressEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OtaProgressKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(OtaProgressKafkaConsumer.class);

    private final ObjectMapper objectMapper;
    private final RolloutCampaignService campaignService;

    public OtaProgressKafkaConsumer(ObjectMapper objectMapper, RolloutCampaignService campaignService) {
        this.objectMapper = objectMapper;
        this.campaignService = campaignService;
    }

    @KafkaListener(topics = KafkaTopics.OTA_UPDATE_STATUS, groupId = "backend-service-group")
    public void consumeOtaUpdateProgress(String message) throws JsonProcessingException {
        // Exceptions propagate to the shared error handler (retry, then dead-letter topic).
        OtaUpdateProgressEvent event = objectMapper.readValue(message, OtaUpdateProgressEvent.class);
        log.info("Received OTA update progress for update [{}] vehicle [{}] status [{}] progress [{}%]",
                event.getUpdateStatusId(), event.getVehicleId(), event.getStatus(), event.getProgressPercent());

        campaignService.handleVehicleUpdateProgress(event);
    }
}
