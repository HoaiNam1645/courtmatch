package com.courtly.api.auth.dto;

import com.courtly.common.enums.ResetChannel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Buoc 1 cua 2.1.5: xin gui ma xac minh.
 *
 * @param channel     kenh nhan ma; hien chi ho tro {@code email}
 * @param destination email da dang ky
 */
public record PasswordResetSendRequest(

        @NotNull(message = "Vui long chon cach nhan ma xac minh")
        ResetChannel channel,

        @NotBlank(message = "Vui long nhap email da dang ky")
        @Size(max = 255, message = "Email toi da 255 ky tu")
        String destination) {
}
