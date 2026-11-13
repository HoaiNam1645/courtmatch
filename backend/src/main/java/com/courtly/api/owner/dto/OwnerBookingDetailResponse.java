package com.courtly.api.owner.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 2.3.15 - chi tiet mot booking cua chu san kem timeline trang thai. */
public record OwnerBookingDetailResponse(UUID bookingId, String bookingCode, String status,
                                         UUID venueId, String venueName,
                                         String playerName, String playerPhone,
                                         List<BookingRequestResponse.CourtSlot> slots,
                                         BigDecimal totalAmount, String note,
                                         String rejectionReason, String cancellationReason,
                                         Payment payment, List<Step> timeline, Instant createdAt) {

    /** @param method cash hoac owner_bank_transfer */
    public record Payment(UUID paymentId, String status, String method, BigDecimal amount,
                          Instant payerReportedAt, Instant confirmedAt, String rejectionReason) {
    }

    /** @param bySystem true khi do he thong tu chuyen (het han, hoan tat sau gio choi) */
    public record Step(String oldStatus, String newStatus, String reason, String actorName,
                       String actorRole, boolean bySystem, Instant createdAt) {
    }
}
