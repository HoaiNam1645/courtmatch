package com.courtly.service.admin;

import com.courtly.api.admin.dto.AdminUserResponses;
import com.courtly.common.enums.UserStatus;
import com.courtly.common.exception.ApiException;
import com.courtly.common.exception.ErrorCode;
import com.courtly.domain.account.Role;
import com.courtly.domain.account.User;
import com.courtly.domain.account.UserRepository;
import com.courtly.domain.account.UserRole;
import com.courtly.domain.system.AuditLog;
import com.courtly.domain.system.AuditLogRepository;
import com.courtly.service.audit.AuditService;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 2.3.28 - quan tri vien quan ly tai khoan nguoi dung. */
@Service
@RequiredArgsConstructor
public class AdminUserService {

    static final String STATUS_ACTION = "user.status.change";
    static final String PERMISSION = "user.manage";
    private static final int MIN_REASON = 3;

    private final AdminAccessService accessService;
    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;
    private final AuditService auditService;

    /**
     * @param status all hoac mot trang thai
     * @param role   all hoac mot ma vai tro
     * @param q      tim theo ten (khong dau), email, so dien thoai
     */
    @Transactional(readOnly = true)
    public AdminUserResponses.ListResponse list(UUID adminId, String q, String role, String status, int page, int size) {
        accessService.requirePermission(adminId, PERMISSION);
        List<UserStatus> statuses = parseStatuses(status);
        boolean anyRole = role == null || role.isBlank() || "all".equals(role);
        if (!anyRole && !List.of(Role.PLAYER, Role.OWNER, Role.STAFF, Role.ADMIN).contains(role)) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "Vai tro khong hop le.",
                    Map.of("role", "Chi nhan all, player, owner, staff hoac admin"));
        }
        String keyword = "%" + (q == null ? "" : q.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")) + "%";

        Page<User> users = userRepository.searchForAdmin(statuses, anyRole, anyRole ? List.of("-") : List.of(role), keyword,
                PageRequest.of(page, size));
        Map<String, Long> counts = new LinkedHashMap<>();
        Arrays.stream(UserStatus.values()).forEach(value -> counts.put(value.getValue(), 0L));
        userRepository.countByStatus().forEach(row -> counts.put(row.getStatus().getValue(), row.getTotal()));

        return new AdminUserResponses.ListResponse(users.getContent().stream().map(AdminUserService::toSummary).toList(),
                users.getNumber(), users.getSize(), users.getTotalElements(), users.getTotalPages(), counts);
    }

    @Transactional(readOnly = true)
    public AdminUserResponses.Detail get(UUID adminId, UUID userId) {
        accessService.requirePermission(adminId, PERMISSION);
        return toDetail(requireUser(userId));
    }

    /** Khoa, ngung hoac kich hoat lai tai khoan; ghi nhat ky kem ly do. */
    @Transactional
    public AdminUserResponses.Detail changeStatus(UUID adminId, UUID userId, String statusValue, String reason) {
        User admin = accessService.requirePermission(adminId, PERMISSION);
        UserStatus target = parseTarget(statusValue);
        User user = requireUser(userId);
        UserStatus current = user.getStatus();

        if (user.getId().equals(admin.getId())) {
            throw ApiException.conflict(ErrorCode.USER_STATE_INVALID, "Ban khong the doi trang thai tai khoan cua chinh minh.",
                    Map.of("status", "Khong the tu doi trang thai"));
        }
        if (current == target) {
            throw ApiException.conflict(ErrorCode.USER_STATE_INVALID, "Tai khoan da o trang thai nay.",
                    Map.of("status", "Tai khoan da o trang thai nay"));
        }
        if (current == UserStatus.PENDING && target == UserStatus.ACTIVE) {
            throw ApiException.conflict(ErrorCode.USER_STATE_INVALID,
                    "Tai khoan chua xac minh email nen chua the kich hoat.", Map.of("status", "Tai khoan chua xac minh email"));
        }
        if (target != UserStatus.ACTIVE && (reason == null || reason.length() < MIN_REASON)) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "Vui long nhap ly do (it nhat 3 ky tu).",
                    Map.of("reason", "Bat buoc nhap ly do khi khoa hoac ngung tai khoan"));
        }

        user.setStatus(target);
        Map<String, Object> newData = new LinkedHashMap<>();
        newData.put("status", target.getValue());
        if (reason != null) {
            newData.put("reason", reason);
        }
        auditService.record(admin, STATUS_ACTION, "users", user.getId(), Map.of("status", current.getValue()), newData);
        return toDetail(user);
    }

    private User requireUser(UUID userId) {
        return userRepository.findById(userId).orElseThrow(() -> ApiException.notFound("Khong tim thay tai khoan."));
    }

    private static List<UserStatus> parseStatuses(String status) {
        if (status == null || status.isBlank() || "all".equals(status)) {
            return List.of(UserStatus.values());
        }
        try {
            return List.of(UserStatus.from(status));
        } catch (IllegalArgumentException exception) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "Trang thai tai khoan khong hop le.",
                    Map.of("status", "Chi nhan all, active, inactive, banned hoac pending"));
        }
    }

    private static UserStatus parseTarget(String value) {
        try {
            UserStatus status = UserStatus.from(value);
            if (status != UserStatus.PENDING) {
                return status;
            }
        } catch (IllegalArgumentException exception) {
            // roi xuong thong bao chung
        }
        throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "Trang thai khong hop le.",
                Map.of("status", "Chi nhan active, inactive hoac banned"));
    }

    static AdminUserResponses.Summary toSummary(User user) {
        return new AdminUserResponses.Summary(user.getId(), user.getFullName(), user.getEmail(), user.getPhone(),
                user.getStatus().getValue(), rolesOf(user), user.getEmailVerifiedAt() != null,
                user.getPhoneVerifiedAt() != null, user.getLastLoginAt(), user.getCreatedAt());
    }

    AdminUserResponses.Detail toDetail(User user) {
        AdminUserResponses.StatusChange change = auditLogRepository
                .findFirstByEntityTypeAndEntityIdAndActionOrderByCreatedAtDesc("users", user.getId(), STATUS_ACTION)
                .map(AdminUserService::toChange).orElse(null);
        return new AdminUserResponses.Detail(user.getId(), user.getFullName(), user.getEmail(), user.getPhone(),
                user.getAvatarUrl(), user.getStatus().getValue(), rolesOf(user), user.getEmailVerifiedAt() != null,
                user.getEmailVerifiedAt(), user.getPhoneVerifiedAt() != null, user.getPhoneVerifiedAt(),
                user.isPasswordChangeRequired(), user.getLastLoginAt(), user.getCreatedAt(), user.getUpdatedAt(), change);
    }

    private static AdminUserResponses.StatusChange toChange(AuditLog log) {
        Map<String, Object> data = log.getNewData() == null ? Map.of() : log.getNewData();
        return new AdminUserResponses.StatusChange(String.valueOf(data.get("status")),
                data.get("reason") == null ? null : String.valueOf(data.get("reason")),
                log.getActor() == null ? null : log.getActor().getFullName(), log.getCreatedAt());
    }

    static List<String> rolesOf(User user) {
        return user.getUserRoles().stream().map(UserRole::getRole).map(Role::getCode).sorted().toList();
    }
}
