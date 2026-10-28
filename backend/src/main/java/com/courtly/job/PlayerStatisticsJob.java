package com.courtly.job;

import com.courtly.service.match.PlayerPerformanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Doi soat toan bo pipeline thong ke va diem ky nang cua 2.2.3-2.2.7. */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "courtly.jobs.match-data", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class PlayerStatisticsJob {

    private final PlayerPerformanceService performanceService;

    @Scheduled(cron = "${courtly.jobs.match-data.reconciliation-cron:0 20 2 * * *}",
            zone = "Asia/Ho_Chi_Minh")
    public void reconcileStatistics() {
        try {
            performanceService.reconcileAll();
        } catch (Exception exception) {
            log.error("Khong doi soat duoc du lieu hieu suat nguoi choi", exception);
        }
    }
}
