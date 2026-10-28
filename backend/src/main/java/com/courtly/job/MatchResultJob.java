package com.courtly.job;

import com.courtly.service.match.MatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Tu xac nhan ket qua va dong tranh chap qua han cua 2.2.2. */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "courtly.jobs.match-data", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class MatchResultJob {

    private final MatchService matchService;

    @Scheduled(fixedDelayString = "${courtly.jobs.match-data.confirmation-interval-ms:3600000}")
    public void resolvePendingResults() {
        try {
            matchService.autoConfirmOverdueMatches();
            matchService.cancelExpiredDisputes();
        } catch (Exception exception) {
            log.error("Khong xu ly duoc ket qua tran qua han", exception);
        }
    }
}
