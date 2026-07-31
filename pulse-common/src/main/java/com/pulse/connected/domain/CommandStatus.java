package com.pulse.connected.domain;

public enum CommandStatus {
    PENDING,
    SENT,
    ACKNOWLEDGED,
    COMPLETED,
    FAILED,
    TIMED_OUT
}
