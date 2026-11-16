package com.courtly.api.admin.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** 2.3.52 - ghi nhan chu san da hoan tien truc tiep cho nguoi choi: so tien hoan va ly do bat buoc. */
public record RecordRefundRequest(
        @NotNull(message = "So tien hoan la bat buoc")
        @DecimalMin(value = "1", message = "So tien hoan toi thieu 1 dong")
        @Digits(integer = 10, fraction = 0, message = "So tien phai la so nguyen dong") BigDecimal amount,
        @NotBlank(message = "Ly do hoan tien la bat buoc")
        @Size(min = 5, max = 500, message = "Ly do tu 5 den 500 ky tu") String reason) {
}
