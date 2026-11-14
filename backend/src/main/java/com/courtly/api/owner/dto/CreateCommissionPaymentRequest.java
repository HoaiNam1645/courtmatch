package com.courtly.api.owner.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * 2.3.24 - chu san gui thong tin thanh toan cong no hoa hong cho Courtly.
 *
 * @param settlementId ky doi soat dang tra; null = tra chung vao cong no hien tai
 * @param proofUrl     duong dan anh chung tu chuyen khoan, co the bo trong
 */
public record CreateCommissionPaymentRequest(
        UUID settlementId,

        @NotNull(message = "Vui long nhap so tien da thanh toan")
        @DecimalMin(value = "1", message = "So tien phai lon hon 0")
        @DecimalMax(value = "999999999", message = "So tien toi da 999.999.999 dong")
        @Digits(integer = 9, fraction = 2, message = "So tien khong hop le")
        BigDecimal amount,

        @NotNull(message = "Vui long nhap ngay thanh toan")
        LocalDate paidOn,

        @NotBlank(message = "Vui long nhap ma tham chieu giao dich")
        @Size(min = 3, max = 100, message = "Ma tham chieu tu 3 den 100 ky tu")
        String referenceCode,

        @Size(max = 1000, message = "Duong dan bang chung toi da 1000 ky tu")
        @Pattern(regexp = "^$|^https?://\\S+$", message = "Duong dan bang chung phai bat dau bang http:// hoac https://")
        String proofUrl,

        @Size(max = 500, message = "Ghi chu toi da 500 ky tu")
        String note) {

    public CreateCommissionPaymentRequest {
        referenceCode = referenceCode == null ? null : referenceCode.trim();
        proofUrl = proofUrl == null || proofUrl.isBlank() ? null : proofUrl.trim();
        note = note == null || note.isBlank() ? null : note.trim();
    }
}
