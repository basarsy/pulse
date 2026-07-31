package com.pulse.connected.vehicle;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VehicleRepository extends JpaRepository<Vehicle, UUID> {

    Optional<Vehicle> findByVin(String vin);

    boolean existsByVin(String vin);

    @Query("SELECT DISTINCT v FROM Vehicle v LEFT JOIN v.authorizedUsers au WHERE v.owner.id = :userId OR au.id = :userId")
    List<Vehicle> findAllAccessibleByUserId(@Param("userId") UUID userId);
}
