package com.courtly.service.owner;

import com.courtly.api.owner.dto.BookingReconciliationResponse;
import com.courtly.common.exception.ApiException;
import com.courtly.domain.booking.Booking;
import com.courtly.domain.booking.BookingRepository;
import com.courtly.domain.finance.BookingCommission;
import com.courtly.domain.finance.BookingCommissionRepository;
import com.courtly.domain.finance.CommissionSettlement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 2.3.22 - chu san xem lich su doi soat hoa hong cua mot booking. */
@Service
@RequiredArgsConstructor
public class OwnerBookingReconciliationService {

    private final BookingRepository bookingRepository;
    private final BookingCommissionRepository commissionRepository;

    @Transactional(readOnly = true)
    public BookingReconciliationResponse get(UUID ownerId, UUID bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .filter(item -> item.getVenue().getOwner().getId().equals(ownerId))
                .orElseThrow(() -> ApiException.notFound("Khong tim thay don dat san."));
        BookingCommission commission = commissionRepository.findByBookingId(bookingId).orElse(null);
        if (commission == null) {
            return new BookingReconciliationResponse(booking.getId(), booking.getBookingCode(),
                    booking.getStatus().getValue(), null, null, List.of());
        }

        Instant now = Instant.now();
        CommissionSettlement settlement = commission.getSettlement();
        List<BookingReconciliationResponse.Event> events = new ArrayList<>();
        events.add(new BookingReconciliationResponse.Event("recorded", "Ghi nhan hoa hong cho booking",
                commission.getCalculatedAt(), commission.getCommissionAmount(),
                commission.getFeeConfig() == null ? null : commission.getFeeConfig().getName()));
        if (settlement != null) {
            events.add(new BookingReconciliationResponse.Event("assigned_to_settlement", "Dua vao ky doi soat",
                    settlement.getCreatedAt(), commission.getCommissionAmount(),
                    settlement.getPeriodFrom() + " - " + settlement.getPeriodTo()));
        }
        switch (commission.getStatus()) {
            case "suspended" -> events.add(new BookingReconciliationResponse.Event("suspended",
                    "Tam hoan do tranh chap", commission.getUpdatedAt(), commission.getCommissionAmount(), null));
            case "adjusted" -> events.add(new BookingReconciliationResponse.Event("adjusted",
                    "Hoa hong duoc dieu chinh", commission.getUpdatedAt(), commission.getCommissionAmount(), null));
            case "waived" -> events.add(new BookingReconciliationResponse.Event("waived",
                    "Hoa hong duoc mien", commission.getUpdatedAt(), commission.getCommissionAmount(), null));
            case "settled" -> events.add(new BookingReconciliationResponse.Event("settled", "Da doi soat xong",
                    commission.getSettledAt() != null ? commission.getSettledAt() : commission.getUpdatedAt(),
                    commission.getCommissionAmount(), null));
            default -> { }
        }
        events.sort(Comparator.comparing(BookingReconciliationResponse.Event::at));

        boolean overdue = OwnerCommissionService.OUTSTANDING.contains(commission.getStatus())
                && commission.getDueAt() != null && commission.getDueAt().isBefore(now);
        return new BookingReconciliationResponse(booking.getId(), booking.getBookingCode(),
                booking.getStatus().getValue(),
                new BookingReconciliationResponse.Commission(commission.getGrossAmount(), commission.getCommissionAmount(),
                        commission.getStatus(), overdue, commission.getDueAt()),
                settlement == null ? null : new BookingReconciliationResponse.Settlement(
                        settlement.getId(), settlement.getPeriodFrom(), settlement.getPeriodTo(), settlement.getStatus()),
                events);
    }
}
