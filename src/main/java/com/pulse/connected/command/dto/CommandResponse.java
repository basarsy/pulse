package com.pulse.connected.command.dto;

import com.pulse.connected.command.CommandStatus;
import com.pulse.connected.command.CommandType;

import java.time.Instant;
import java.util.UUID;

public class CommandResponse {

    private UUID id;
    private UUID vehicleId;
    private UUID issuedByUserId;
    private CommandType type;
    private CommandStatus status;
    private Instant requestedAt;
    private Instant resolvedAt;
    private String resultMessage;
    private String idempotencyKey;

    public CommandResponse() {}

    public CommandResponse(UUID id, UUID vehicleId, UUID issuedByUserId, CommandType type, CommandStatus status, Instant requestedAt, Instant resolvedAt, String resultMessage, String idempotencyKey) {
        this.id = id;
        this.vehicleId = vehicleId;
        this.issuedByUserId = issuedByUserId;
        this.type = type;
        this.status = status;
        this.requestedAt = requestedAt;
        this.resolvedAt = resolvedAt;
        this.resultMessage = resultMessage;
        this.idempotencyKey = idempotencyKey;
    }

    public static CommandResponseBuilder builder() {
        return new CommandResponseBuilder();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getVehicleId() { return vehicleId; }
    public void setVehicleId(UUID vehicleId) { this.vehicleId = vehicleId; }

    public UUID getIssuedByUserId() { return issuedByUserId; }
    public void setIssuedByUserId(UUID issuedByUserId) { this.issuedByUserId = issuedByUserId; }

    public CommandType getType() { return type; }
    public void setType(CommandType type) { this.type = type; }

    public CommandStatus getStatus() { return status; }
    public void setStatus(CommandStatus status) { this.status = status; }

    public Instant getRequestedAt() { return requestedAt; }
    public void setRequestedAt(Instant requestedAt) { this.requestedAt = requestedAt; }

    public Instant getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(Instant resolvedAt) { this.resolvedAt = resolvedAt; }

    public String getResultMessage() { return resultMessage; }
    public void setResultMessage(String resultMessage) { this.resultMessage = resultMessage; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public static class CommandResponseBuilder {
        private UUID id;
        private UUID vehicleId;
        private UUID issuedByUserId;
        private CommandType type;
        private CommandStatus status;
        private Instant requestedAt;
        private Instant resolvedAt;
        private String resultMessage;
        private String idempotencyKey;

        CommandResponseBuilder() {}

        public CommandResponseBuilder id(UUID id) { this.id = id; return this; }
        public CommandResponseBuilder vehicleId(UUID vehicleId) { this.vehicleId = vehicleId; return this; }
        public CommandResponseBuilder issuedByUserId(UUID issuedByUserId) { this.issuedByUserId = issuedByUserId; return this; }
        public CommandResponseBuilder type(CommandType type) { this.type = type; return this; }
        public CommandResponseBuilder status(CommandStatus status) { this.status = status; return this; }
        public CommandResponseBuilder requestedAt(Instant requestedAt) { this.requestedAt = requestedAt; return this; }
        public CommandResponseBuilder resolvedAt(Instant resolvedAt) { this.resolvedAt = resolvedAt; return this; }
        public CommandResponseBuilder resultMessage(String resultMessage) { this.resultMessage = resultMessage; return this; }
        public CommandResponseBuilder idempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; return this; }

        public CommandResponse build() {
            return new CommandResponse(id, vehicleId, issuedByUserId, type, status, requestedAt, resolvedAt, resultMessage, idempotencyKey);
        }
    }
}
