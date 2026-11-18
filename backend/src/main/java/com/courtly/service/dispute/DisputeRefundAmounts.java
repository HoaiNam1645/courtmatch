package com.courtly.service.dispute;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * So tien hoan cua quyet dinh cua quan tri vien (2.3.100-101), tinh tren so tien nguoi choi da yeu cau.
 *
 * <p>Toan phan = dung so da yeu cau. Mot nua = 50% so da yeu cau, lam tron xuong boi so 1.000d va toi thieu 1.000d.
 * Ca hai khong vuot so da thu con co the hoan.
 */
public final class DisputeRefundAmounts {

    private static final BigDecimal STEP = BigDecimal.valueOf(1000);

    private DisputeRefundAmounts() {
    }

    public static BigDecimal full(BigDecimal requested, BigDecimal refundable) {
        return requested.min(refundable);
    }

    public static BigDecimal half(BigDecimal requested, BigDecimal refundable) {
        BigDecimal half = requested.divide(BigDecimal.valueOf(2), 0, RoundingMode.DOWN).divide(STEP, 0, RoundingMode.DOWN).multiply(STEP);
        return half.max(STEP).min(refundable);
    }
}
