package com.courtly.service.admin;

import com.courtly.common.enums.UserStatus;
import com.courtly.common.exception.ApiException;
import com.courtly.common.exception.ErrorCode;
import com.courtly.domain.account.Role;
import com.courtly.domain.account.RolePermissionRepository;
import com.courtly.domain.account.User;
import com.courtly.domain.account.UserRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kiem tra quyen o phia server cho khu quan tri.
 *
 * <p>Token chi chung minh danh tinh luc dang nhap. Moi request quan tri doi chieu lai database:
 * tai khoan bi khoa, bi go vai tro admin hoac vai tro bi go quyen thi token cu khong con dung duoc.
 */
@Service
@RequiredArgsConstructor
public class AdminAccessService {

    private final UserRepository userRepository;
    private final RolePermissionRepository rolePermissionRepository;

    @Transactional(readOnly = true)
    public User requireActiveAdmin(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.forbidden(ErrorCode.FORBIDDEN, "Ban khong co quyen truy cap trang quan tri."));
        boolean isAdmin = user.getUserRoles().stream().anyMatch(userRole -> Role.ADMIN.equals(userRole.getRole().getCode()));
        if (!isAdmin) {
            throw ApiException.forbidden(ErrorCode.FORBIDDEN, "Ban khong co quyen truy cap trang quan tri.");
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw ApiException.forbidden(ErrorCode.ACCOUNT_DISABLED, "Tai khoan dang bi khoa.");
        }
        return user;
    }

    /** Quan tri vien phai co quyen chi tiet {@code permissionCode} qua mot trong cac vai tro cua minh. */
    @Transactional(readOnly = true)
    public User requirePermission(UUID userId, String permissionCode) {
        User admin = requireActiveAdmin(userId);
        if (!rolePermissionRepository.userHasPermission(userId, permissionCode)) {
            throw ApiException.forbidden(ErrorCode.PERMISSION_DENIED, "Ban chua duoc cap quyen thuc hien thao tac nay.");
        }
        return admin;
    }
}
