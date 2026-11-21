package com.pulse.connected.vehicle;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VehicleRepository extends JpaRepository<Vehicle, UUID> {

    Optional<Vehicle> findByVin(String vin);

    boolean existsByVin(String vin);

    @Query("SELECT DISTINCT v FROM Vehicle v LEFT JOIN v.authorizedUsers au WHERE v.owner.id = :userId OR au.id = :userId")
    List<Vehicle> findAllAccessibleByUserId(@Param("userId") UUID userId);

    /**
     * Marks ONLINE vehicles whose last heartbeat is older than {@code cutoff} (or missing) as
     * OFFLINE, in one statement. Returns the number of vehicles marked.
     */
    // --- Targeted writes. Heartbeats, telemetry and OTA installs each change their own columns of
    // the same row, from different Kafka listener threads. Loading the entity and saving it back
    // writes every column, so one writer could put back another's older values (a heartbeat
    // reverting a just-installed software version). Each of these updates only its own columns.

    /**
     * Records a heartbeat. Returns 1 if this heartbeat brought the vehicle back online, 0 if it was
     * already online or doesn't exist. Atomic, so of two simultaneous heartbeats only one sees the
     * reconnect.
     */
    @Modifying
    @Query("""
            UPDATE Vehicle v SET v.connectivityState = com.pulse.connected.domain.ConnectivityState.ONLINE,
                                 v.lastSeenAt = :seenAt
            WHERE v.id = :id AND v.connectivityState <> com.pulse.connected.domain.ConnectivityState.ONLINE
            """)
    int markOnline(@Param("id") UUID id, @Param("seenAt") Instant seenAt);

    /** Moves {@code lastSeenAt} forward. Returns 0 if the vehicle doesn't exist. */
    @Modifying
    @Query("UPDATE Vehicle v SET v.lastSeenAt = :seenAt WHERE v.id = :id")
    int markSeen(@Param("id") UUID id, @Param("seenAt") Instant seenAt);

    /** Stores the latest telemetry reading on the vehicle. Returns 0 if the vehicle doesn't exist. */
    @Modifying
    @Query("""
            UPDATE Vehicle v SET v.batteryOrFuelLevel = :battery, v.odometerKm = :odometer,
                                 v.lastLat = :lat, v.lastLng = :lng, v.lastSeenAt = :seenAt
            WHERE v.id = :id
            """)
    int updateTelemetry(@Param("id") UUID id, @Param("battery") Double battery, @Param("odometer") Double odometer,
                        @Param("lat") Double lat, @Param("lng") Double lng, @Param("seenAt") Instant seenAt);

    /** Records the software version a vehicle now runs. */
    @Modifying
    @Query("UPDATE Vehicle v SET v.currentSoftwareVersion = :version WHERE v.id = :id")
    int updateSoftwareVersion(@Param("id") UUID id, @Param("version") String version);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Vehicle v SET v.connectivityState = com.pulse.connected.domain.ConnectivityState.OFFLINE
            WHERE v.connectivityState = com.pulse.connected.domain.ConnectivityState.ONLINE
              AND (v.lastSeenAt IS NULL OR v.lastSeenAt < :cutoff)
            """)
    int markOnlineVehiclesOfflineLastSeenBefore(@Param("cutoff") Instant cutoff);
}
