package com.courtly.job;

import com.courtly.service.dispute.DisputeRefundService;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Tac vu nen cua luong bao cao don dat san: tu xac nhan khoan hoan (2.3.108). Moi viec xu ly mot lan nho khoa dong. */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "courtly.jobs.disputes", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DisputeJob {

    private final DisputeRefundService refundService;
    private final com.courtly.service.dispute.DisputeTimeoutService timeoutService;

    @Scheduled(initialDelayString = "${courtly.jobs.disputes.initial-delay-ms:90000}",
            fixedDelayString = "${courtly.jobs.disputes.interval-ms:600000}")
    public void run() {
        try {
            Instant now = Instant.now();
            int reminded = timeoutService.remind(now);
            int resolved = timeoutService.resolveOverdue(now);
            if (reminded > 0 || resolved > 0) {
                log.info("Bao cao don dat san: nhac {} chu san, tu xu thua {} bao cao", reminded, resolved);
            }
            int confirmed = refundService.autoConfirmDue(now);
            if (confirmed > 0) {
                log.info("Tu xac nhan {} khoan hoan tien", confirmed);
            }
        } catch (Exception exception) {
            log.error("Khong xu ly duoc tac vu bao cao don dat san", exception);
        }
    }
}
