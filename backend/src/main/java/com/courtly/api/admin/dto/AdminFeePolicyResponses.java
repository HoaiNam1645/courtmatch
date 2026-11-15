package com.courtly.api.admin.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 2.3.39 - 2.3.41 - cau hinh phi nen tang va nguong han che cong no qua han. */
public final class AdminFeePolicyResponses {

    private AdminFeePolicyResponses() {
    }

    /**
     * @param feeType         percentage (feeValue la %) hoac fixed (feeValue la VND moi booking)
     * @param status          active hoac inactive
     * @param commissionCount so hoa hong da ghi nhan theo chinh sach nay
     * @param commissionTotal tong tien hoa hong da ghi nhan theo chinh sach nay
     */
    public record Policy(UUID id, String name, String feeType, BigDecimal feeValue, String status, Instant effectiveFrom,
                         Instant effectiveTo, String createdBy, Instant createdAt, long commissionCount,
                         BigDecimal commissionTotal) {
    }

    /**
     * @param level 1 = chan nhan booking moi, 2 = tam an san
     * @param minOverdueAmount null khi khong dung nguong so tien
     * @param minOverdueDays   null khi khong dung nguong so ngay
     */
    public record Restriction(int level, boolean enabled, BigDecimal minOverdueAmount, Integer minOverdueDays,
                              String updatedBy, Instant updatedAt) {
    }

    /** @param active chinh sach dang ap dung; null neu he thong chua co (khong the ghi nhan hoa hong) */
    public record ListResponse(Policy active, List<Policy> policies, List<Restriction> restrictions) {
    }
}
