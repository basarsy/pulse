package com.pulse.connected.ota;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SoftwareVersionRepository extends JpaRepository<SoftwareVersion, UUID> {
    Optional<SoftwareVersion> findByVersionLabel(String versionLabel);
    boolean existsByVersionLabel(String versionLabel);
}
