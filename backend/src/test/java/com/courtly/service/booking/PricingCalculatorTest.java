package com.courtly.service.booking;

import static org.assertj.core.api.Assertions.assertThat;

import com.courtly.common.enums.ActiveStatus;
import com.courtly.domain.venue.CourtPriceRule;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Kiem thu cach cong tien theo tung doan gia (2.1.27). */
class PricingCalculatorTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 9, 28);

    /** Gia san mau: truoc 16:00 la 100k, tu 16:00 den 23:00 la 140k. */
    private static List<CourtPriceRule> twoBandRules() {
        return List.of(
                rule(null, LocalTime.of(5, 0), LocalTime.of(16, 0), 100_000),
                rule(null, LocalTime.of(16, 0), LocalTime.of(23, 0), 140_000));
    }

    private static CourtPriceRule rule(Short dayOfWeek, LocalTime start, LocalTime end, long price) {
        CourtPriceRule item = new CourtPriceRule();
        item.setDayOfWeek(dayOfWeek);
        item.setStartTime(start);
        item.setEndTime(end);
        item.setPricePerHour(BigDecimal.valueOf(price));
        item.setStatus(ActiveStatus.ACTIVE);
        item.setEffectiveFrom(LocalDate.of(2020, 1, 1));
        return item;
    }

    @Test
    @DisplayName("Mot gio tron trong mot khung gia")
    void singleBandOneHour() {
        Optional<BigDecimal> total = PricingCalculator.calculate(
                twoBandRules(), MONDAY, LocalTime.of(14, 0), LocalTime.of(15, 0));

        assertThat(total).contains(BigDecimal.valueOf(100_000));
    }

    @Test
    @DisplayName("90 phut nam tron trong khung gia cao")
    void singleBandNinetyMinutes() {
        Optional<BigDecimal> total = PricingCalculator.calculate(
                twoBandRules(), MONDAY, LocalTime.of(17, 0), LocalTime.of(18, 30));

        // 140.000 x 1,5 gio
        assertThat(total).contains(BigDecimal.valueOf(210_000));
    }

    @Test
    @DisplayName("90 phut vat qua hai khung gia thi cong theo tung doan")
    void spansTwoBands() {
        Optional<BigDecimal> total = PricingCalculator.calculate(
                twoBandRules(), MONDAY, LocalTime.of(15, 0), LocalTime.of(16, 30));

        // 100.000 x 1 gio + 140.000 x 0,5 gio
        assertThat(total).contains(BigDecimal.valueOf(170_000));
    }

    @Test
    @DisplayName("120 phut vat qua hai khung gia")
    void spansTwoBandsTwoHours() {
        Optional<BigDecimal> total = PricingCalculator.calculate(
                twoBandRules(), MONDAY, LocalTime.of(15, 0), LocalTime.of(17, 0));

        // 100.000 + 140.000
        assertThat(total).contains(BigDecimal.valueOf(240_000));
    }

    @Test
    @DisplayName("Co doan gio khong khai bao gia thi khong tinh, khong doan bua")
    void returnsEmptyWhenPriceMissing() {
        List<CourtPriceRule> rules = List.of(
                rule(null, LocalTime.of(5, 0), LocalTime.of(16, 0), 100_000));

        Optional<BigDecimal> total = PricingCalculator.calculate(
                rules, MONDAY, LocalTime.of(15, 0), LocalTime.of(17, 0));

        assertThat(total).isEmpty();
    }

    @Test
    @DisplayName("Rule khai bao cho dung thu duoc uu tien hon rule ap dung moi ngay")
    void daySpecificRuleWins() {
        List<CourtPriceRule> rules = List.of(
                rule(null, LocalTime.of(5, 0), LocalTime.of(23, 0), 100_000),
                rule((short) 1, LocalTime.of(5, 0), LocalTime.of(23, 0), 180_000));

        Optional<BigDecimal> total = PricingCalculator.calculate(
                rules, MONDAY, LocalTime.of(10, 0), LocalTime.of(11, 0));

        assertThat(total).contains(BigDecimal.valueOf(180_000));
    }

    @Test
    @DisplayName("Rule het hieu luc thi khong duoc dung")
    void ignoresExpiredRule() {
        CourtPriceRule expired = rule(null, LocalTime.of(5, 0), LocalTime.of(23, 0), 100_000);
        expired.setEffectiveTo(LocalDate.of(2020, 12, 31));

        Optional<BigDecimal> total = PricingCalculator.calculate(
                List.of(expired), MONDAY, LocalTime.of(10, 0), LocalTime.of(11, 0));

        assertThat(total).isEmpty();
    }

    @Test
    @DisplayName("Gio ket thuc khong sau gio bat dau thi khong tinh")
    void rejectsInvertedRange() {
        assertThat(PricingCalculator.calculate(
                twoBandRules(), MONDAY, LocalTime.of(15, 0), LocalTime.of(15, 0))).isEmpty();
    }
}
