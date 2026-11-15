package com.courtly.api.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Ghi chu bat buoc khi ket luan hoac khoi phuc (2.3.37). */
public record ReviewReportNoteRequest(
        @NotBlank(message = "Vui long nhap ghi chu") @Size(min = 3, max = 1000, message = "Ghi chu tu 3 den 1000 ky tu") String note) {
}
