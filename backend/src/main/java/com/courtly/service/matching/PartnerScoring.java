package com.courtly.service.matching;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

/** Cong thuc diem tong the baseline cua mot goi y doi tac (trong so khop chia doi 2.3.65). */
final class PartnerScoring {

    static final double SKILL = 0.4;
    static final double STYLE = 0.2;
    static final double TIME = 0.2;
    static final double LOCATION = 0.2;

    private PartnerScoring() {
    }

    static Map<String, Object> weights() {
        Map<String, Object> weights = new LinkedHashMap<>();
        weights.put("skill", SKILL);
        weights.put("style", STYLE);
        weights.put("time", TIME);
        weights.put("location", LOCATION);
        return weights;
    }

    /** Trung binh co trong so cua cac diem thanh phan, thanh phan null bi bo va trong so duoc chuan hoa lai. */
    static BigDecimal total(BigDecimal skill, BigDecimal style, BigDecimal time, BigDecimal location) {
        double sum = 0;
        double weight = 0;
        if (skill != null) { sum += SKILL * skill.doubleValue(); weight += SKILL; }
        if (style != null) { sum += STYLE * style.doubleValue(); weight += STYLE; }
        if (time != null) { sum += TIME * time.doubleValue(); weight += TIME; }
        if (location != null) { sum += LOCATION * location.doubleValue(); weight += LOCATION; }
        if (weight == 0) return BigDecimal.ZERO.setScale(2);
        return BigDecimal.valueOf(sum / weight).setScale(2, RoundingMode.HALF_UP);
    }
}
