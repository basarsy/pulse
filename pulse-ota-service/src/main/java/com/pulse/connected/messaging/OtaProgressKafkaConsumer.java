package com.pulse.connected.messaging;

import com.pulse.connected.messaging.dto.OtaUpdateProgressEvent;
import com.pulse.connected.ota.RolloutCampaignService;
import com.pulse.connected.websocket.OtaWebSocketService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class OtaProgressKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(OtaProgressKafkaConsumer.class);

    private final RolloutCampaignService campaignService;
    private final OtaWebSocketService webSocketService;

    public OtaProgressKafkaConsumer(RolloutCampaignService campaignService, OtaWebSocketService webSocketService) {
        this.campaignService = campaignService;
        this.webSocketService = webSocketService;
    }

    @KafkaListener(topics = KafkaTopics.OTA_UPDATE_STATUS, groupId = "ota-service-group")
    public void consumeUpdateProgress(OtaUpdateProgressEvent event) {
        log.info("Received OTA update progress for vehicle [{}]: status [{}], progress [{}%]",
                event.getVehicleId(), event.getStatus(), event.getProgressPercent());

        campaignService.handleVehicleUpdateProgress(event);

        if (event.getCampaignId() != null) {
            webSocketService.notifyCampaignProgress(event.getCampaignId(), event);
        }
        if (event.getVehicleId() != null) {
            webSocketService.notifyVehicleUpdateProgress(event.getVehicleId(), event);
        }
    }
}
