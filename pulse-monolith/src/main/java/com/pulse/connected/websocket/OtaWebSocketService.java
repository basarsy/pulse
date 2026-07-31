package com.pulse.connected.websocket;

import com.pulse.connected.ota.dto.CampaignResponse;
import com.pulse.connected.ota.dto.VehicleUpdateStatusResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class OtaWebSocketService {

    private static final Logger log = LoggerFactory.getLogger(OtaWebSocketService.class);

    private final SimpMessagingTemplate messagingTemplate;

    public OtaWebSocketService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void notifyCampaignProgress(UUID campaignId, CampaignResponse response) {
        String destination = "/topic/rollouts/" + campaignId;
        log.info("Pushing WebSocket update for campaign [{}] status [{}] to [{}]",
                campaignId, response.getStatus(), destination);
        messagingTemplate.convertAndSend(destination, response);
    }

    public void notifyVehicleUpdateStatus(UUID vehicleId, VehicleUpdateStatusResponse response) {
        String destination = "/topic/vehicles/" + vehicleId + "/update-status";
        log.info("Pushing WebSocket update for vehicle [{}] OTA status [{}] to [{}]",
                vehicleId, response.getStatus(), destination);
        messagingTemplate.convertAndSend(destination, response);
    }
}
