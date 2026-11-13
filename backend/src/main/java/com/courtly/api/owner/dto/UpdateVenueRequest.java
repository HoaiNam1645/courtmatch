package com.courtly.api.owner.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/**
 * 2.3.4 - cap nhat thong tin chung va tien ich cua san.
 *
 * <p>San con duoc them / sua qua API rieng vi moi san con co trang thai va booking rieng.
 * Danh sach tien ich gui len thay the toan bo danh sach cu.
 */
public record UpdateVenueRequest(

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
        List<VenueServiceInput> services) {

    public UpdateVenueRequest {
        name = CreateVenueRequest.trimToNull(name);
        description = CreateVenueRequest.trimToNull(description);
        address = CreateVenueRequest.trimToNull(address);
        ward = CreateVenueRequest.trimToNull(ward);
        district = CreateVenueRequest.trimToNull(district);
        phone = CreateVenueRequest.trimToNull(phone);
        email = CreateVenueRequest.trimToNull(email);
        services = services == null ? List.of() : services;
    }
}
