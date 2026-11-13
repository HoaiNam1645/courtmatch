package com.courtly.api.owner.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 2.3.14 - tu choi yeu cau dat san: bat buoc chon ly do trong danh muc.
 *
 * @param reasonCode ma ly do (xem RejectReasons); "other" bat buoc co ghi chu
 * @param note       ghi chu them, hien cho nguoi choi
 */
public record RejectBookingRequestRequest(

        @NotBlank(message = "Vui long chon ly do tu choi")
        @Size(max = 50, message = "Ma ly do khong hop le")
        String reasonCode,

        @Size(max = 500, message = "Ghi chu toi da 500 ky tu")
        String note) {

    public RejectBookingRequestRequest {
        note = note == null || note.isBlank() ? null : note.trim();
    }
}
