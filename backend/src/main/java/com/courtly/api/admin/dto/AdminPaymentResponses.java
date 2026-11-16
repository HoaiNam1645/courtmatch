package com.courtly.api.admin.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** 2.3.47 - 2.3.50: bao cao thanh toan cong no hoa hong cua chu san, goc nhin quan tri vien. */
public final class AdminPaymentResponses {

    private AdminPaymentResponses() {
    }

    public record Settlement(UUID id, LocalDate periodFrom, LocalDate periodTo, String status) {
    }

    /**
     * @param status              pending_review, confirmed, rejected
     * @param courtlyOffsetAmount phan Courtly no chu san da bu tru vao cong no khi xac nhan
     * @param recordedByAdmin     quan tri vien tu ghi nhan thay chu san (khong co bao cao cua chu san)
     * @param settlement          ky doi soat bao cao gan voi; null = tra chung vao cong no hien tai
     */
    public record Row(UUID id, UUID ownerId, String ownerName, String ownerEmail, Settlement settlement, BigDecimal amount,
                      BigDecimal courtlyOffsetAmount, LocalDate paidOn, String referenceCode, String proofUrl, String note,
                      String status, String reviewNote, String reviewedByName, Instant reviewedAt, Instant createdAt,
                      boolean recordedByAdmin) {
    }

    public record Totals(long count, BigDecimal amount) {
    }

    public record OwnerOption(UUID id, String fullName, String email) {
    }

    /**
     * Tong quan cua mot chu san khi loc theo chu san (2.3.50).
     *
     * @param confirmedAmount tong tien da duoc xac nhan
     * @param pendingAmount   tong tien dang cho xac nhan
     * @param outstanding     cong no hoa hong con lai (chua tru so du)
     * @param credit          so du da thanh toan nhung chua du dong mot hoa hong
     */
    public record OwnerSummary(UUID ownerId, String ownerName, BigDecimal confirmedAmount, BigDecimal pendingAmount,
                               BigDecimal outstanding, BigDecimal credit, BigDecimal owedByCourtly, int restrictionLevel) {
    }

    public record ListResponse(List<Row> content, int page, int size, long totalElements, int totalPages, Totals totals,
                               java.util.Map<String, Long> statusCounts, List<OwnerOption> owners, OwnerSummary ownerSummary) {
    }
}
