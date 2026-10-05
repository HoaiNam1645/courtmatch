package com.courtly.api.auth.dto;

import com.courtly.domain.account.User;
import com.courtly.domain.account.UserRole;
import java.util.List;
import java.util.UUID;

/**
 * Thong tin tai khoan tra ve cho frontend.
 *
 * <p>Khong bao gio chua passwordHash hay bat ky du lieu nhay cam nao khac.
 */
public record UserResponse(UUID id,
                           String fullName,
                           String email,
                           String phone,
                           String avatarUrl,
                           String status,
                           List<String> roles) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getPhone(),
                user.getAvatarUrl(),
                user.getStatus().getValue(),
                user.getUserRoles().stream()
                        .map(UserRole::getRole)
                        .map(role -> role.getCode())
                        .sorted()
                        .toList());
    }
}
