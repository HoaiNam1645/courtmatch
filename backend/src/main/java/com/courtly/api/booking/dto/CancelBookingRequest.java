package com.courtly.api.booking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Huy dat san (2.1.31). Giao dien co modal chon ly do. */
public record CancelBookingRequest(

        @NotBlank(message = "Vui long chon ly do huy")
        @Size(max = 255, message = "Ly do toi da 255 ky tu")
        String reason) {

    public CancelBookingRequest {
        reason = reason == null ? null : reason.trim();
    }
}
