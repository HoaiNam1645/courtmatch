package com.courtly.api.owner.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 2.3.25 - trang thai thanh toan cong no hoa hong.
 *
 * <p>Cong no chi giam khi Courtly xac nhan, nen so tien dang cho xac nhan duoc hien rieng.
 *
 * @param totalOutstanding   cong no hien tai (2.3.20)
 * @param pendingAmount      tong tien da gui, dang cho Courtly xac nhan
 * @param confirmedAmount    tong tien Courtly da xac nhan tu truoc den nay
 * @param rejectedCount      so lan gui bi tu choi
 * @param lastPayment        lan gui gan nhat; null neu chua gui lan nao
 * @param unpaidSettlements  cac ky chua thanh toan xong, ky moi nhat truoc
 */
public record CommissionPaymentStatusResponse(BigDecimal totalOutstanding, BigDecimal pendingAmount, long pendingCount,
                                              BigDecimal confirmedAmount, long confirmedCount, long rejectedCount,
                                              CommissionPaymentResponse lastPayment,
                                              List<SettlementStatus> unpaidSettlements) {

    /**
     * @param pendingAmount dang cho xac nhan cua ky
     * @param payableAmount con co the gui = con phai tra - dang cho xac nhan (khong am)
     */
    public record SettlementStatus(UUID id, LocalDate periodFrom, LocalDate periodTo, String status,
                                   BigDecimal amountDue, BigDecimal amountPaid, BigDecimal remainingAmount,
                                   BigDecimal pendingAmount, BigDecimal payableAmount, Instant ownerSubmittedAt) {
    }
}
