package com.courtly.job;

import com.courtly.service.matching.PartnerRequestService;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Tu huy yeu cau ghep cap khong duoc phan hoi kip thoi gian va bao cho nguoi gui. */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "courtly.jobs.partner-request-expiry", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PartnerRequestExpiryJob {

    private final PartnerRequestService requestService;

    @Scheduled(initialDelayString = "${courtly.jobs.partner-request-expiry.initial-delay-ms:30000}",
            fixedDelayString = "${courtly.jobs.partner-request-expiry.interval-ms:60000}")
    public void expire() {
        try {
            int count = requestService.expireOverdue(Instant.now());
            if (count > 0) log.info("Da het han {} yeu cau ghep cap", count);
        } catch (Exception exception) {
            log.error("Khong het han duoc yeu cau ghep cap", exception);
        }
    }
}
