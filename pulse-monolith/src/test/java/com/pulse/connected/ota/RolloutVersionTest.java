package com.pulse.connected.ota;

import com.pulse.connected.AbstractIntegrationTest;
import com.pulse.connected.auth.Role;
import com.pulse.connected.auth.User;
import com.pulse.connected.auth.UserRepository;
import com.pulse.connected.domain.CampaignStatus;
import com.pulse.connected.domain.RolloutStageStatus;
import com.pulse.connected.ota.dto.CampaignResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every rollout update gets a version no earlier update had, even when one transaction publishes
 * twice without changing the rollout row in between: a later update must never share a version
 * with an earlier, different state, or a browser could keep the earlier one.
 */
class RolloutVersionTest extends AbstractIntegrationTest {

    @Autowired
    private RolloutCampaignService campaignService;

    @Autowired
    private RolloutCampaignRepository campaignRepository;

    @Autowired
    private SoftwareVersionRepository softwareVersionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void twoUpdatesInOneTransaction_GetDifferentVersions_AndTheLastIsCommitted() {
        UUID campaignId = draftCampaign();

        List<CampaignResponse> published = new TransactionTemplate(transactionManager).execute(status -> {
            RolloutCampaign campaign = campaignRepository.findByIdForUpdate(campaignId).orElseThrow();
            return List.of(campaignService.publishProgress(campaign), campaignService.publishProgress(campaign));
        });

        assertThat(published.get(1).version()).isGreaterThan(published.get(0).version());
        assertThat(campaignRepository.findById(campaignId).orElseThrow().getVersion())
                .isEqualTo(published.get(1).version());
    }

    private UUID draftCampaign() {
        User admin = userRepository.save(User.builder()
                .email(uniqueEmail("rollout-version"))
                .passwordHash("not-a-real-hash")
                .fullName("Rollout Version")
                .roles(Set.of(Role.FLEET_ADMIN))
                .build());
        SoftwareVersion version = softwareVersionRepository.save(SoftwareVersion.builder()
                .versionLabel("ver-" + UUID.randomUUID().toString().substring(0, 20))
                .packageUrl("https://ota.example/pkg.bin")
                .checksum("0".repeat(64))
                .mandatory(false)
                .build());
        RolloutCampaign campaign = RolloutCampaign.builder()
                .softwareVersion(version).targetModel("VersionModel-" + UUID.randomUUID())
                .status(CampaignStatus.DRAFT).currentStageIndex(0)
                .failureThresholdPercent(10.0).createdBy(admin)
                .build();
        campaign.setStages(new ArrayList<>(List.of(
                RolloutStage.builder().campaign(campaign).percentage(100).status(RolloutStageStatus.PENDING).build())));
        return campaignRepository.save(campaign).getId();
    }
}
