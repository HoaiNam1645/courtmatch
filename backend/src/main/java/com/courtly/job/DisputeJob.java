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

    @Scheduled(initialDelayString = "${courtly.jobs.disputes.initial-delay-ms:90000}",
            fixedDelayString = "${courtly.jobs.disputes.interval-ms:600000}")
    public void run() {
        try {
            int confirmed = refundService.autoConfirmDue(Instant.now());
            if (confirmed > 0) {
                log.info("Tu xac nhan {} khoan hoan tien", confirmed);
            }
        } catch (Exception exception) {
            log.error("Khong xu ly duoc tac vu bao cao don dat san", exception);
        }
    }
}
