package com.courtly.api.owner.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 2.3.27 - ly do chu san chua nhan duoc tien nguoi choi bao da tra. */
public record RejectOwnerPaymentRequest(
        @NotBlank(message = "Vui long nhap ly do tu choi")
        @Size(min = 3, max = 500, message = "Ly do tu 3 den 500 ky tu")
        String reason) {

    public RejectOwnerPaymentRequest {
        reason = reason == null ? null : reason.trim();
    }
}
