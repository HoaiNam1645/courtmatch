package com.courtly.api.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Bam "Gui lai ma" o man hinh nhap ma xac minh (2.1.1). */
public record ResendVerificationRequest(

        @NotBlank(message = "Thieu email da dang ky")
        @Size(max = 255, message = "Email toi da 255 ky tu")
        String email) {
}
