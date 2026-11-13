package com.pulse.connected.ota;

import com.pulse.connected.domain.UpdateStatus;

import java.util.List;

/**
 * Allowed transitions of a vehicle's update status. Progress events can arrive late or be
 * redelivered, so an event that would move an update backwards must be ignored rather than
 * applied.
 *
 * <pre>
 * PENDING → DOWNLOADING → DOWNLOADED → INSTALLING → INSTALLED   (forward only; repeats allowed
 *                                                                 for progress updates)
 * any non-final state → FAILED or ROLLED_BACK
 * FAILED → ROLLED_BACK
 * INSTALLED, ROLLED_BACK: final
 * </pre>
 */
final class UpdateStatusTransitions {

    private static final List<UpdateStatus> HAPPY_PATH = List.of(
            UpdateStatus.PENDING,
            UpdateStatus.DOWNLOADING,
            UpdateStatus.DOWNLOADED,
            UpdateStatus.INSTALLING,
            UpdateStatus.INSTALLED);

    private UpdateStatusTransitions() {}

    static boolean isAllowed(UpdateStatus from, UpdateStatus to) {
        if (from == UpdateStatus.INSTALLED || from == UpdateStatus.ROLLED_BACK) {
            return false;
        }
        if (from == UpdateStatus.FAILED) {
            return to == UpdateStatus.ROLLED_BACK;
        }
        if (to == UpdateStatus.FAILED || to == UpdateStatus.ROLLED_BACK) {
            return true;
        }
        return HAPPY_PATH.indexOf(to) >= HAPPY_PATH.indexOf(from);
    }
}
