package com.courtly.api.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 2.3.35 - tu choi san kem ly do trong danh muc.
 *
 * @param reasonCode ma ly do (lay tu GET /api/v1/admin/venues/reject-reasons)
 * @param note       ghi chu them; bat buoc it nhat 3 ky tu khi chon ly do "other"
 */
public record RejectVenueRequest(
        @NotBlank(message = "Vui long chon ly do tu choi")
        String reasonCode,

        @Size(max = 500, message = "Ghi chu toi da 500 ky tu")
        String note) {

    public RejectVenueRequest {
        reasonCode = reasonCode == null ? null : reasonCode.trim();
        note = note == null || note.isBlank() ? null : note.trim();
    }
}
