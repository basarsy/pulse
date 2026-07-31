package com.pulse.connected.ota;

import com.pulse.connected.ota.dto.CreateSoftwareVersionRequest;
import com.pulse.connected.ota.dto.SoftwareVersionResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/software-versions")
public class SoftwareVersionController {

    private final SoftwareVersionService softwareVersionService;

    public SoftwareVersionController(SoftwareVersionService softwareVersionService) {
        this.softwareVersionService = softwareVersionService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('FLEET_ADMIN', 'SYSTEM_ADMIN')")
    public ResponseEntity<SoftwareVersionResponse> createSoftwareVersion(
            @Valid @RequestBody CreateSoftwareVersionRequest request) {
        SoftwareVersionResponse response = softwareVersionService.registerSoftwareVersion(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<SoftwareVersionResponse> getSoftwareVersionById(@PathVariable UUID id) {
        SoftwareVersionResponse response = softwareVersionService.getSoftwareVersionById(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<SoftwareVersionResponse>> getAllSoftwareVersions() {
        List<SoftwareVersionResponse> versions = softwareVersionService.getAllSoftwareVersions();
        return ResponseEntity.ok(versions);
    }
}
