package com.pulse.connected.command;

public enum CommandStatus {
    PENDING,
    SENT,
    ACKNOWLEDGED,
    COMPLETED,
    FAILED,
    TIMED_OUT
}
