package com.pulse.connected.websocket;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class WebSocketNotificationService {

    private final SimpMessagingTemplate messagingTemplate;

    public WebSocketNotificationService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void notifyVehicleUpdate(String vehicleId, Object update) {
        messagingTemplate.convertAndSend("/topic/vehicle/" + vehicleId, update);
    }
}
