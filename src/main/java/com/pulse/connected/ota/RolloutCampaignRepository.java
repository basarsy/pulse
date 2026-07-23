package com.pulse.connected.ota;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RolloutCampaignRepository extends JpaRepository<RolloutCampaign, UUID> {
    List<RolloutCampaign> findAllByTargetModel(String targetModel);
    List<RolloutCampaign> findAllByStatus(CampaignStatus status);
}
