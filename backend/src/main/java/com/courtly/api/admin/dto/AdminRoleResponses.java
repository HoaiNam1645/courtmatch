package com.courtly.api.admin.dto;

import java.util.List;
import java.util.UUID;

/** 2.3.29 - phan quyen vai tro cho nguoi dung. */
public final class AdminRoleResponses {

    private AdminRoleResponses() {
    }

    public record Role(String code, String name, String description, long userCount, int permissionCount) {
    }

    public record RoleList(List<Role> roles) {
    }
}
