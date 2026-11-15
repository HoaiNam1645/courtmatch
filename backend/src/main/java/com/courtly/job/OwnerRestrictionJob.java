package com.courtly.job;

import com.courtly.service.owner.OwnerRestrictionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Tinh lai muc han che chu san co cong no qua han: cong no den han theo thoi gian nen can chay dinh ky. */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "courtly.jobs.owner-restriction", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OwnerRestrictionJob {

    private final OwnerRestrictionService restrictionService;

    @Scheduled(initialDelayString = "${courtly.jobs.owner-restriction.initial-delay-ms:60000}",
            fixedDelayString = "${courtly.jobs.owner-restriction.interval-ms:300000}")
    public void recompute() {
        try {
            restrictionService.recomputeAll();
        } catch (Exception e) {
            log.error("Khong tinh lai duoc muc han che chu san", e);
        }
    }
}
