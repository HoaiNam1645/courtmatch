package com.courtly.api.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 2.3.28 - doi trang thai tai khoan.
 *
 * @param status active, inactive hoac banned
 * @param reason bat buoc khi khoa hoac ngung tai khoan
 */
public record ChangeUserStatusRequest(
        @NotBlank(message = "Vui long chon trang thai")
        String status,

        @Size(max = 500, message = "Ly do toi da 500 ky tu")
        String reason) {

    public ChangeUserStatusRequest {
        status = status == null ? null : status.trim();
        reason = reason == null || reason.isBlank() ? null : reason.trim();
    }
}
