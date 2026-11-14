package com.pulse.connected.vehicle;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class VehicleConnectivityMonitorTest {

    @Mock
    private VehicleRepository vehicleRepository;

    @Test
    void marksVehiclesSilentLongerThanOfflineAfter() {
        VehicleConnectivityMonitor monitor =
                new VehicleConnectivityMonitor(vehicleRepository, new ConnectivityProperties(Duration.ofSeconds(60)));
        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);

        Instant before = Instant.now();
        monitor.markSilentVehiclesOffline();
        Instant after = Instant.now();

        verify(vehicleRepository).markOnlineVehiclesOfflineLastSeenBefore(cutoff.capture());
        assertThat(cutoff.getValue()).isBetween(before.minusSeconds(60), after.minusSeconds(60));
    }
}
