package com.courtly.api.owner.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 2.3.22 - lich su doi soat hoa hong cua mot booking.
 *
 * <p>Dung lai tu hoa hong cua booking va ky doi soat da gan (khong co bang lich su rieng). Booking
 * chua hoan thanh thi chua co hoa hong: {@code commission} la null va {@code events} rong.
 *
 * @param events theo thu tu thoi gian tang dan
 */
public record BookingReconciliationResponse(UUID bookingId, String bookingCode, String bookingStatus,
                                            Commission commission, Settlement settlement, List<Event> events) {

    /** @param status pending, due, suspended, adjusted, waived, settled; overdue khi chua doi soat va da qua han */
    public record Commission(BigDecimal grossAmount, BigDecimal commissionAmount, String status, boolean overdue,
                             Instant dueAt) {
    }

    public record Settlement(UUID id, LocalDate periodFrom, LocalDate periodTo, String status) {
    }

    /**
     * @param type recorded, assigned_to_settlement, suspended, adjusted, waived, settled
     * @param note thong tin them (vd ky doi soat); co the null
     */
    public record Event(String type, String label, Instant at, BigDecimal amount, String note) {
    }
}
