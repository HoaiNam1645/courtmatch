package com.courtly.api.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Ket luan bao cao. {@code action} chi can khi chap nhan bao cao (warning, account_banned,
 * account_inactive, other); tu choi bao cao thi bo qua.
 */
public record ConcludeReportRequest(
        @Size(max = 30, message = "Huong xu ly toi da 30 ky tu") String action,
        @NotBlank(message = "Vui long nhap ghi chu") @Size(min = 3, max = 1000, message = "Ghi chu tu 3 den 1000 ky tu") String note) {
}
