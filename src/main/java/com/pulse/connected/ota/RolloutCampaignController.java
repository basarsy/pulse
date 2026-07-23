package com.pulse.connected.ota;

import com.pulse.connected.ota.dto.CampaignResponse;
import com.pulse.connected.ota.dto.CreateCampaignRequest;
import com.pulse.connected.ota.dto.VehicleUpdateStatusResponse;
import com.pulse.connected.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/rollouts")
public class RolloutCampaignController {

    private final RolloutCampaignService campaignService;

    public RolloutCampaignController(RolloutCampaignService campaignService) {
        this.campaignService = campaignService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('FLEET_ADMIN', 'SYSTEM_ADMIN')")
    public ResponseEntity<CampaignResponse> createCampaign(
            @Valid @RequestBody CreateCampaignRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        CampaignResponse response = campaignService.createCampaign(request, principal.getUser());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasAnyRole('FLEET_ADMIN', 'SYSTEM_ADMIN')")
    public ResponseEntity<CampaignResponse> startCampaign(@PathVariable UUID id) {
        CampaignResponse response = campaignService.startCampaign(id);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/pause")
    @PreAuthorize("hasAnyRole('FLEET_ADMIN', 'SYSTEM_ADMIN')")
    public ResponseEntity<CampaignResponse> pauseCampaign(@PathVariable UUID id) {
        CampaignResponse response = campaignService.pauseCampaign(id);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/resume")
    @PreAuthorize("hasAnyRole('FLEET_ADMIN', 'SYSTEM_ADMIN')")
    public ResponseEntity<CampaignResponse> resumeCampaign(@PathVariable UUID id) {
        CampaignResponse response = campaignService.resumeCampaign(id);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/abort")
    @PreAuthorize("hasAnyRole('FLEET_ADMIN', 'SYSTEM_ADMIN')")
    public ResponseEntity<CampaignResponse> abortCampaign(@PathVariable UUID id) {
        CampaignResponse response = campaignService.abortCampaign(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('FLEET_ADMIN', 'SYSTEM_ADMIN')")
    public ResponseEntity<CampaignResponse> getCampaignDetails(@PathVariable UUID id) {
        CampaignResponse response = campaignService.getCampaignDetails(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/vehicles")
    @PreAuthorize("hasAnyRole('FLEET_ADMIN', 'SYSTEM_ADMIN')")
    public ResponseEntity<List<VehicleUpdateStatusResponse>> getCampaignVehicleStatuses(@PathVariable UUID id) {
        List<VehicleUpdateStatusResponse> statuses = campaignService.getCampaignVehicleStatuses(id);
        return ResponseEntity.ok(statuses);
    }
}
