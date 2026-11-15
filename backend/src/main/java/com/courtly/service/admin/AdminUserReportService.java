package com.courtly.service.admin;

import com.courtly.api.admin.dto.AdminReportResponses;
import com.courtly.common.enums.ReportStatus;
import com.courtly.common.enums.ReportTargetType;
import com.courtly.common.enums.UserStatus;
import com.courtly.common.exception.ApiException;
import com.courtly.common.exception.ErrorCode;
import com.courtly.domain.account.RolePermissionRepository;
import com.courtly.domain.account.User;
import com.courtly.domain.account.UserRepository;
import com.courtly.domain.review.AdminReportQueries;
import com.courtly.domain.review.Report;
import com.courtly.domain.review.ReportRepository;
import com.courtly.service.audit.AuditService;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 2.3.36 - quan tri vien xu ly bao cao nguoi dung.
 *
 * <p>Vong doi: pending -> reviewing (nhan xu ly) -> resolved (chap nhan, kem huong xu ly) hoac
 * rejected (khong co co so). Bao cao da ket luan khong the sua lai.
 */
@Service
@RequiredArgsConstructor
public class AdminUserReportService {

    static final String PERMISSION = "report.handle";
    static final String LOCK_PERMISSION = "user.manage";
    private static final int PREVIEW_LENGTH = 120;
    private static final int RELATED_LIMIT = 10;
    private static final Map<String, UserStatus> ACCOUNT_ACTIONS = Map.of(
            "account_banned", UserStatus.BANNED, "account_inactive", UserStatus.INACTIVE);
    private static final Set<String> RESOLVE_ACTIONS = Set.of("warning", "account_banned", "account_inactive", "other");

    private final AdminAccessService accessService;
    private final AdminReportQueries reportQueries;
    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final AdminUserService userService;
    private final AuditService auditService;

    /**
     * @param status all hoac pending, reviewing, resolved, rejected
     * @param reason all hoac mot ly do co trong du lieu
     */
    @Transactional(readOnly = true)
    public AdminReportResponses.ListResponse list(UUID adminId, String status, String reason, String q, int page, int size) {
        accessService.requirePermission(adminId, PERMISSION);
        List<ReportStatus> statuses = parseStatuses(status);
        boolean anyReason = reason == null || reason.isBlank() || "all".equals(reason);
        String keyword = "%" + (q == null ? "" : q.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")) + "%";
        Page<Report> reports = reportQueries.search(ReportTargetType.USER, statuses, anyReason,
                anyReason ? "" : reason.trim(), keyword, PageRequest.of(page, size));

        Map<UUID, User> targets = userRepository.findAllById(
                reports.getContent().stream().map(Report::getTargetId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(User::getId, user -> user));

        Map<String, Long> counts = new LinkedHashMap<>();
        Arrays.stream(ReportStatus.values()).forEach(value -> counts.put(value.getValue(), 0L));
        reportQueries.countByStatus(ReportTargetType.USER).forEach(row -> counts.put(row.getStatus().getValue(), row.getTotal()));

        return new AdminReportResponses.ListResponse(reports.getContent().stream()
                .map(report -> toSummary(report, targets.get(report.getTargetId()))).toList(),
                reports.getNumber(), reports.getSize(), reports.getTotalElements(), reports.getTotalPages(),
                counts, reportQueries.reasons(ReportTargetType.USER));
    }

    @Transactional(readOnly = true)
    public AdminReportResponses.Detail get(UUID adminId, UUID reportId) {
        User admin = accessService.requirePermission(adminId, PERMISSION);
        return toDetail(admin, requireReport(reportId));
    }

    /** pending -> reviewing; quan tri vien nhan xu ly tro thanh nguoi phu trach. */
    @Transactional
    public AdminReportResponses.Detail start(UUID adminId, UUID reportId) {
        User admin = accessService.requirePermission(adminId, PERMISSION);
        Report report = requireReport(reportId);
        requireNotSelf(admin, report);
        if (report.getStatus() != ReportStatus.PENDING) {
            throw stateInvalid("Chi nhan xu ly duoc bao cao dang cho xu ly.");
        }
        report.setStatus(ReportStatus.REVIEWING);
        report.setHandledBy(admin);
        reportRepository.saveAndFlush(report);
        auditService.record(admin, "report.start", "reports", report.getId(),
                Map.of("status", "pending"), Map.of("status", "reviewing"));
        return toDetail(admin, report);
    }

    /** Chap nhan bao cao: ghi huong xu ly, co the khoa / ngung tai khoan ngay trong cung giao dich. */
    @Transactional
    public AdminReportResponses.Detail resolve(UUID adminId, UUID reportId, String action, String note) {
        User admin = accessService.requirePermission(adminId, PERMISSION);
        Report report = requireReport(reportId);
        requireConcludable(admin, report);
        String normalizedAction = action == null ? "" : action.trim();
        if (!RESOLVE_ACTIONS.contains(normalizedAction)) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "Huong xu ly khong hop le.",
                    Map.of("action", "Chi nhan warning, account_banned, account_inactive hoac other"));
        }
        String cleanNote = cleanNote(note);

        UserStatus targetStatus = ACCOUNT_ACTIONS.get(normalizedAction);
        Map<String, Object> newData = new LinkedHashMap<>();
        newData.put("status", "resolved");
        newData.put("action", normalizedAction);
        newData.put("note", cleanNote);
        if (targetStatus != null) {
            User target = userRepository.findById(report.getTargetId()).orElseThrow(
                    () -> stateInvalid("Tai khoan bi bao cao khong con ton tai nen khong the khoa."));
            newData.put("targetStatusBefore", target.getStatus().getValue());
            // Cung quy tac 2.3.28: can quyen user.manage, co ly do, khong tu khoa chinh minh.
            userService.changeStatus(admin.getId(), target.getId(), targetStatus.getValue(),
                    "Xu ly bao cao: " + cleanNote);
        }
        conclude(admin, report, ReportStatus.RESOLVED, normalizedAction, cleanNote);
        auditService.record(admin, "report.resolve", "reports", report.getId(), Map.of("status", "reviewing"), newData);
        return toDetail(admin, report);
    }

    @Transactional
    public AdminReportResponses.Detail reject(UUID adminId, UUID reportId, String note) {
        User admin = accessService.requirePermission(adminId, PERMISSION);
        Report report = requireReport(reportId);
        requireConcludable(admin, report);
        String cleanNote = cleanNote(note);
        conclude(admin, report, ReportStatus.REJECTED, null, cleanNote);
        auditService.record(admin, "report.reject", "reports", report.getId(), Map.of("status", "reviewing"),
                Map.of("status", "rejected", "note", cleanNote));
        return toDetail(admin, report);
    }

    private void conclude(User admin, Report report, ReportStatus status, String action, String note) {
        report.setStatus(status);
        report.setHandlingAction(action);
        report.setHandlingNote(note);
        report.setHandledBy(admin);
        report.setHandledAt(Instant.now());
        reportRepository.saveAndFlush(report);
    }

    private void requireConcludable(User admin, Report report) {
        requireNotSelf(admin, report);
        if (report.getStatus() != ReportStatus.REVIEWING) {
            String message = report.getStatus() == ReportStatus.PENDING
                    ? "Hay nhan xu ly bao cao truoc khi ket luan."
                    : "Bao cao da duoc ket luan, khong the thay doi.";
            throw stateInvalid(message);
        }
    }

    /** Khong tu xu ly bao cao ve chinh minh. */
    private static void requireNotSelf(User admin, Report report) {
        if (report.getTargetId().equals(admin.getId())) {
            throw stateInvalid("Ban khong the xu ly bao cao ve chinh minh.");
        }
    }

    private static String cleanNote(String note) {
        String trimmed = note == null ? "" : note.trim();
        if (trimmed.length() < 3) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "Vui long nhap ghi chu (it nhat 3 ky tu).",
                    Map.of("note", "Bat buoc nhap ghi chu khi ket luan"));
        }
        return trimmed;
    }

    private Report requireReport(UUID reportId) {
        Report report = reportQueries.findDetailById(reportId)
                .orElseThrow(() -> ApiException.notFound("Khong tim thay bao cao."));
        if (report.getTargetType() != ReportTargetType.USER) {
            throw ApiException.notFound("Khong tim thay bao cao.");
        }
        return report;
    }

    private AdminReportResponses.Summary toSummary(Report report, User target) {
        return new AdminReportResponses.Summary(report.getId(), report.getStatus().getValue(), report.getReason(),
                preview(report.getDescription()), person(report.getReporter()), target(report.getTargetId(), target),
                report.getCreatedAt(), report.getHandledAt(),
                report.getHandledBy() == null ? null : report.getHandledBy().getFullName());
    }

    private AdminReportResponses.Detail toDetail(User admin, Report report) {
        User target = userRepository.findById(report.getTargetId()).orElse(null);
        boolean self = report.getTargetId().equals(admin.getId());
        boolean canLock = target != null && target.getStatus() == UserStatus.ACTIVE && !self
                && rolePermissionRepository.userHasPermission(admin.getId(), LOCK_PERMISSION);
        List<AdminReportResponses.Related> related = reportQueries
                .findOthersAboutTarget(ReportTargetType.USER, report.getTargetId(), report.getId(), PageRequest.of(0, RELATED_LIMIT))
                .stream().map(item -> new AdminReportResponses.Related(item.getId(), item.getStatus().getValue(), item.getReason(),
                        item.getReporter().getFullName(), item.getHandlingAction(), item.getCreatedAt())).toList();
        AdminReportResponses.Handling handling = report.getStatus() == ReportStatus.RESOLVED || report.getStatus() == ReportStatus.REJECTED
                ? new AdminReportResponses.Handling(report.getHandlingAction(), report.getHandlingNote(),
                        report.getHandledBy() == null ? null : report.getHandledBy().getFullName(), report.getHandledAt())
                : null;
        return new AdminReportResponses.Detail(report.getId(), report.getStatus().getValue(), report.getReason(),
                report.getDescription(), person(report.getReporter()), target(report.getTargetId(), target),
                target == null ? List.of() : target.getUserRoles().stream().map(role -> role.getRole().getCode()).sorted().toList(),
                reportQueries.countAboutTarget(ReportTargetType.USER, report.getTargetId()), related, report.getCreatedAt(),
                handling, report.getStatus() == ReportStatus.PENDING && !self,
                report.getStatus() == ReportStatus.REVIEWING && !self, canLock, self);
    }

    private static AdminReportResponses.Person person(User user) {
        return new AdminReportResponses.Person(user.getId(), user.getFullName(), user.getEmail());
    }

    private static AdminReportResponses.Target target(UUID targetId, User user) {
        return user == null
                ? new AdminReportResponses.Target(targetId, null, null, null, false)
                : new AdminReportResponses.Target(user.getId(), user.getFullName(), user.getEmail(), user.getStatus().getValue(), true);
    }

    private static String preview(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        String text = description.strip();
        return text.length() <= PREVIEW_LENGTH ? text : text.substring(0, PREVIEW_LENGTH) + "...";
    }

    private static List<ReportStatus> parseStatuses(String status) {
        if (status == null || status.isBlank() || "all".equals(status)) {
            return List.of(ReportStatus.values());
        }
        try {
            return List.of(ReportStatus.from(status));
        } catch (IllegalArgumentException exception) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "Trang thai bao cao khong hop le.",
                    Map.of("status", "Chi nhan all, pending, reviewing, resolved hoac rejected"));
        }
    }

    private static ApiException stateInvalid(String message) {
        return ApiException.conflict(ErrorCode.REPORT_STATE_INVALID, message, Map.of("status", message));
    }
}
