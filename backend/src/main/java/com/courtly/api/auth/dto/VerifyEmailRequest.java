package com.courtly.api.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Nhap ma xac minh email sau khi dang ky (2.1.1). */
public record VerifyEmailRequest(

        @NotBlank(message = "Thieu email da dang ky")
        @Size(max = 255, message = "Email toi da 255 ky tu")
        String email,

        @NotBlank(message = "Vui long nhap ma xac minh")
        @Pattern(regexp = "^\\d{6}$", message = "Ma xac minh gom 6 chu so")
        String code) {
}
