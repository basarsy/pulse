package com.pulse.connected.command.dto;

import com.pulse.connected.command.CommandType;
import jakarta.validation.constraints.NotNull;

public class IssueCommandRequest {

    @NotNull(message = "Command type is required")
    private CommandType type;

    private String idempotencyKey;

    public IssueCommandRequest() {}

    public IssueCommandRequest(CommandType type, String idempotencyKey) {
        this.type = type;
        this.idempotencyKey = idempotencyKey;
    }

    public static IssueCommandRequestBuilder builder() {
        return new IssueCommandRequestBuilder();
    }

    public CommandType getType() { return type; }
    public void setType(CommandType type) { this.type = type; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public static class IssueCommandRequestBuilder {
        private CommandType type;
        private String idempotencyKey;

        IssueCommandRequestBuilder() {}

        public IssueCommandRequestBuilder type(CommandType type) { this.type = type; return this; }
        public IssueCommandRequestBuilder idempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; return this; }

        public IssueCommandRequest build() {
            return new IssueCommandRequest(type, idempotencyKey);
        }
    }
}
