package com.pulse.connected.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pulse.connected.messaging.dto.OtaUpdateProgressEvent;
import com.pulse.connected.ota.RolloutCampaignService;
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

    @KafkaListener(topics = KafkaTopicConfig.TOPIC_OTA_UPDATE_STATUS, groupId = "backend-service-group")
    public void consumeOtaUpdateProgress(String message) {
        try {
            OtaUpdateProgressEvent event = objectMapper.readValue(message, OtaUpdateProgressEvent.class);
            log.info("Received OTA update progress for update [{}] vehicle [{}] status [{}] progress [{}%]",
                    event.getUpdateStatusId(), event.getVehicleId(), event.getStatus(), event.getProgressPercent());

            campaignService.handleVehicleUpdateProgress(event);
        } catch (Exception e) {
            log.error("Failed to process OTA update progress message: {}", message, e);
        }
    }
}
