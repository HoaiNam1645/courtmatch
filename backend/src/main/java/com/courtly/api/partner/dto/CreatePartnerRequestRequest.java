package com.courtly.api.partner.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Loi nhan moi choi gui toi mot ung vien doi tac (2.2.31). */
public record CreatePartnerRequestRequest(
        @NotNull(message = "Hay chon nguoi nhan")
        UUID receiverId,

        @NotBlank(message = "Hay nhap loi nhan")
        @Size(max = 500, message = "Loi nhan toi da 500 ky tu")
        String message) {
}
