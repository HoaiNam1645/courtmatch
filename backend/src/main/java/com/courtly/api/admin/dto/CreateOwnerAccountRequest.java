package com.courtly.api.admin.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 2.3.33 - cap tai khoan chu san: ten, email dang nhap va so dien thoai lien he. */
public record CreateOwnerAccountRequest(
        @NotBlank(message = "Vui long nhap ho ten")
        @Size(min = 2, max = 150, message = "Ho ten tu 2 den 150 ky tu")
        String fullName,

        @NotBlank(message = "Vui long nhap email")
        @Email(message = "Email khong dung dinh dang")
        @Size(max = 255, message = "Email toi da 255 ky tu")
        String email,

        @NotBlank(message = "Vui long nhap so dien thoai")
        @Size(max = 20, message = "So dien thoai toi da 20 ky tu")
        String phone) {

    public CreateOwnerAccountRequest {
        fullName = fullName == null ? null : fullName.trim();
        email = email == null ? null : email.trim();
        phone = phone == null ? null : phone.trim();
    }
}
