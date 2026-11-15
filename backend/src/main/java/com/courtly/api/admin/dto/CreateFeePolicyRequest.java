package com.courtly.api.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Tao chinh sach phi nen tang (2.3.39). Chinh sach moi co hieu luc ngay khi tao.
 *
 * @param feeType  percentage (feeValue tu 0 den 100) hoac fixed (so tien VND moi booking)
 */
public record CreateFeePolicyRequest(
        @NotBlank(message = "Vui long nhap ten chinh sach") @Size(min = 2, max = 150, message = "Ten chinh sach tu 2 den 150 ky tu") String name,
        @NotBlank(message = "Vui long chon loai phi") String feeType,
        @NotNull(message = "Vui long nhap gia tri phi") BigDecimal feeValue) {
}
