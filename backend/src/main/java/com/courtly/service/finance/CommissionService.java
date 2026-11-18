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
    private final com.courtly.domain.payment.BookingDisputeRepository disputeRepository;
    private final com.courtly.service.dispute.DisputeCommissionService disputeCommissionService;

    /** Cong thuc hoa hong duy nhat: dung cho ca ghi nhan that va uoc tinh hien o man hinh chu san (2.3.18). */
    public static BigDecimal commissionFor(PlatformFeeConfig config, BigDecimal grossAmount) {
        return config.getFeeType() == FeeType.PERCENTAGE
                ? grossAmount.multiply(config.getFeeValue()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                : config.getFeeValue().setScale(2, RoundingMode.HALF_UP);
    }

    public void createForCompletedBooking(Booking booking, Instant now) {
        if (commissionRepository.findByBookingId(booking.getId()).isPresent()) {
            return;
        }
        PlatformFeeConfig config = configRepository
                .findFirstByStatusOrderByEffectiveFromDesc(ActiveStatus.ACTIVE)
                .orElseThrow(() -> new IllegalStateException("Chua co chinh sach hoa hong dang active."));

        // Hoa hong tinh tren gia goc. Phan voucher Courtly chiu duoc tru vao cong no cua chu san; phan vuot hoa hong
        // la khoan Courtly con no chu san (courtlyOwed).
        BigDecimal baseCommission = commissionFor(config, booking.getTotalAmount());
        BigDecimal discount = booking.getDiscountAmount();
        BigDecimal commissionAmount = baseCommission.subtract(discount).max(BigDecimal.ZERO);
        BigDecimal courtlyOwed = discount.subtract(baseCommission).max(BigDecimal.ZERO);

        BookingCommission commission = new BookingCommission();
        commission.setBooking(booking);
        commission.setOwner(booking.getVenue().getOwner());
        commission.setFeeConfig(config);
        commission.setGrossAmount(booking.getTotalAmount());
        commission.setFeeType(config.getFeeType());
        commission.setFeeValue(config.getFeeValue());
        commission.setCommissionAmount(commissionAmount);
        commission.setVoucherDiscount(discount);
        commission.setCourtlyOwed(courtlyOwed);
        commission.setStatus("due");
        commission.setCalculatedAt(now);
        commission.setDueAt(now.plus(7, ChronoUnit.DAYS));
        commissionRepository.saveAndFlush(commission);
        // Don dang co bao cao (nguoi choi bao cao khi don con o confirmed): hoa hong sinh ra o trang thai tam hoan (2.3.105).
        disputeRepository.findActiveByBooking(booking.getId())
                .ifPresent(dispute -> disputeCommissionService.hold(booking.getId(), dispute.getId()));
    }
}
