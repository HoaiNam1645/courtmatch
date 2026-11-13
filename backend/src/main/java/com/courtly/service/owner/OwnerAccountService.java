package com.courtly.service.owner;

import com.courtly.api.auth.dto.UserResponse;
import com.courtly.api.owner.dto.ChangePasswordRequest;
import com.courtly.common.enums.UserStatus;
import com.courtly.common.exception.ApiException;
import com.courtly.common.exception.ErrorCode;
import com.courtly.domain.account.Role;
import com.courtly.domain.account.User;
import com.courtly.domain.account.UserRepository;
import com.courtly.service.audit.AuditService;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 2.3.1 - tai khoan chu san do quan tri vien cap.
 *
 * <p>Token chi chung minh danh tinh tai thoi diem dang nhap. Moi request vao khu quan ly
 * deu doi chieu lai database: tai khoan bi khoa hoac bi go vai tro sau khi da dang nhap
 * thi token cu khong con dung duoc.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OwnerAccountService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    /**
     * Chan request vao khu quan ly neu tai khoan khong con la chu san dang hoat dong,
     * hoac van dang dung mat khau tam.
     */
    @Transactional(readOnly = true)
    public void requireUsableOwner(UUID userId) {
        User owner = requireActiveOwner(userId);
        if (owner.isPasswordChangeRequired()) {
            throw ApiException.forbidden(ErrorCode.PASSWORD_CHANGE_REQUIRED,
                    "Ban can doi mat khau tam truoc khi su dung trang quan ly san.");
        }
    }

    /** Doi mat khau. Mat khau moi dat xong thi go co bat buoc doi mat khau. */
    @Transactional
    public UserResponse changePassword(UUID userId, ChangePasswordRequest request) {
        User owner = requireActiveOwner(userId);

        if (owner.getPasswordHash() == null
                || !passwordEncoder.matches(request.currentPassword(), owner.getPasswordHash())) {
            throw ApiException.badRequest(ErrorCode.CURRENT_PASSWORD_INVALID,
                    "Mat khau hien tai khong dung.",
                    Map.of("currentPassword", "Mat khau hien tai khong dung"));
        }
        if (passwordEncoder.matches(request.newPassword(), owner.getPasswordHash())) {
            throw ApiException.badRequest(ErrorCode.PASSWORD_SAME_AS_OLD,
                    "Mat khau moi phai khac mat khau hien tai.",
                    Map.of("newPassword", "Mat khau moi phai khac mat khau hien tai"));
        }

        boolean wasTemporary = owner.isPasswordChangeRequired();
        owner.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        owner.setPasswordChangeRequired(false);
        // Khong ghi mat khau hay hash vao nhat ky.
        auditService.record(owner, "owner.password.change", "users", owner.getId(),
                Map.of("passwordChangeRequired", wasTemporary),
                Map.of("passwordChangeRequired", false));

        log.info("Chu san {} da doi mat khau", owner.getId());
        return UserResponse.from(owner);
    }

    private User requireActiveOwner(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.forbidden(ErrorCode.FORBIDDEN,
                        "Ban khong co quyen truy cap trang quan ly san."));
        boolean isOwner = user.getUserRoles().stream()
                .anyMatch(userRole -> Role.OWNER.equals(userRole.getRole().getCode()));
        if (!isOwner) {
            throw ApiException.forbidden(ErrorCode.FORBIDDEN,
                    "Ban khong co quyen truy cap trang quan ly san.");
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw ApiException.forbidden(ErrorCode.ACCOUNT_DISABLED, "Tai khoan dang bi khoa.");
        }
        return user;
    }
}
