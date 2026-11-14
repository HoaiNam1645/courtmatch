package com.courtly.api.admin.dto;

import jakarta.validation.constraints.NotBlank;

/** 2.3.29 - vai tro can gan cho tai khoan (player, owner, staff, admin). */
public record AssignRoleRequest(@NotBlank(message = "Vui long chon vai tro") String role) {

    public AssignRoleRequest {
        role = role == null ? null : role.trim();
    }
}
