package com.courtly.api.owner.dto;

import jakarta.validation.constraints.NotNull;

/** 2.3.9 - buoc sinh khung gio: 30 hoac 60 phut. */
public record UpdateSlotStepRequest(
        @NotNull(message = "Vui long chon buoc khung gio")
        Integer stepMinutes) {
}
