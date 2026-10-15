package com.courtly.api.payment.dto;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ReportPaymentRequest(
        @NotBlank(message = "Vui long chon phuong thuc thanh toan")
        @Pattern(regexp = "cash|owner_bank_transfer", message = "Phuong thuc thanh toan khong hop le")
        String method,
        @Size(max = 1000, message = "Duong dan bang chung toi da 1000 ky tu") String proofUrl) {
}
