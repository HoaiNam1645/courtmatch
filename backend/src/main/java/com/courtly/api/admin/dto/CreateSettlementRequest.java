package com.courtly.api.admin.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/** 2.3.45 - chot ky: gom hoa hong chua doi soat cua mot chu san trong khoang ngay thanh mot ky. */
public record CreateSettlementRequest(
        @NotNull(message = "Chu san la bat buoc") UUID ownerId,
        @NotNull(message = "Ngay bat dau la bat buoc") LocalDate periodFrom,
        @NotNull(message = "Ngay ket thuc la bat buoc") LocalDate periodTo,
        @Size(max = 500, message = "Ghi chu toi da 500 ky tu") String note) {
}
