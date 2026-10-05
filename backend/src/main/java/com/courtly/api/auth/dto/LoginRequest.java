package com.courtly.api.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Du lieu dang nhap (2.1.2).
 *
 * @param emailOrPhone giao dien /login co hai tab, gui email hoac so dien thoai vao cung truong nay
 */
public record LoginRequest(

        @NotBlank(message = "Vui long nhap email hoac so dien thoai")
        @Size(max = 255, message = "Gia tri qua dai")
        String emailOrPhone,

        @NotBlank(message = "Vui long nhap mat khau")
        @Size(max = 72, message = "Mat khau toi da 72 ky tu")
        String password) {

    public LoginRequest {
        emailOrPhone = emailOrPhone == null ? null : emailOrPhone.trim();
    }
}
