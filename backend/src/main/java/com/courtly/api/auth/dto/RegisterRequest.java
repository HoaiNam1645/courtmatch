package com.courtly.api.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Du lieu dang ky tai khoan (2.1.1).
 *
 * <p>Email la bat buoc vi tai khoan moi phai xac minh qua ma gui ve email truoc khi
 * dang nhap duoc. So dien thoai van tuy chon. Rang buoc {@code users_identity_check} cua
 * database (phai co email hoac phone) van duoc thoa man.
 */
public record RegisterRequest(

        @NotBlank(message = "Vui long nhap ho va ten")
        @Size(min = 2, max = 150, message = "Ho va ten tu 2 den 150 ky tu")
        String fullName,

        // Bat buoc tu khi co xac minh email (2.1.1): khong co email thi khong gui duoc ma.
        @NotBlank(message = "Vui long nhap email")
        @Email(message = "Email khong dung dinh dang")
        @Size(max = 255, message = "Email toi da 255 ky tu")
        String email,

        // Kiem tra long o day; chuan hoa va kiem tra chat o AuthService.
        @Pattern(regexp = "^$|^[0-9+ .\\-()]{9,20}$", message = "So dien thoai khong dung dinh dang")
        String phone,

        @NotBlank(message = "Vui long nhap mat khau")
        @Size(min = 8, max = 72, message = "Mat khau tu 8 den 72 ky tu")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
                message = "Mat khau phai co it nhat mot chu cai va mot chu so")
        String password,

        @NotBlank(message = "Vui long nhap lai mat khau")
        String confirmPassword) {

    /** Chuan hoa chuoi rong thanh null de khop cot nullable trong database. */
    public RegisterRequest {
        fullName = trimToNull(fullName);
        email = trimToNull(email);
        phone = trimToNull(phone);
    }

    @JsonIgnore
    @AssertTrue(message = "Mat khau nhap lai khong khop")
    public boolean isPasswordConfirmed() {
        return password != null && password.equals(confirmPassword);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
