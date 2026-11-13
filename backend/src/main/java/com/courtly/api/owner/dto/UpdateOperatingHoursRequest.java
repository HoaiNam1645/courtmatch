package com.courtly.api.owner.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 2.3.7 - gio hoat dong cua ca tuan; phai gui du 7 ngay, moi ngay mot lan. */
public record UpdateOperatingHoursRequest(
        @NotNull(message = "Thieu gio hoat dong")
        @Size(min = 7, max = 7, message = "Phai khai bao du 7 ngay trong tuan")
        List<@Valid OperatingHoursInput> days) {
}
