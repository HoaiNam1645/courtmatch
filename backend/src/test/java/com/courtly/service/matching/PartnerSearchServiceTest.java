package com.courtly.service.matching;

import static org.assertj.core.api.Assertions.assertThat;

import com.courtly.common.enums.PlayingStyle;
import com.courtly.domain.account.PlayerAvailabilitySlot;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class PartnerSearchServiceTest {

    @Test
    void skillSimilarityUsesEloExpectedWinBalance() {
        assertThat(PartnerSearchService.skillSimilarity(
                new BigDecimal("1200"), new BigDecimal("1200")))
                .isEqualByComparingTo("100.00");
        assertThat(PartnerSearchService.skillSimilarity(
                new BigDecimal("1200"), new BigDecimal("1280")))
                .isEqualByComparingTo("77.37");
        assertThat(PartnerSearchService.skillSimilarity(
                new BigDecimal("1200"), new BigDecimal("1600")))
                .isEqualByComparingTo("18.18");
    }

    @Test
    void locationCompatibilityUsesRequesterRadius() {
        assertThat(PartnerSearchService.locationCompatibility(
                new BigDecimal("0"), new BigDecimal("10")))
                .isEqualByComparingTo("100.00");
        assertThat(PartnerSearchService.locationCompatibility(
                new BigDecimal("2"), new BigDecimal("10")))
                .isEqualByComparingTo("80.00");
        assertThat(PartnerSearchService.locationCompatibility(
                new BigDecimal("12"), new BigDecimal("10")))
                .isEqualByComparingTo("0.00");
    }

    @Test
    void timeCompatibilityMeasuresRequesterScheduleCoverageAndMergesOverlaps() {
        List<PlayerAvailabilitySlot> requester = List.of(
                slot(2, 18, 20),
                slot(2, 19, 21),
                slot(6, 8, 10));
        List<PlayerAvailabilitySlot> candidate = List.of(
                slot(2, 17, 22),
                slot(6, 9, 10));

        assertThat(PartnerSearchService.timeCompatibility(
                requester, candidate, (short) 2, LocalTime.of(18, 0), LocalTime.of(19, 30)))
                .isEqualByComparingTo("80.00");
    }

    @Test
    void styleCompatibilityHandlesSameBalancedComplementAndMissingStyles() {
        assertThat(PartnerSearchService.styleCompatibility(
                PlayingStyle.ATTACKING, PlayingStyle.ATTACKING))
                .isEqualByComparingTo("100.00");
        assertThat(PartnerSearchService.styleCompatibility(
                PlayingStyle.ATTACKING, PlayingStyle.BALANCED))
                .isEqualByComparingTo("80.00");
        assertThat(PartnerSearchService.styleCompatibility(
                PlayingStyle.ATTACKING, PlayingStyle.DEFENSIVE))
                .isEqualByComparingTo("65.00");
        assertThat(PartnerSearchService.styleCompatibility(null, PlayingStyle.DEFENSIVE))
                .isEqualByComparingTo("50.00");
    }

    private static PlayerAvailabilitySlot slot(int day, int startHour, int endHour) {
        PlayerAvailabilitySlot slot = new PlayerAvailabilitySlot();
        slot.setDayOfWeek((short) day);
        slot.setStartTime(LocalTime.of(startHour, 0));
        slot.setEndTime(LocalTime.of(endHour, 0));
        return slot;
    }
}
