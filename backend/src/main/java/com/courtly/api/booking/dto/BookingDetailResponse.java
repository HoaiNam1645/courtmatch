package com.courtly.api.booking.dto;

import com.courtly.common.enums.BookingItemStatus;
import com.courtly.common.enums.BookingStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Chi tiet mot don dat san (2.1.29) kem timeline trang thai (2.1.30).
 *
 * @param cancellable co duoc huy khong - server quyet dinh, giao dien khong tu suy (2.1.31)
 */
public record BookingDetailResponse(UUID id,
                                    String bookingCode,
                                    BookingStatus status,
                                    BigDecimal totalAmount,
                                    String note,
                                    Instant expiresAt,
                                    Instant confirmedAt,
                                    Instant cancelledAt,
                                    String cancellationReason,
                                    Instant createdAt,
                                    boolean cancellable,
                                    VenueInfo venue,
                                    List<Item> items,
                                    List<StatusChange> statusHistory) {

    public record VenueInfo(UUID id,
                            String slug,
                            String name,
                            String address,
                            String phone,
                            String coverImageUrl,
                            BigDecimal latitude,
                            BigDecimal longitude) {
    }

    public record Item(UUID id,
                       UUID courtId,
                       String courtName,
                       Instant startTime,
                       Instant endTime,
                       BigDecimal price,
                       BookingItemStatus status) {
    }

    /** @param bySystem true khi thay doi do he thong tu thuc hien, vi du het han thanh toan */
    public record StatusChange(BookingStatus oldStatus,
                               BookingStatus newStatus,
                               String reason,
                               boolean bySystem,
                               Instant createdAt) {
    }
}
