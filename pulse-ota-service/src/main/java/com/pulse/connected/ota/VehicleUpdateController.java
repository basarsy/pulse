package com.pulse.connected.ota;

import com.pulse.connected.ota.dto.VehicleUpdateStatusResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/vehicles/{id}/update-status")
public class VehicleUpdateController {

    private final RolloutCampaignService campaignService;

    public VehicleUpdateController(RolloutCampaignService campaignService) {
        this.campaignService = campaignService;
    }

    @GetMapping
    public ResponseEntity<List<VehicleUpdateStatusResponse>> getVehicleUpdateStatus(@PathVariable UUID id) {
        List<VehicleUpdateStatusResponse> history = campaignService.getVehicleIdUpdateHistory(id);
        return ResponseEntity.ok(history);
    }
}
