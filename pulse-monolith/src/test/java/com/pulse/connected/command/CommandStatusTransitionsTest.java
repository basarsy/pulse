package com.pulse.connected.command;

import com.pulse.connected.domain.CommandStatus;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static com.pulse.connected.domain.CommandStatus.*;
import static org.assertj.core.api.Assertions.assertThat;

class CommandStatusTransitionsTest {

    private static final Set<CommandStatus> FINAL = EnumSet.of(COMPLETED, FAILED, TIMED_OUT, CANCELLED);

    @Test
    void finalStates_AreFinal() {
        for (CommandStatus status : CommandStatus.values()) {
            assertThat(CommandStatusTransitions.isFinal(status))
                    .as("isFinal(%s)", status)
                    .isEqualTo(FINAL.contains(status));
        }
        assertThat(CommandStatusTransitions.isFinal(PENDING)).isFalse();
        assertThat(CommandStatusTransitions.isFinal(SENT)).isFalse();
        assertThat(CommandStatusTransitions.isFinal(ACKNOWLEDGED)).isFalse();
    }

    @Test
    void nothingLeavesAFinalState() {
        for (CommandStatus from : FINAL) {
            for (CommandStatus to : CommandStatus.values()) {
                assertThat(CommandStatusTransitions.isAllowed(from, to)).as("%s -> %s", from, to).isFalse();
            }
        }
    }

    @Test
    void forwardPath_Allowed() {
        assertThat(CommandStatusTransitions.isAllowed(PENDING, SENT)).isTrue();
        assertThat(CommandStatusTransitions.isAllowed(SENT, ACKNOWLEDGED)).isTrue();
        assertThat(CommandStatusTransitions.isAllowed(ACKNOWLEDGED, COMPLETED)).isTrue();
        assertThat(CommandStatusTransitions.isAllowed(SENT, FAILED)).isTrue();
        assertThat(CommandStatusTransitions.isAllowed(PENDING, TIMED_OUT)).isTrue();
    }

    @Test
    void backwardsOrSkipping_NotAllowed() {
        assertThat(CommandStatusTransitions.isAllowed(SENT, PENDING)).isFalse();
        assertThat(CommandStatusTransitions.isAllowed(ACKNOWLEDGED, SENT)).isFalse();
        assertThat(CommandStatusTransitions.isAllowed(PENDING, ACKNOWLEDGED)).isFalse();
        assertThat(CommandStatusTransitions.isAllowed(ACKNOWLEDGED, ACKNOWLEDGED)).isFalse();
    }
}
