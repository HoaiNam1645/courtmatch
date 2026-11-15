package com.courtly.api.admin.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 2.3.43 - danh sach giao dich doi soat: moi dong la hoa hong cua mot booking.
 *
 * <p>{@code totals} la tong cua toan bo tap dang loc, khong phu thuoc trang dang xem.
 */
public record AdminCommissionTransactionResponse(List<Row> content, int page, int size, long totalElements, int totalPages,
                                                  Totals totals, List<VenueOption> venues, List<SettlementOption> settlements) {

    /**
     * @param status  pending, due, adjusted, suspended, waived, settled
     * @param overdue chua thu (pending, due, adjusted) va qua han thanh toan
     * @param settlement ky doi soat da gan; null neu chua gan
     * @param settledAt  thoi diem doi soat xong; null neu chua
     */
    public record Row(UUID commissionId, UUID bookingId, String bookingCode, UUID venueId, String venueName, String ownerName,
                      BigDecimal grossAmount, BigDecimal commissionAmount, String status, Instant calculatedAt, Instant dueAt,
                      boolean overdue, Settlement settlement, Instant settledAt) {
    }

    public record Settlement(UUID id, LocalDate periodFrom, LocalDate periodTo, String status) {
    }

    public record Totals(long count, BigDecimal grossAmount, BigDecimal commissionAmount) {
    }

    public record VenueOption(UUID id, String name) {
    }

    public record SettlementOption(UUID id, String ownerName, LocalDate periodFrom, LocalDate periodTo, String status) {
    }
}
