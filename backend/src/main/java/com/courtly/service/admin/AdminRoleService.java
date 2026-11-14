package com.courtly.service.admin;

import com.courtly.api.admin.dto.AdminRoleResponses;
import com.courtly.api.admin.dto.AdminUserResponses;
import com.courtly.common.exception.ApiException;
import com.courtly.common.exception.ErrorCode;
import com.courtly.domain.account.CourtOwnerProfile;
import com.courtly.domain.account.CourtOwnerProfileRepository;
import com.courtly.domain.account.PlayerProfile;
import com.courtly.domain.account.PlayerProfileRepository;
import com.courtly.domain.account.Role;
import com.courtly.domain.account.RoleRepository;
import com.courtly.domain.account.User;
import com.courtly.domain.account.UserRepository;
import com.courtly.domain.account.UserRole;
import com.courtly.service.audit.AuditService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 2.3.29 - quan tri vien gan hoac go vai tro cua tai khoan.
 *
 * <p>Vai tro nam trong token luc dang nhap nen vai tro moi chi co hieu luc voi token moi; viec go
 * vai tro admin / owner co hieu luc ngay vi khu quan tri va khu chu san doi chieu lai database.
 */
@Service
@RequiredArgsConstructor
public class AdminRoleService {

    static final String PERMISSION = "user.role.assign";

    private final AdminAccessService accessService;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PlayerProfileRepository playerProfileRepository;
    private final CourtOwnerProfileRepository ownerProfileRepository;
    private final AdminUserService userService;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public AdminRoleResponses.RoleList list(UUID adminId) {
        accessService.requirePermission(adminId, PERMISSION);
        Map<String, Long> users = new HashMap<>();
        roleRepository.countUsersByRole().forEach(row -> users.put(row.getCode(), row.getTotal()));
        return new AdminRoleResponses.RoleList(roleRepository.findAll().stream()
                .sorted((a, b) -> a.getCode().compareTo(b.getCode()))
                .map(role -> new AdminRoleResponses.Role(role.getCode(), role.getName(), role.getDescription(),
                        users.getOrDefault(role.getCode(), 0L), role.getRolePermissions().size()))
                .toList());
    }

    @Transactional
    public AdminUserResponses.Detail assign(UUID adminId, UUID userId, String roleCode) {
        User admin = accessService.requirePermission(adminId, PERMISSION);
        User user = requireUser(userId);
        Role role = requireRole(roleCode);

        if (hasRole(user, roleCode)) {
            throw invalid("Tai khoan da co vai tro nay.", "role", "Tai khoan da co vai tro nay");
        }
        if (Role.OWNER.equals(roleCode) && roleRepository.existsOtherHolder(Role.OWNER, userId)) {
            throw invalid("He thong chi co mot chu san, khong the gan them vai tro chu san.", "role",
                    "Da co mot tai khoan chu san");
        }

        List<String> before = AdminUserService.rolesOf(user);
        user.getUserRoles().add(new UserRole(user, role));
        userRepository.saveAndFlush(user);
        // Ho so di kem vai tro de cac man hinh cua vai tro do dung duoc ngay.
        if (Role.PLAYER.equals(roleCode) && !playerProfileRepository.existsById(user.getId())) {
            playerProfileRepository.saveAndFlush(new PlayerProfile(user));
        }
        if (Role.OWNER.equals(roleCode) && !ownerProfileRepository.existsById(user.getId())) {
            ownerProfileRepository.saveAndFlush(new CourtOwnerProfile(user));
        }
        audit(admin, user, "user.role.assign", before, AdminUserService.rolesOf(user), roleCode);
        return userService.toDetail(user);
    }

    @Transactional
    public AdminUserResponses.Detail remove(UUID adminId, UUID userId, String roleCode) {
        User admin = accessService.requirePermission(adminId, PERMISSION);
        User user = requireUser(userId);
        requireRole(roleCode);

        if (!hasRole(user, roleCode)) {
            throw invalid("Tai khoan khong co vai tro nay.", "role", "Tai khoan khong co vai tro nay");
        }
        if (Role.ADMIN.equals(roleCode) && user.getId().equals(admin.getId())) {
            throw invalid("Ban khong the tu go vai tro quan tri vien cua chinh minh.", "role", "Khong the tu go vai tro admin");
        }
        if (user.getUserRoles().size() <= 1) {
            throw invalid("Tai khoan phai co it nhat mot vai tro.", "role", "Khong the go vai tro cuoi cung");
        }

        List<String> before = AdminUserService.rolesOf(user);
        user.getUserRoles().removeIf(userRole -> roleCode.equals(userRole.getRole().getCode()));
        userRepository.saveAndFlush(user);
        audit(admin, user, "user.role.remove", before, AdminUserService.rolesOf(user), roleCode);
        return userService.toDetail(user);
    }

    private void audit(User admin, User user, String action, List<String> before, List<String> after, String roleCode) {
        auditService.record(admin, action, "users", user.getId(), Map.of("roles", before),
                Map.of("roles", after, "role", roleCode));
    }

    private static boolean hasRole(User user, String code) {
        return user.getUserRoles().stream().anyMatch(userRole -> code.equals(userRole.getRole().getCode()));
    }

    private User requireUser(UUID userId) {
        return userRepository.findById(userId).orElseThrow(() -> ApiException.notFound("Khong tim thay tai khoan."));
    }

    private Role requireRole(String code) {
        return roleRepository.findByCode(code).orElseThrow(() -> ApiException.badRequest(ErrorCode.VALIDATION_FAILED,
                "Vai tro khong hop le.", Map.of("role", "Chi nhan player, owner, staff hoac admin")));
    }

    private static ApiException invalid(String message, String field, String fieldMessage) {
        return ApiException.conflict(ErrorCode.ROLE_ASSIGNMENT_INVALID, message, Map.of(field, fieldMessage));
    }
}
