package com.courtly.api.owner.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/**
 * 2.3.3 - them san cau long moi. San moi luon cho quan tri vien duyet.
 *
 * <p>Gio hoat dong (2.3.7), bang gia (2.3.5) va hinh anh (2.3.6) duoc khai bao o cac buoc
 * rieng sau khi san da duoc tao.
 */
public record CreateVenueRequest(

        @NotBlank(message = "Vui long nhap ten san")
        @Size(min = 3, max = 200, message = "Ten san tu 3 den 200 ky tu")
        String name,

        @Size(max = 2000, message = "Mo ta toi da 2000 ky tu")
        String description,

        @NotBlank(message = "Vui long nhap dia chi")
        @Size(max = 300, message = "Dia chi toi da 300 ky tu")
        String address,

        @Size(max = 100, message = "Phuong/xa toi da 100 ky tu")
        String ward,

        @NotBlank(message = "Vui long chon quan/huyen")
        String district,

        @NotNull(message = "Vui long chon vi tri san tren ban do")
        BigDecimal latitude,

        @NotNull(message = "Vui long chon vi tri san tren ban do")
        BigDecimal longitude,

        @NotBlank(message = "Vui long nhap so dien thoai cua san")
        @Pattern(regexp = "^[0-9+ .\\-()]{8,20}$", message = "So dien thoai khong dung dinh dang")
        String phone,

        @Email(message = "Email khong dung dinh dang")
        @Size(max = 255, message = "Email toi da 255 ky tu")
        String email,

        @Valid
        @Size(max = 20, message = "Toi da 20 tien ich")
        List<VenueServiceInput> services,

        @NotEmpty(message = "San phai co it nhat mot san con")
        @Size(max = 30, message = "Toi da 30 san con")
        List<@Valid CourtInput> courts) {

    public CreateVenueRequest {
        name = trimToNull(name);
        description = trimToNull(description);
        address = trimToNull(address);
        ward = trimToNull(ward);
        district = trimToNull(district);
        phone = trimToNull(phone);
        email = trimToNull(email);
        services = services == null ? List.of() : services;
    }

    static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
