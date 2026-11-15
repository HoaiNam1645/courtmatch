package com.courtly.api.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Sua ten, loai phi hoac gia tri cua mot chinh sach (2.3.40); cung quy tac kiem tra voi 2.3.39. */
public record UpdateFeePolicyRequest(
        @NotBlank(message = "Vui long nhap ten chinh sach") @Size(min = 2, max = 150, message = "Ten chinh sach tu 2 den 150 ky tu") String name,
        @NotBlank(message = "Vui long chon loai phi") String feeType,
        @NotNull(message = "Vui long nhap gia tri phi") BigDecimal feeValue) {
}
