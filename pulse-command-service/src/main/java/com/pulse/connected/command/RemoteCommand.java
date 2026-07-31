package com.pulse.connected.command;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import com.pulse.connected.domain.CommandType;
import com.pulse.connected.domain.CommandStatus;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "remote_commands")
public class RemoteCommand {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "vehicle_id", nullable = false)
    private UUID vehicleId;

    @Column(name = "issued_by", nullable = false)
    private UUID issuedById;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CommandType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CommandStatus status = CommandStatus.PENDING;

    @CreationTimestamp
    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "result_message", length = 500)
    private String resultMessage;

    @Column(name = "idempotency_key", unique = true, length = 100)
    private String idempotencyKey;

    public RemoteCommand() {}

    public RemoteCommand(UUID id, UUID vehicleId, UUID issuedById, CommandType type, CommandStatus status, Instant requestedAt, Instant resolvedAt, String resultMessage, String idempotencyKey) {
        this.id = id;
        this.vehicleId = vehicleId;
        this.issuedById = issuedById;
        this.type = type;
        this.status = status != null ? status : CommandStatus.PENDING;
        this.requestedAt = requestedAt;
        this.resolvedAt = resolvedAt;
        this.resultMessage = resultMessage;
        this.idempotencyKey = idempotencyKey;
    }

    public static RemoteCommandBuilder builder() {
        return new RemoteCommandBuilder();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getVehicleId() { return vehicleId; }
    public void setVehicleId(UUID vehicleId) { this.vehicleId = vehicleId; }

    public UUID getIssuedById() { return issuedById; }
    public void setIssuedById(UUID issuedById) { this.issuedById = issuedById; }

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

    public static class RemoteCommandBuilder {
        private UUID id;
        private UUID vehicleId;
        private UUID issuedById;
        private CommandType type;
        private CommandStatus status = CommandStatus.PENDING;
        private Instant requestedAt;
        private Instant resolvedAt;
        private String resultMessage;
        private String idempotencyKey;

        RemoteCommandBuilder() {}

        public RemoteCommandBuilder id(UUID id) { this.id = id; return this; }
        public RemoteCommandBuilder vehicleId(UUID vehicleId) { this.vehicleId = vehicleId; return this; }
        public RemoteCommandBuilder issuedById(UUID issuedById) { this.issuedById = issuedById; return this; }
        public RemoteCommandBuilder type(CommandType type) { this.type = type; return this; }
        public RemoteCommandBuilder status(CommandStatus status) { this.status = status; return this; }
        public RemoteCommandBuilder requestedAt(Instant requestedAt) { this.requestedAt = requestedAt; return this; }
        public RemoteCommandBuilder resolvedAt(Instant resolvedAt) { this.resolvedAt = resolvedAt; return this; }
        public RemoteCommandBuilder resultMessage(String resultMessage) { this.resultMessage = resultMessage; return this; }
        public RemoteCommandBuilder idempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; return this; }

        public RemoteCommand build() {
            return new RemoteCommand(id, vehicleId, issuedById, type, status, requestedAt, resolvedAt, resultMessage, idempotencyKey);
        }
    }
}
