package com.courtly.api.owner.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;

/** 2.3.6 - thu tu moi cua toan bo anh trong san, anh dau tien hien truoc. */
public record ReorderImagesRequest(
        @NotEmpty(message = "Danh sach anh khong duoc rong")
        List<UUID> imageIds) {
}
