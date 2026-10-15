package com.courtly.service.finance;

import com.courtly.common.enums.ActiveStatus;
import com.courtly.common.enums.FeeType;
import com.courtly.domain.booking.Booking;
import com.courtly.domain.finance.BookingCommission;
import com.courtly.domain.finance.BookingCommissionRepository;
import com.courtly.domain.finance.PlatformFeeConfig;
import com.courtly.domain.finance.PlatformFeeConfigRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Creates one immutable fee snapshot when a booking is completed. */
@Service
@RequiredArgsConstructor
public class CommissionService {

    private final PlatformFeeConfigRepository configRepository;
    private final BookingCommissionRepository commissionRepository;

    public void createForCompletedBooking(Booking booking, Instant now) {
        if (commissionRepository.findByBookingId(booking.getId()).isPresent()) {
            return;
        }
        PlatformFeeConfig config = configRepository
                .findFirstByStatusOrderByEffectiveFromDesc(ActiveStatus.ACTIVE)
                .orElseThrow(() -> new IllegalStateException("Chua co chinh sach hoa hong dang active."));

        BigDecimal commissionAmount = config.getFeeType() == FeeType.PERCENTAGE
                ? booking.getTotalAmount().multiply(config.getFeeValue())
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                : config.getFeeValue().setScale(2, RoundingMode.HALF_UP);

        BookingCommission commission = new BookingCommission();
        commission.setBooking(booking);
        commission.setOwner(booking.getVenue().getOwner());
        commission.setFeeConfig(config);
        commission.setGrossAmount(booking.getTotalAmount());
        commission.setFeeType(config.getFeeType());
        commission.setFeeValue(config.getFeeValue());
        commission.setCommissionAmount(commissionAmount);
        commission.setStatus("due");
        commission.setCalculatedAt(now);
        commission.setDueAt(now.plus(7, ChronoUnit.DAYS));
        commissionRepository.save(commission);
    }
}
