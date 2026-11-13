package com.courtly.api.owner.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 2.3.12 - danh sach yeu cau dat san cua chu san.
 *
 * @param awaitingCount    tong so yeu cau dang cho duyet (khong phu thuoc bo loc)
 * @param expiringSoonCount so yeu cau cho duyet con duoi {@link #EXPIRING_SOON_MINUTES} phut
 */
public record BookingRequestResponse(List<Item> content, int page, int size, long totalElements, int totalPages,
                                     long awaitingCount, long expiringSoonCount) {

    public static final int EXPIRING_SOON_MINUTES = 10;

    /**
     * @param status           trang thai booking (pending_owner_confirmation, ...)
     * @param respondBy        han chu san phai phan hoi; null khi da xu ly
     * @param secondsRemaining so giay con lai den han phan hoi, 0 neu da qua han hoac khong co han
     * @param expiringSoon     con duoi 10 phut
     */
    public record Item(UUID bookingId, String bookingCode, String status,
                       UUID venueId, String venueName,
                       String playerName, String playerPhone,
                       List<CourtSlot> slots, BigDecimal totalAmount, String note,
                       Instant respondBy, long secondsRemaining, boolean expiringSoon, Instant createdAt) {
    }

    public record CourtSlot(String courtName, Instant startTime, Instant endTime) {
    }
}
