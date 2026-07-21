package com.pulse.connected.command;

import com.pulse.connected.auth.User;
import com.pulse.connected.vehicle.Vehicle;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "remote_commands")
public class RemoteCommand {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "issued_by", nullable = false)
    private User issuedBy;

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

    public RemoteCommand(UUID id, Vehicle vehicle, User issuedBy, CommandType type, CommandStatus status, Instant requestedAt, Instant resolvedAt, String resultMessage, String idempotencyKey) {
        this.id = id;
        this.vehicle = vehicle;
        this.issuedBy = issuedBy;
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

    public Vehicle getVehicle() { return vehicle; }
    public void setVehicle(Vehicle vehicle) { this.vehicle = vehicle; }

    public User getIssuedBy() { return issuedBy; }
    public void setIssuedBy(User issuedBy) { this.issuedBy = issuedBy; }

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
        private Vehicle vehicle;
        private User issuedBy;
        private CommandType type;
        private CommandStatus status = CommandStatus.PENDING;
        private Instant requestedAt;
        private Instant resolvedAt;
        private String resultMessage;
        private String idempotencyKey;

        RemoteCommandBuilder() {}

        public RemoteCommandBuilder id(UUID id) { this.id = id; return this; }
        public RemoteCommandBuilder vehicle(Vehicle vehicle) { this.vehicle = vehicle; return this; }
        public RemoteCommandBuilder issuedBy(User issuedBy) { this.issuedBy = issuedBy; return this; }
        public RemoteCommandBuilder type(CommandType type) { this.type = type; return this; }
        public RemoteCommandBuilder status(CommandStatus status) { this.status = status; return this; }
        public RemoteCommandBuilder requestedAt(Instant requestedAt) { this.requestedAt = requestedAt; return this; }
        public RemoteCommandBuilder resolvedAt(Instant resolvedAt) { this.resolvedAt = resolvedAt; return this; }
        public RemoteCommandBuilder resultMessage(String resultMessage) { this.resultMessage = resultMessage; return this; }
        public RemoteCommandBuilder idempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; return this; }

        public RemoteCommand build() {
            return new RemoteCommand(id, vehicle, issuedBy, type, status, requestedAt, resolvedAt, resultMessage, idempotencyKey);
        }
    }
}
