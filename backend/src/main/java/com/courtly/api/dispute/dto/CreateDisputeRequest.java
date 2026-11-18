package com.courtly.api.dispute.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Noi dung bao cao ve don dat san (2.3.85, 2.3.86). */
public record CreateDisputeRequest(
        @NotBlank(message = "Vui long chon loai bao cao") String category,
        @NotBlank(message = "Vui long mo ta van de")
        @Size(max = 2000, message = "Mo ta toi da 2000 ky tu") String description) {
}
