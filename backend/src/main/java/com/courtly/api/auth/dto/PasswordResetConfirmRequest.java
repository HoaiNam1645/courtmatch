package com.courtly.api.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Buoc 3 cua 2.1.5: dat mat khau moi.
 *
 * <p>Rang buoc mat khau giong het {@link RegisterRequest} - hai cho cung mot quy tac
 * thi nguoi dung khong bi tu choi o mot cho ma duoc chap nhan o cho kia.
 */
public record PasswordResetConfirmRequest(

        @NotBlank(message = "Thieu token dat lai mat khau")
        @Size(max = 200, message = "Token khong hop le")
        String resetToken,

        @NotBlank(message = "Vui long nhap mat khau moi")
        @Size(min = 8, max = 72, message = "Mat khau tu 8 den 72 ky tu")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
                message = "Mat khau phai co it nhat mot chu cai va mot chu so")
        String password,

        @NotBlank(message = "Vui long nhap lai mat khau moi")
        String confirmPassword) {

    @JsonIgnore
    @AssertTrue(message = "Mat khau nhap lai khong khop")
    public boolean isPasswordConfirmed() {
        return password != null && password.equals(confirmPassword);
    }
}
