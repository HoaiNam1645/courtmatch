package com.courtly.api.admin.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

/** 2.3.30 - toan bo quyen moi cua mot vai tro (thay the tap quyen hien tai). */
public record UpdateRolePermissionsRequest(@NotNull(message = "Vui long gui danh sach quyen") List<String> permissions) {
}
