package com.pulse.connected.command;

import com.pulse.connected.domain.CommandStatus;

import java.util.EnumSet;
import java.util.Set;

/**
 * Allowed transitions of a remote command's status. Results from vehicles can arrive late or be
 * redelivered, and a command can time out or be cancelled while its result is in flight, so a
 * change that is not allowed must be ignored rather than applied.
 *
 * <pre>
 * PENDING → SENT → ACKNOWLEDGED
 * any non-final state → COMPLETED, FAILED, TIMED_OUT or CANCELLED
 * COMPLETED, FAILED, TIMED_OUT, CANCELLED: final
 * </pre>
 */
final class CommandStatusTransitions {

    private static final Set<CommandStatus> FINAL = EnumSet.of(
            CommandStatus.COMPLETED,
            CommandStatus.FAILED,
            CommandStatus.TIMED_OUT,
            CommandStatus.CANCELLED);

    private CommandStatusTransitions() {}

    static boolean isFinal(CommandStatus status) {
        return FINAL.contains(status);
    }

    static boolean isAllowed(CommandStatus from, CommandStatus to) {
        if (isFinal(from)) {
            return false;
        }
        return switch (to) {
            case PENDING -> false;
            case SENT -> from == CommandStatus.PENDING;
            case ACKNOWLEDGED -> from == CommandStatus.SENT;
            case COMPLETED, FAILED, TIMED_OUT, CANCELLED -> true;
        };
    }
}
