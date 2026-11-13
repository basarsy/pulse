package com.pulse.connected.common;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "audit_log")
public class AuditLogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "actor_user_id", nullable = true)
    private UUID actorUserId;

    @Column(nullable = false, length = 100)
    private String action;

    @Column(nullable = false, length = 50)
    private String targetType;

    @Column(length = 100)
    private String targetId;

    @CreationTimestamp
    @Column(name = "timestamp", updatable = false)
    private Instant timestamp;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    public AuditLogEntry() {
    }

    private AuditLogEntry(Builder builder) {
        this.id = builder.id;
        this.actorUserId = builder.actorUserId;
        this.action = builder.action;
        this.targetType = builder.targetType;
        this.targetId = builder.targetId;
        this.timestamp = builder.timestamp;
        this.metadata = builder.metadata;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getActorUserId() { return actorUserId; }
    public void setActorUserId(UUID actorUserId) { this.actorUserId = actorUserId; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }
    public String getTargetId() { return targetId; }
    public void setTargetId(String targetId) { this.targetId = targetId; }
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private UUID id;
        private UUID actorUserId;
        private String action;
        private String targetType;
        private String targetId;
        private Instant timestamp;
        private Map<String, Object> metadata;

        public Builder id(UUID id) { this.id = id; return this; }
        public Builder actorUserId(UUID actorUserId) { this.actorUserId = actorUserId; return this; }
        public Builder action(String action) { this.action = action; return this; }
        public Builder targetType(String targetType) { this.targetType = targetType; return this; }
        public Builder targetId(String targetId) { this.targetId = targetId; return this; }
        public Builder timestamp(Instant timestamp) { this.timestamp = timestamp; return this; }
        public Builder metadata(Map<String, Object> metadata) { this.metadata = metadata; return this; }

        public AuditLogEntry build() { return new AuditLogEntry(this); }
    }
}
