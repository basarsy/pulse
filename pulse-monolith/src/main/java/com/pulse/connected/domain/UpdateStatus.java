package com.pulse.connected.domain;

public enum UpdateStatus {
    PENDING,
    DOWNLOADING,
    DOWNLOADED,
    INSTALLING,
    INSTALLED,
    FAILED,
    ROLLED_BACK,
    /**
     * The vehicle reported no progress within the update timeout (e.g. parked, or offline). Finished
     * for the rollout, so its stage can move on, but not a failure: it doesn't count towards the
     * failure threshold. A later report from the vehicle still settles it (installed or failed).
     */
    TIMED_OUT
}
