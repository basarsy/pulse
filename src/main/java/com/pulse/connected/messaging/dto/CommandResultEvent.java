package com.pulse.connected.messaging.dto;

import com.pulse.connected.command.CommandStatus;

import java.time.Instant;
import java.util.UUID;

public class CommandResultEvent {

    private UUID commandId;
    private UUID vehicleId;
    private CommandStatus status;
    private String resultMessage;
    private Instant timestamp;

    public CommandResultEvent() {}

    public CommandResultEvent(UUID commandId, UUID vehicleId, CommandStatus status, String resultMessage, Instant timestamp) {
        this.commandId = commandId;
        this.vehicleId = vehicleId;
        this.status = status;
        this.resultMessage = resultMessage;
        this.timestamp = timestamp;
    }

    public static CommandResultEventBuilder builder() {
        return new CommandResultEventBuilder();
    }

    public UUID getCommandId() { return commandId; }
    public void setCommandId(UUID commandId) { this.commandId = commandId; }

    public UUID getVehicleId() { return vehicleId; }
    public void setVehicleId(UUID vehicleId) { this.vehicleId = vehicleId; }

    public CommandStatus getStatus() { return status; }
    public void setStatus(CommandStatus status) { this.status = status; }

    public String getResultMessage() { return resultMessage; }
    public void setResultMessage(String resultMessage) { this.resultMessage = resultMessage; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

    public static class CommandResultEventBuilder {
        private UUID commandId;
        private UUID vehicleId;
        private CommandStatus status;
        private String resultMessage;
        private Instant timestamp;

        CommandResultEventBuilder() {}

        public CommandResultEventBuilder commandId(UUID commandId) { this.commandId = commandId; return this; }
        public CommandResultEventBuilder vehicleId(UUID vehicleId) { this.vehicleId = vehicleId; return this; }
        public CommandResultEventBuilder status(CommandStatus status) { this.status = status; return this; }
        public CommandResultEventBuilder resultMessage(String resultMessage) { this.resultMessage = resultMessage; return this; }
        public CommandResultEventBuilder timestamp(Instant timestamp) { this.timestamp = timestamp; return this; }

        public CommandResultEvent build() {
            return new CommandResultEvent(commandId, vehicleId, status, resultMessage, timestamp);
        }
    }
}
