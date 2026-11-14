package com.courtly.api.admin.dto;

import java.util.List;

/** 2.3.30 - quan ly quyen cua tung vai tro. */
public final class AdminPermissionResponses {

    private AdminPermissionResponses() {
    }

    public record Permission(String code, String name, String description) {
    }

    /** @param permissions ma cac quyen vai tro dang co, sap theo ma */
    public record RolePermissions(String code, String name, List<String> permissions) {
    }

    /**
     * @param protectedPermissions quyen cot loi khong duoc go khoi vai tro admin
     */
    public record Matrix(List<Permission> permissions, List<RolePermissions> roles, List<String> protectedPermissions) {
    }
}
