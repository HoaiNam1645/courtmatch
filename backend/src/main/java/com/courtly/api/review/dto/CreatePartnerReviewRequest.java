package com.courtly.api.review.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreatePartnerReviewRequest(
        @NotNull(message = "Khong duoc de trong") UUID matchId,
        @NotNull(message = "Khong duoc de trong") UUID reviewedUserId,
        @NotNull(message = "Khong duoc de trong")
        @Min(value = 1, message = "Diem sao phai tu 1 den 5")
        @Max(value = 5, message = "Diem sao phai tu 1 den 5") Integer rating,
        @Size(max = 1000, message = "Nhan xet toi da 1000 ky tu") String comment) {
}
