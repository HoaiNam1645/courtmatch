package com.courtly.service.voucher;

import java.time.LocalDate;
import java.util.Set;

/** Cau noi de test cac ham tinh tuan cua {@link VoucherEligibilityService} tu goi khac. */
public final class VoucherEligibilityServiceAccess {

    private VoucherEligibilityServiceAccess() {
    }

    public static LocalDate weekStart(LocalDate day) {
        return VoucherEligibilityService.weekStart(day);
    }

    public static int streak(Set<LocalDate> weeks, LocalDate today) {
        return VoucherEligibilityService.streak(weeks, today);
    }
}
