package com.courtly.service.booking;

import com.courtly.common.enums.ActiveStatus;
import com.courtly.domain.venue.CourtPriceRule;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Tinh tien mot khung dat san tu court_price_rules (2.1.27).
 *
 * <p>Mot lan dat 90 phut co the nam vat qua hai khung gia, vi du 100.000d/gio den 16:00
 * roi 140.000d/gio sau do. Lop nay cong tien theo tung doan thay vi lay gia tai gio bat dau
 * roi nhan len - cach do se tinh sai.
 *
 * <p>Gia luon tinh o server. Khong bao gio nhan so tien do client gui len.
 */
public final class PricingCalculator {

    private PricingCalculator() {
    }

    /**
     * @param rules     bang gia cua san con
     * @param date      ngay choi, dung de chon rule theo thu va theo thoi gian hieu luc
     * @param startTime gio bat dau
     * @param endTime   gio ket thuc, phai sau gio bat dau
     * @return tong tien, hoac rong neu co doan thoi gian khong co gia
     */
    public static Optional<BigDecimal> calculate(List<CourtPriceRule> rules,
                                                 LocalDate date,
                                                 LocalTime startTime,
                                                 LocalTime endTime) {
        if (!endTime.isAfter(startTime)) {
            return Optional.empty();
        }

        List<CourtPriceRule> applicable = effectiveRules(rules, date);
        BigDecimal total = BigDecimal.ZERO;
        LocalTime cursor = startTime;

        while (cursor.isBefore(endTime)) {
            LocalTime segmentStart = cursor;
            Optional<CourtPriceRule> rule = applicable.stream()
                    .filter(item -> !segmentStart.isBefore(item.getStartTime())
                            && segmentStart.isBefore(item.getEndTime()))
                    .findFirst();

            if (rule.isEmpty()) {
                // Co doan gio khong khai bao gia - khong doan bua, bao khong tinh duoc.
                return Optional.empty();
            }

            LocalTime segmentEnd = min(rule.get().getEndTime(), endTime);
            long minutes = java.time.Duration.between(segmentStart, segmentEnd).toMinutes();
            total = total.add(rule.get().getPricePerHour()
                    .multiply(BigDecimal.valueOf(minutes))
                    .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP));

            cursor = segmentEnd;
        }

        return Optional.of(total.setScale(0, RoundingMode.HALF_UP));
    }

    /** Rule dang hieu luc cho ngay va thu tuong ung, sap theo gio bat dau. */
    public static List<CourtPriceRule> effectiveRules(List<CourtPriceRule> rules, LocalDate date) {
        short dayOfWeek = (short) date.getDayOfWeek().getValue();
        return rules.stream()
                .filter(rule -> rule.getStatus() == ActiveStatus.ACTIVE)
                .filter(rule -> rule.getDayOfWeek() == null || rule.getDayOfWeek() == dayOfWeek)
                .filter(rule -> !date.isBefore(rule.getEffectiveFrom()))
                .filter(rule -> rule.getEffectiveTo() == null || !date.isAfter(rule.getEffectiveTo()))
                // Rule khai bao cho dung thu duoc uu tien hon rule ap dung moi ngay.
                .sorted(Comparator.comparing(CourtPriceRule::getStartTime)
                        .thenComparing(rule -> rule.getDayOfWeek() == null ? 1 : 0))
                .toList();
    }

    private static LocalTime min(LocalTime left, LocalTime right) {
        return left.isBefore(right) ? left : right;
    }
}
