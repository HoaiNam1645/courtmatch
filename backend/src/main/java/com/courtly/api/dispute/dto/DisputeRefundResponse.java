package com.courtly.api.dispute.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Khoan hoan tien truc tiep cua bao cao (2.3.108). Chu san hoan, Courtly chi theo doi va xac nhan.
 *
 * @param status          awaiting_owner_refund, owner_reported_refunded hoac confirmed
 * @param proofUrl        anh chung tu chu san gui (rieng tu, xem qua API co kiem tra quyen); null khi chua co
 * @param autoConfirmAt   luc he thong tu xac nhan neu nguoi choi khong phan hoi (chi khi chu san da bao hoan)
 * @param canReport       chu san bao da hoan duoc
 * @param canConfirm      nguoi choi xac nhan / khieu nai duoc
 * @param canAdminConfirm quan tri vien xac nhan thay duoc
 */
public record DisputeRefundResponse(UUID id, BigDecimal amount, String status, Instant createdAt, Instant ownerReportedAt,
                                    String ownerNote, String proofUrl, String playerComplaint, Instant complainedAt,
                                    Instant confirmedAt, boolean autoConfirmed, Instant autoConfirmAt, boolean canReport,
                                    boolean canConfirm, boolean canAdminConfirm) {
}
