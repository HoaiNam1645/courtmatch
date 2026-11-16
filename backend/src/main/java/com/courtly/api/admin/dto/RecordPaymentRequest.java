package com.courtly.api.admin.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * 2.3.49 - quan tri vien ghi nhan chu san da thanh toan cong no ngoai he thong. Co settlementId thi tinh vao ky do,
 * khong co thi tinh vao cong no chung (hoa hong cu nhat chua thuoc ky).
 */
public record RecordPaymentRequest(
        @NotNull(message = "Chu san la bat buoc") UUID ownerId,
        UUID settlementId,
        @NotNull(message = "So tien la bat buoc")
        @DecimalMin(value = "1", message = "So tien toi thieu 1 dong")
        @Digits(integer = 10, fraction = 0, message = "So tien phai la so nguyen dong") BigDecimal amount,
        @NotNull(message = "Ngay thanh toan la bat buoc") LocalDate paidOn,
        @NotBlank(message = "Ma tham chieu la bat buoc")
        @Size(min = 3, max = 100, message = "Ma tham chieu tu 3 den 100 ky tu") String referenceCode,
        @Size(max = 500, message = "Duong dan bang chung toi da 500 ky tu") String proofUrl,
        @Size(max = 500, message = "Ghi chu toi da 500 ky tu") String note) {
}
