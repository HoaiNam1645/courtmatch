package com.courtly.service.owner;

import com.courtly.api.owner.dto.CommissionListResponse;
import com.courtly.api.owner.dto.NetIncomeResponse;
import com.courtly.common.exception.ApiException;
import com.courtly.common.exception.ErrorCode;
import com.courtly.domain.payment.PaymentRepository;
import com.courtly.domain.venue.VenueRepository;
import com.courtly.service.booking.AvailabilityService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 2.3.19 - chu san xem so tien thuc nhan cua tung booking. */
@Service
@RequiredArgsConstructor
public class OwnerNetIncomeService {

    private final OwnedVenueLookup ownedVenueLookup;
    private final VenueRepository venueRepository;
    private final PaymentRepository paymentRepository;
    private final OwnerCommissionService commissionService;

    /**
     * @param from ngay xac nhan thu tien (gio Viet Nam), tinh ca ngay nay; null = 29 ngay truoc ngay ket thuc
     * @param to   null = hom nay
     */
    @Transactional(readOnly = true)
    public NetIncomeResponse list(UUID ownerId, UUID venueId, LocalDate from, LocalDate to, int page, int size) {
        LocalDate end = to == null ? LocalDate.now(AvailabilityService.ZONE) : to;
        LocalDate start = from == null ? end.minusDays(OwnerRevenueService.DEFAULT_DAYS - 1L) : from;
        if (end.isBefore(start)) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "Khoang ngay khong hop le.",
                    Map.of("to", "Ngay ket thuc phai sau hoac bang ngay bat dau"));
        }
        if (ChronoUnit.DAYS.between(start, end) + 1 > OwnerRevenueService.MAX_DAYS) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "Chi xem toi da " + OwnerRevenueService.MAX_DAYS + " ngay moi lan.",
                    Map.of("to", "Khoang ngay toi da " + OwnerRevenueService.MAX_DAYS + " ngay"));
        }

        List<UUID> venueIds;
        if (venueId == null) {
            venueIds = venueRepository.findIdsByOwnerId(ownerId);
        } else {
            ownedVenueLookup.requireVenue(ownerId, venueId);
            venueIds = List.of(venueId);
        }
        if (venueIds.isEmpty()) {
            return new NetIncomeResponse(start, end, List.of(), page, size, 0, 0,
                    new NetIncomeResponse.Totals(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0));
        }

        // Muc phi uoc tinh cho booking chua ghi nhan hoa hong; khong co chinh sach thi coi la 0.
        CommissionListResponse.Policy policy = commissionService.activePolicy();
        String feeType = policy == null ? "fixed" : policy.feeType();
        BigDecimal feeValue = policy == null ? BigDecimal.ZERO : policy.feeValue();

        Instant fromTime = start.atStartOfDay(AvailabilityService.ZONE).toInstant();
        Instant toTime = end.plusDays(1).atStartOfDay(AvailabilityService.ZONE).toInstant();
        Page<PaymentRepository.NetIncomeRow> rows = paymentRepository.netIncome(
                venueIds, fromTime, toTime, feeType, feeValue, PageRequest.of(page, size));
        PaymentRepository.NetIncomeTotals totals = paymentRepository.netIncomeTotals(venueIds, fromTime, toTime, feeType, feeValue);

        return new NetIncomeResponse(start, end,
                rows.getContent().stream().map(OwnerNetIncomeService::toItem).toList(),
                rows.getNumber(), rows.getSize(), rows.getTotalElements(), rows.getTotalPages(),
                new NetIncomeResponse.Totals(totals.getGross(), totals.getCommission(), totals.getRefunded(),
                        totals.getPendingRefund(), net(totals.getGross(), totals.getCommission(), totals.getRefunded()),
                        totals.getBookings()));
    }

    /** So thuc nhan = doanh thu gop - hoa hong - tien da hoan; khong am. */
    static BigDecimal net(BigDecimal gross, BigDecimal commission, BigDecimal refunded) {
        return gross.subtract(commission).subtract(refunded);
    }

    private static NetIncomeResponse.Item toItem(PaymentRepository.NetIncomeRow row) {
        return new NetIncomeResponse.Item(
                row.getPaymentId(), row.getBookingId(), row.getBookingCode(), row.getBookingStatus(),
                row.getVenueId(), row.getVenueName(), row.getPlayerName(), row.getConfirmedAt(),
                row.getGrossAmount(), row.getCommissionAmount(), Boolean.TRUE.equals(row.getCommissionEstimated()),
                row.getCommissionStatus(), row.getRefundedAmount(), row.getPendingRefundAmount(),
                net(row.getGrossAmount(), row.getCommissionAmount(), row.getRefundedAmount()));
    }
}
