package com.courtly.api.booking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectBookingRequest(
        @NotBlank(message = "Vui long nhap ly do tu choi")
        @Size(max = 1000, message = "Ly do toi da 1000 ky tu") String reason) {
}
