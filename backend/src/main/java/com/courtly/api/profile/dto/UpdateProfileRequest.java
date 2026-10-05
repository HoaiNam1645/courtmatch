package com.courtly.api.profile.dto;

import com.courtly.common.enums.Gender;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Chinh sua ho so nguoi choi (2.1.7) - man hinh /profile/edit.
 *
 * <p>Gom thong tin o bang users (ho ten, email, so dien thoai) va thong tin ca nhan
 * o bang player_profiles (gioi tinh, ngay sinh, gioi thieu).
 */
public record UpdateProfileRequest(

        @NotBlank(message = "Vui long nhap ho va ten")
        @Size(min = 2, max = 150, message = "Ho va ten tu 2 den 150 ky tu")
        String fullName,

        @Email(message = "Email khong dung dinh dang")
        @Size(max = 255, message = "Email toi da 255 ky tu")
        String email,

        @Pattern(regexp = "^$|^[0-9+ .\\-()]{9,20}$", message = "So dien thoai khong dung dinh dang")
        String phone,

        Gender gender,

        @Past(message = "Ngay sinh phai o qua khu")
        LocalDate dateOfBirth,

        // Giao dien /profile/edit gioi han o textarea 240 ky tu.
        @Size(max = 240, message = "Gioi thieu toi da 240 ky tu")
        String bio) {

    public UpdateProfileRequest {
        fullName = trimToNull(fullName);
        email = trimToNull(email);
        phone = trimToNull(phone);
        bio = trimToNull(bio);
    }

    @JsonIgnore
    @AssertTrue(message = "Vui long nhap email hoac so dien thoai")
    public boolean isIdentityProvided() {
        return email != null || phone != null;
    }

    @JsonIgnore
    @AssertTrue(message = "Nguoi choi phai tu 10 tuoi tro len")
    public boolean isAgeReasonable() {
        if (dateOfBirth == null) {
            return true;
        }
        LocalDate today = LocalDate.now();
        return !dateOfBirth.isAfter(today.minusYears(10)) && dateOfBirth.isAfter(today.minusYears(100));
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
