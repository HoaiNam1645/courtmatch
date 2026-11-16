package com.courtly.api.admin.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** 2.3.51 - dieu chinh hoa hong cua mot booking: so tien moi chu san phai tra va ly do bat buoc. */
public record AdjustCommissionRequest(
        @NotNull(message = "So tien moi la bat buoc")
        @DecimalMin(value = "0", message = "So tien khong duoc am")
        @Digits(integer = 10, fraction = 0, message = "So tien phai la so nguyen dong") BigDecimal newAmount,
        @NotBlank(message = "Ly do dieu chinh la bat buoc")
        @Size(min = 5, max = 500, message = "Ly do tu 5 den 500 ky tu") String reason) {
}
