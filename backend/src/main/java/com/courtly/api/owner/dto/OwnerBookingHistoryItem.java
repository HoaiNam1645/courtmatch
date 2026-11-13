package com.courtly.api.owner.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 2.3.15 - mot dong trong lich su dat san cua chu san.
 *
 * @param status        trang thai booking
 * @param paymentStatus trang thai thanh toan moi nhat; null khi chua co (chua duoc xac nhan)
 */
public record OwnerBookingHistoryItem(UUID bookingId, String bookingCode, String status,
                                      UUID venueId, String venueName,
                                      String playerName, String playerPhone,
                                      List<BookingRequestResponse.CourtSlot> slots,
                                      BigDecimal totalAmount, String paymentStatus, Instant createdAt) {
}
