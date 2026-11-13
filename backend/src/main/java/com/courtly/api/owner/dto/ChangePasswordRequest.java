package com.courtly.api.owner.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 2.3.1 - chu san doi mat khau tam do quan tri vien cap.
 *
 * <p>Quy tac mat khau moi giong het dang ky (2.1.1) va dat lai mat khau (2.1.5).
 */
public record ChangePasswordRequest(

        @NotBlank(message = "Vui long nhap mat khau hien tai")
        @Size(max = 72, message = "Mat khau toi da 72 ky tu")
        String currentPassword,

        @NotBlank(message = "Vui long nhap mat khau moi")
        @Size(min = 8, max = 72, message = "Mat khau tu 8 den 72 ky tu")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
                message = "Mat khau phai co it nhat mot chu cai va mot chu so")
        String newPassword,

        @NotBlank(message = "Vui long nhap lai mat khau moi")
        String confirmPassword) {

    @JsonIgnore
    @AssertTrue(message = "Mat khau nhap lai khong khop")
    public boolean isPasswordConfirmed() {
        return newPassword != null && newPassword.equals(confirmPassword);
    }
}
