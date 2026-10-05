package com.courtly.api.profile.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Khu vuc choi uu tien (2.1.11).
 *
 * @param id        chi co trong response; request bo qua vi moi lan luu la thay toan bo danh sach
 * @param latitude  chua dung o giao dien hien tai, de san cho buoc ghep cap theo khoang cach
 */
public record PreferredLocationDto(

        UUID id,

        @NotBlank(message = "Vui long nhap ten dia diem")
        @Size(max = 100, message = "Ten dia diem toi da 100 ky tu")
        String label,

        @Size(max = 500, message = "Dia chi toi da 500 ky tu")
        String address,

        @DecimalMin(value = "-90.0", message = "Vi do khong hop le")
        @DecimalMax(value = "90.0", message = "Vi do khong hop le")
        BigDecimal latitude,

        @DecimalMin(value = "-180.0", message = "Kinh do khong hop le")
        @DecimalMax(value = "180.0", message = "Kinh do khong hop le")
        BigDecimal longitude,

        @NotNull(message = "Thieu ban kinh")
        @DecimalMin(value = "1", message = "Ban kinh tu 1 den 30 km")
        @DecimalMax(value = "30", message = "Ban kinh tu 1 den 30 km")
        BigDecimal radiusKm,

        boolean isDefault) {
}
