package com.courtly.api.admin.dto;

import jakarta.validation.constraints.Size;

/** 2.3.47 - ghi chu khi xac nhan (khong bat buoc). */
public record ReviewPaymentRequest(@Size(max = 500, message = "Ghi chu toi da 500 ky tu") String note) {
}
