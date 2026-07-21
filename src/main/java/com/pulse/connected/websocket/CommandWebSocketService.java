package com.pulse.connected.websocket;

import com.pulse.connected.command.dto.CommandResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class CommandWebSocketService {

    private static final Logger log = LoggerFactory.getLogger(CommandWebSocketService.class);

    private final SimpMessagingTemplate messagingTemplate;

    public CommandWebSocketService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void notifyCommandStatusChange(UUID vehicleId, CommandResponse commandResponse) {
        String destination = "/topic/vehicles/" + vehicleId + "/commands";
        log.info("Pushing WebSocket update for command [{}] status [{}] to destination [{}]",
                commandResponse.getId(), commandResponse.getStatus(), destination);
        messagingTemplate.convertAndSend(destination, commandResponse);
    }
}
