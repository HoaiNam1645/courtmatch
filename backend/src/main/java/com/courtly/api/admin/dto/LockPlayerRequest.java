package com.courtly.api.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 2.3.31 - khoa nguoi choi, bat buoc co ly do. */
public record LockPlayerRequest(
        @NotBlank(message = "Vui long nhap ly do khoa")
        @Size(min = 3, max = 500, message = "Ly do tu 3 den 500 ky tu")
        String reason) {

    public LockPlayerRequest {
        reason = reason == null ? null : reason.trim();
    }
}
