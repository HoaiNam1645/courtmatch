package com.courtly.job;

import com.courtly.service.match.PlayerStatisticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Doi soat du lieu tong hop theo nguoi choi cua 2.2.3. */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "courtly.jobs.match-data", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class PlayerStatisticsJob {

    private final PlayerStatisticsService statisticsService;

    @Scheduled(cron = "${courtly.jobs.match-data.reconciliation-cron:0 20 2 * * *}",
            zone = "Asia/Ho_Chi_Minh")
    public void reconcileStatistics() {
        try {
            statisticsService.reconcileAll();
        } catch (Exception exception) {
            log.error("Khong doi soat duoc player_statistics", exception);
        }
    }
}
