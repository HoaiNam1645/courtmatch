package com.courtly.service.admin;

import com.courtly.api.admin.dto.AdminPermissionResponses;
import com.courtly.common.exception.ApiException;
import com.courtly.common.exception.ErrorCode;
import com.courtly.domain.account.Permission;
import com.courtly.domain.account.PermissionRepository;
import com.courtly.domain.account.Role;
import com.courtly.domain.account.RolePermission;
import com.courtly.domain.account.RoleRepository;
import com.courtly.domain.account.User;
import com.courtly.service.audit.AuditService;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 2.3.30 - quan tri vien bat / tat quyen chi tiet cua tung vai tro.
 *
 * <p>Nguoi dung nhan quyen qua vai tro cua minh. Quyen cot loi cua vai tro admin khong duoc go de
 * khong ai con quan ly duoc tai khoan va vai tro.
 */
@Service
@RequiredArgsConstructor
public class AdminPermissionService {

    /** Go hai quyen nay khoi admin la tu khoa he thong quan tri. */
    static final Set<String> PROTECTED_ADMIN_PERMISSIONS = Set.of("user.manage", "user.role.assign");

    private final AdminAccessService accessService;
    private final PermissionRepository permissionRepository;
    private final RoleRepository roleRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public AdminPermissionResponses.Matrix matrix(UUID adminId) {
        accessService.requirePermission(adminId, AdminRoleService.PERMISSION);
        return buildMatrix();
    }

    /** Thay toan bo tap quyen cua vai tro bang {@code codes}; ghi nhat ky phan thay doi. */
    @Transactional
    public AdminPermissionResponses.Matrix update(UUID adminId, String roleCode, List<String> codes) {
        User admin = accessService.requirePermission(adminId, AdminRoleService.PERMISSION);
        Role role = roleRepository.findByCode(roleCode).orElseThrow(() -> ApiException.notFound("Khong tim thay vai tro."));

        Set<String> wanted = new TreeSet<>();
        for (String code : codes) {
            if (code == null || code.isBlank()) {
                throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "Danh sach quyen khong hop le.",
                        Map.of("permissions", "Ma quyen khong duoc de trong"));
            }
            wanted.add(code.trim());
        }
        Map<String, Permission> catalog = permissionRepository.findAll().stream()
                .collect(Collectors.toMap(Permission::getCode, permission -> permission));
        List<String> unknown = wanted.stream().filter(code -> !catalog.containsKey(code)).toList();
        if (!unknown.isEmpty()) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "Co quyen khong ton tai.",
                    Map.of("permissions", "Quyen khong ton tai: " + String.join(", ", unknown)));
        }
        if (Role.ADMIN.equals(roleCode) && !wanted.containsAll(PROTECTED_ADMIN_PERMISSIONS)) {
            throw ApiException.conflict(ErrorCode.PERMISSION_ASSIGNMENT_INVALID,
                    "Khong the go quyen cot loi cua quan tri vien (quan ly tai khoan, phan quyen vai tro).",
                    Map.of("permissions", "Vai tro admin phai giu: " + String.join(", ", new TreeSet<>(PROTECTED_ADMIN_PERMISSIONS))));
        }

        Set<String> current = role.getRolePermissions().stream().map(item -> item.getPermission().getCode())
                .collect(Collectors.toCollection(TreeSet::new));
        if (!current.equals(wanted)) {
            role.getRolePermissions().removeIf(item -> !wanted.contains(item.getPermission().getCode()));
            for (String code : wanted) {
                if (!current.contains(code)) {
                    role.getRolePermissions().add(new RolePermission(role, catalog.get(code)));
                }
            }
            roleRepository.saveAndFlush(role);
            Set<String> added = new TreeSet<>(wanted);
            added.removeAll(current);
            Set<String> removed = new TreeSet<>(current);
            removed.removeAll(wanted);
            auditService.record(admin, "role.permissions.update", "roles", role.getId(),
                    Map.of("permissions", List.copyOf(current)),
                    Map.of("permissions", List.copyOf(wanted), "added", List.copyOf(added), "removed", List.copyOf(removed)));
        }
        return buildMatrix();
    }

    private AdminPermissionResponses.Matrix buildMatrix() {
        List<AdminPermissionResponses.Permission> permissions = permissionRepository.findAll().stream()
                .sorted(Comparator.comparing(Permission::getCode))
                .map(item -> new AdminPermissionResponses.Permission(item.getCode(), item.getName(), item.getDescription()))
                .toList();
        List<AdminPermissionResponses.RolePermissions> roles = roleRepository.findAll().stream()
                .sorted(Comparator.comparing(Role::getCode))
                .map(role -> new AdminPermissionResponses.RolePermissions(role.getCode(), role.getName(),
                        role.getRolePermissions().stream().map(item -> item.getPermission().getCode()).sorted().toList()))
                .toList();
        return new AdminPermissionResponses.Matrix(permissions, roles, List.copyOf(new TreeSet<>(PROTECTED_ADMIN_PERMISSIONS)));
    }
}
