package com.courtly.api.dispute.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Noi dung bo sung thong tin cho bao cao dang xu ly (2.3.91). */
public record SupplementRequest(
        @NotBlank(message = "Vui long nhap noi dung bo sung")
        @Size(max = 2000, message = "Noi dung toi da 2000 ky tu") String content) {
}
