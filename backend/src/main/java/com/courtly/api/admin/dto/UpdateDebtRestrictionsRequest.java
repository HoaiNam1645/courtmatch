package com.courtly.api.admin.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

/** Cap nhat ca hai muc han che cong no qua han (2.3.40). Phai gui du muc 1 va muc 2. */
public record UpdateDebtRestrictionsRequest(@NotEmpty(message = "Vui long gui du hai muc han che") List<@Valid Level> levels) {

    /**
     * @param minOverdueAmount null neu khong dung nguong so tien
     * @param minOverdueDays   null neu khong dung nguong so ngay
     */
    public record Level(@NotNull(message = "Vui long chon muc") Integer level, boolean enabled,
                        BigDecimal minOverdueAmount, Integer minOverdueDays) {
    }
}
