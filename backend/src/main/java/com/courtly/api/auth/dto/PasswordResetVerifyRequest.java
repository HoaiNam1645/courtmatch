package com.courtly.api.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Buoc 2 cua 2.1.5: doi ma xac minh lay token dat mat khau. */
public record PasswordResetVerifyRequest(

        @NotBlank(message = "Thieu email da nhap o buoc truoc")
        @Size(max = 255, message = "Email toi da 255 ky tu")
        String destination,

        @NotBlank(message = "Vui long nhap ma xac minh")
        @Pattern(regexp = "^\\d{6}$", message = "Ma xac minh gom 6 chu so")
        String code) {
}
