package com.pulse.connected.ota;

import com.pulse.connected.domain.UpdateStatus;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class UpdateStatusTransitionsTest {

    @ParameterizedTest(name = "{0} -> {1} allowed={2}")
    @CsvSource({
            // forward progress
            "PENDING, DOWNLOADING, true",
            "DOWNLOADING, DOWNLOADING, true",
            "DOWNLOADING, DOWNLOADED, true",
            "DOWNLOADED, INSTALLING, true",
            "INSTALLING, INSTALLED, true",
            "PENDING, INSTALLED, true",
            // backwards (late / redelivered events)
            "DOWNLOADED, DOWNLOADING, false",
            "INSTALLING, DOWNLOADING, false",
            // failure from any non-final state
            "PENDING, FAILED, true",
            "INSTALLING, FAILED, true",
            "INSTALLING, ROLLED_BACK, true",
            "FAILED, ROLLED_BACK, true",
            // final states
            "INSTALLED, DOWNLOADING, false",
            "INSTALLED, FAILED, false",
            "FAILED, INSTALLING, false",
            "FAILED, INSTALLED, false",
            "ROLLED_BACK, FAILED, false",
            // timeout: from any unfinished state; afterwards only the outcome the vehicle reports late
            "PENDING, TIMED_OUT, true",
            "INSTALLING, TIMED_OUT, true",
            "TIMED_OUT, INSTALLED, true",
            "TIMED_OUT, FAILED, true",
            "TIMED_OUT, ROLLED_BACK, true",
            "TIMED_OUT, DOWNLOADING, false",
            "TIMED_OUT, INSTALLING, false",
            "TIMED_OUT, TIMED_OUT, false",
            "INSTALLED, TIMED_OUT, false",
            "FAILED, TIMED_OUT, false"
    })
    void transitions(UpdateStatus from, UpdateStatus to, boolean allowed) {
        assertThat(UpdateStatusTransitions.isAllowed(from, to)).isEqualTo(allowed);
    }
}
