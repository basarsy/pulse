package com.pulse.connected.websocket;

import com.pulse.connected.messaging.dto.OtaUpdateProgressEvent;
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

    public void notifyCampaignProgress(UUID campaignId, OtaUpdateProgressEvent event) {
        String destination = "/topic/rollouts/" + campaignId;
        log.debug("Pushing campaign update via WebSocket to [{}]", destination);
        messagingTemplate.convertAndSend(destination, event);
    }

    public void notifyVehicleUpdateProgress(UUID vehicleId, OtaUpdateProgressEvent event) {
        String destination = "/topic/vehicles/" + vehicleId + "/update-status";
        log.debug("Pushing vehicle update progress via WebSocket to [{}]", destination);
        messagingTemplate.convertAndSend(destination, event);
    }
}
