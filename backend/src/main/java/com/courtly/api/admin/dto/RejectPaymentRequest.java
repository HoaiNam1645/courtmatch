package com.courtly.api.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 2.3.48 - tu choi hoac yeu cau bo sung bang chung; ly do bat buoc de chu san biet can sua gi. */
public record RejectPaymentRequest(
        @NotBlank(message = "Ly do tu choi la bat buoc")
        @Size(min = 5, max = 500, message = "Ly do tu 5 den 500 ky tu") String reason) {
}
