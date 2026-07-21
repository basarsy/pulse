package com.pulse.connected.messaging.dto;

import com.pulse.connected.command.CommandType;

import java.time.Instant;
import java.util.UUID;

public class VehicleCommandEvent {

    private UUID commandId;
    private UUID vehicleId;
    private CommandType type;
    private Instant issuedAt;
    private String idempotencyKey;

    public VehicleCommandEvent() {}

    public VehicleCommandEvent(UUID commandId, UUID vehicleId, CommandType type, Instant issuedAt, String idempotencyKey) {
        this.commandId = commandId;
        this.vehicleId = vehicleId;
        this.type = type;
        this.issuedAt = issuedAt;
        this.idempotencyKey = idempotencyKey;
    }

    public static VehicleCommandEventBuilder builder() {
        return new VehicleCommandEventBuilder();
    }

    public UUID getCommandId() { return commandId; }
    public void setCommandId(UUID commandId) { this.commandId = commandId; }

    public UUID getVehicleId() { return vehicleId; }
    public void setVehicleId(UUID vehicleId) { this.vehicleId = vehicleId; }

    public CommandType getType() { return type; }
    public void setType(CommandType type) { this.type = type; }

    public Instant getIssuedAt() { return issuedAt; }
    public void setIssuedAt(Instant issuedAt) { this.issuedAt = issuedAt; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public static class VehicleCommandEventBuilder {
        private UUID commandId;
        private UUID vehicleId;
        private CommandType type;
        private Instant issuedAt;
        private String idempotencyKey;

        VehicleCommandEventBuilder() {}

        public VehicleCommandEventBuilder commandId(UUID commandId) { this.commandId = commandId; return this; }
        public VehicleCommandEventBuilder vehicleId(UUID vehicleId) { this.vehicleId = vehicleId; return this; }
        public VehicleCommandEventBuilder type(CommandType type) { this.type = type; return this; }
        public VehicleCommandEventBuilder issuedAt(Instant issuedAt) { this.issuedAt = issuedAt; return this; }
        public VehicleCommandEventBuilder idempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; return this; }

        public VehicleCommandEvent build() {
            return new VehicleCommandEvent(commandId, vehicleId, type, issuedAt, idempotencyKey);
        }
    }
}
