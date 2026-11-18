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
                                    BigDecimal discountAmount,
                                    BigDecimal payableAmount,
                                    String voucherCode,
                                    String note,
                                    Instant expiresAt,
                                    Instant confirmedAt,
                                    Instant cancelledAt,
                                    String cancellationReason,
                                    Instant createdAt,
                                    boolean cancellable,
                                    VenueInfo venue,
                                    List<Item> items,
                                    List<StatusChange> statusHistory,
                                    Partner partner,
                                    boolean canReportIssue,
                                    Instant reportDeadline,
                                    Dispute dispute) {

    /** Bao cao dang xu ly cua don nay (2.3.85); null khi khong co. */
    public record Dispute(UUID id, String status, String category, Instant createdAt, Instant ownerResponseDeadline) {
    }

    /** Nguoi choi cung do nguoi dat chon khi dat san tu ket qua ghep cap (B5). */
    public record Partner(UUID id, String fullName) {
    }

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
