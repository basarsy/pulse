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
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Vehicle v SET v.connectivityState = com.pulse.connected.domain.ConnectivityState.OFFLINE
            WHERE v.connectivityState = com.pulse.connected.domain.ConnectivityState.ONLINE
              AND (v.lastSeenAt IS NULL OR v.lastSeenAt < :cutoff)
            """)
    int markOnlineVehiclesOfflineLastSeenBefore(@Param("cutoff") Instant cutoff);
}
