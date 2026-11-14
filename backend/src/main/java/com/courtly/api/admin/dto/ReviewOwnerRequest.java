package com.courtly.api.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 2.3.32 - quan tri vien quyet dinh trang thai xac minh ho so chu san.
 *
 * @param status verified, rejected hoac pending (dat lai cho xac minh)
 * @param reason bat buoc khi tu choi hoac dat lai
 */
public record ReviewOwnerRequest(
        @NotBlank(message = "Vui long chon trang thai xac minh")
        String status,

        @Size(max = 500, message = "Ly do toi da 500 ky tu")
        String reason) {

    public ReviewOwnerRequest {
        status = status == null ? null : status.trim();
        reason = reason == null || reason.isBlank() ? null : reason.trim();
    }
}
