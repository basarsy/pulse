package com.pulse.connected.ota;

public enum UpdateStatus {
    PENDING,
    DOWNLOADING,
    DOWNLOADED,
    INSTALLING,
    INSTALLED,
    FAILED,
    ROLLED_BACK
}
