package com.courtly.service.admin;

import com.courtly.api.admin.dto.AdminReviewReportResponses;
import com.courtly.common.enums.ReportStatus;
import com.courtly.common.enums.ReportTargetType;
import com.courtly.common.enums.ReviewStatus;
import com.courtly.common.exception.ApiException;
import com.courtly.common.exception.ErrorCode;
import com.courtly.domain.account.User;
import com.courtly.domain.review.AdminReportQueries;
import com.courtly.domain.review.PartnerReview;
import com.courtly.domain.review.PartnerReviewRepository;
import com.courtly.domain.review.Report;
import com.courtly.domain.review.ReportRepository;
import com.courtly.service.audit.AuditService;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 2.3.37 - quan tri vien xu ly bao cao ve danh gia doi tac.
 *
 * <p>Ket luan: go danh gia ({@code partner_reviews.status = hidden}, bao cao thanh resolved) hoac giu
 * nguyen danh gia (bao cao thanh rejected). Danh gia bi an khong hien o ho so va khong tinh vao diem
 * trung binh vi cac truy van cong khai chi lay danh gia visible. Quan tri vien co the khoi phuc
 * danh gia (hidden -> visible) khi doi quyet dinh; bao cao da ket luan van giu nguyen.
 */
@Service
@RequiredArgsConstructor
public class AdminReviewReportService {

    static final String PERMISSION = "report.handle";
    private static final int RELATED_LIMIT = 10;
    static final String HIDDEN = "review_hidden";
    static final String KEPT = "review_kept";

    private final AdminAccessService accessService;
    private final AdminReportQueries reportQueries;
    private final ReportRepository reportRepository;
    private final PartnerReviewRepository reviewRepository;
    private final AuditService auditService;

    /**
     * @param status       all hoac pending, reviewing, resolved, rejected
     * @param reviewStatus all hoac visible, hidden, reported (trang thai hien tai cua danh gia)
     */
    @Transactional(readOnly = true)
    public AdminReviewReportResponses.ListResponse list(UUID adminId, String status, String reason, String reviewStatus,
                                                        String q, int page, int size) {
        accessService.requirePermission(adminId, PERMISSION);
        boolean anyReason = reason == null || reason.isBlank() || "all".equals(reason);
        boolean anyReview = reviewStatus == null || reviewStatus.isBlank() || "all".equals(reviewStatus);
        Page<Report> reports = reportQueries.searchPartnerReviewReports(AdminReportSupport.parseStatuses(status), anyReason,
                anyReason ? "" : reason.trim(), anyReview, anyReview ? ReviewStatus.VISIBLE : parseReviewStatus(reviewStatus),
                AdminReportSupport.likePattern(q), PageRequest.of(page, size));

        Map<UUID, PartnerReview> reviews = reviews(reports.getContent());
        Map<String, Long> counts = new LinkedHashMap<>();
        Arrays.stream(ReportStatus.values()).forEach(value -> counts.put(value.getValue(), 0L));
        reportQueries.countByStatus(ReportTargetType.PARTNER_REVIEW)
                .forEach(row -> counts.put(row.getStatus().getValue(), row.getTotal()));

        return new AdminReviewReportResponses.ListResponse(reports.getContent().stream()
                .map(report -> new AdminReviewReportResponses.Summary(report.getId(), report.getStatus().getValue(), report.getReason(),
                        report.getReporter().getFullName(), review(report.getTargetId(), reviews.get(report.getTargetId()), true),
                        report.getCreatedAt(), report.getHandledAt(),
                        report.getHandledBy() == null ? null : report.getHandledBy().getFullName()))
                .toList(),
                reports.getNumber(), reports.getSize(), reports.getTotalElements(), reports.getTotalPages(),
                counts, reportQueries.reasons(ReportTargetType.PARTNER_REVIEW));
    }

    @Transactional(readOnly = true)
    public AdminReviewReportResponses.Detail get(UUID adminId, UUID reportId) {
        User admin = accessService.requirePermission(adminId, PERMISSION);
        return toDetail(admin, requireReport(reportId));
    }

    @Transactional
    public AdminReviewReportResponses.Detail start(UUID adminId, UUID reportId) {
        User admin = accessService.requirePermission(adminId, PERMISSION);
        Report report = requireReport(reportId);
        requireNotInvolved(admin, report);
        if (report.getStatus() != ReportStatus.PENDING) {
            throw AdminReportSupport.stateInvalid("Chi nhan xu ly duoc bao cao dang cho xu ly.");
        }
        report.setStatus(ReportStatus.REVIEWING);
        report.setHandledBy(admin);
        reportRepository.saveAndFlush(report);
        auditService.record(admin, "report.start", "reports", report.getId(),
                Map.of("status", "pending"), Map.of("status", "reviewing"));
        return toDetail(admin, report);
    }

    /** Go danh gia: danh gia chuyen hidden, bao cao thanh resolved. Danh gia da an san thi chi ket luan bao cao. */
    @Transactional
    public AdminReviewReportResponses.Detail hide(UUID adminId, UUID reportId, String note) {
        User admin = accessService.requirePermission(adminId, PERMISSION);
        Report report = requireReport(reportId);
        requireConcludable(admin, report);
        String cleanNote = AdminReportSupport.cleanNote(note);
        PartnerReview review = reviewRepository.findWithPeopleById(report.getTargetId()).orElseThrow(
                () -> AdminReportSupport.stateInvalid("Danh gia khong con ton tai nen khong the go."));

        ReviewStatus before = review.getStatus();
        if (before != ReviewStatus.HIDDEN) {
            review.setStatus(ReviewStatus.HIDDEN);
            reviewRepository.saveAndFlush(review);
            auditService.record(admin, "review.hide", "partner_reviews", review.getId(),
                    Map.of("status", before.getValue()), Map.of("status", "hidden", "reportId", report.getId().toString()));
        }
        conclude(admin, report, ReportStatus.RESOLVED, HIDDEN, cleanNote);
        auditService.record(admin, "report.resolve", "reports", report.getId(), Map.of("status", "reviewing"),
                Map.of("status", "resolved", "action", HIDDEN, "note", cleanNote));
        return toDetail(admin, report);
    }

    /** Giu nguyen danh gia: bao cao bi bac bo. Danh gia dang 'reported' tro lai visible; danh gia da an thi giu nguyen. */
    @Transactional
    public AdminReviewReportResponses.Detail keep(UUID adminId, UUID reportId, String note) {
        User admin = accessService.requirePermission(adminId, PERMISSION);
        Report report = requireReport(reportId);
        requireConcludable(admin, report);
        String cleanNote = AdminReportSupport.cleanNote(note);
        reviewRepository.findWithPeopleById(report.getTargetId()).ifPresent(review -> {
            if (review.getStatus() == ReviewStatus.REPORTED) {
                review.setStatus(ReviewStatus.VISIBLE);
                reviewRepository.saveAndFlush(review);
                auditService.record(admin, "review.keep", "partner_reviews", review.getId(),
                        Map.of("status", "reported"), Map.of("status", "visible", "reportId", report.getId().toString()));
            }
        });
        conclude(admin, report, ReportStatus.REJECTED, KEPT, cleanNote);
        auditService.record(admin, "report.reject", "reports", report.getId(), Map.of("status", "reviewing"),
                Map.of("status", "rejected", "action", KEPT, "note", cleanNote));
        return toDetail(admin, report);
    }

    /** Khoi phuc danh gia da go (hidden -> visible) khi doi quyet dinh; bao cao da ket luan khong doi. */
    @Transactional
    public AdminReviewReportResponses.Detail restore(UUID adminId, UUID reportId, String note) {
        User admin = accessService.requirePermission(adminId, PERMISSION);
        Report report = requireReport(reportId);
        requireNotInvolved(admin, report);
        String cleanNote = AdminReportSupport.cleanNote(note);
        PartnerReview review = reviewRepository.findWithPeopleById(report.getTargetId()).orElseThrow(
                () -> AdminReportSupport.stateInvalid("Danh gia khong con ton tai nen khong the khoi phuc."));
        if (review.getStatus() != ReviewStatus.HIDDEN) {
            throw AdminReportSupport.stateInvalid("Danh gia khong o trang thai bi an.");
        }
        review.setStatus(ReviewStatus.VISIBLE);
        reviewRepository.saveAndFlush(review);
        auditService.record(admin, "review.restore", "partner_reviews", review.getId(),
                Map.of("status", "hidden"), Map.of("status", "visible", "reportId", report.getId().toString(), "note", cleanNote));
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
        requireNotInvolved(admin, report);
        if (report.getStatus() != ReportStatus.REVIEWING) {
            throw AdminReportSupport.stateInvalid(report.getStatus() == ReportStatus.PENDING
                    ? "Hay nhan xu ly bao cao truoc khi ket luan."
                    : "Bao cao da duoc ket luan, khong the thay doi.");
        }
    }

    /** Khong xu ly bao cao ve danh gia ma minh la mot ben. */
    private void requireNotInvolved(User admin, Report report) {
        if (isInvolved(admin, reviewRepository.findWithPeopleById(report.getTargetId()).orElse(null))) {
            throw AdminReportSupport.stateInvalid("Ban la mot ben cua danh gia nay nen khong the xu ly bao cao.");
        }
    }

    private static boolean isInvolved(User admin, PartnerReview review) {
        return review != null && (review.getReviewer().getId().equals(admin.getId())
                || review.getReviewedUser().getId().equals(admin.getId()));
    }

    private Report requireReport(UUID reportId) {
        Report report = reportQueries.findDetailById(reportId)
                .orElseThrow(() -> ApiException.notFound("Khong tim thay bao cao."));
        if (report.getTargetType() != ReportTargetType.PARTNER_REVIEW) {
            throw ApiException.notFound("Khong tim thay bao cao.");
        }
        return report;
    }

    private Map<UUID, PartnerReview> reviews(List<Report> reports) {
        if (reports.isEmpty()) {
            return Map.of();
        }
        return reviewRepository.findAllWithPeople(reports.stream().map(Report::getTargetId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(PartnerReview::getId, Function.identity()));
    }

    private AdminReviewReportResponses.Detail toDetail(User admin, Report report) {
        PartnerReview review = reviewRepository.findWithPeopleById(report.getTargetId()).orElse(null);
        boolean involved = isInvolved(admin, review);
        List<AdminReviewReportResponses.Related> related = reportQueries
                .findOthersAboutTarget(ReportTargetType.PARTNER_REVIEW, report.getTargetId(), report.getId(), PageRequest.of(0, RELATED_LIMIT))
                .stream().map(item -> new AdminReviewReportResponses.Related(item.getId(), item.getStatus().getValue(), item.getReason(),
                        item.getReporter().getFullName(), item.getHandlingAction(), item.getCreatedAt())).toList();
        boolean closed = report.getStatus() == ReportStatus.RESOLVED || report.getStatus() == ReportStatus.REJECTED;
        AdminReviewReportResponses.Handling handling = closed
                ? new AdminReviewReportResponses.Handling(report.getHandlingAction(), report.getHandlingNote(),
                        report.getHandledBy() == null ? null : report.getHandledBy().getFullName(), report.getHandledAt())
                : null;
        boolean reviewing = report.getStatus() == ReportStatus.REVIEWING;
        boolean canRestore = report.getStatus() == ReportStatus.RESOLVED && HIDDEN.equals(report.getHandlingAction())
                && review != null && review.getStatus() == ReviewStatus.HIDDEN && !involved;
        return new AdminReviewReportResponses.Detail(report.getId(), report.getStatus().getValue(), report.getReason(),
                report.getDescription(), report.getReporter().getFullName(), review(report.getTargetId(), review, false),
                related, reportQueries.countAboutTarget(ReportTargetType.PARTNER_REVIEW, report.getTargetId()),
                report.getCreatedAt(), handling, report.getStatus() == ReportStatus.PENDING && !involved,
                reviewing && !involved, reviewing && !involved && review != null, canRestore, involved);
    }

    private static AdminReviewReportResponses.Review review(UUID targetId, PartnerReview review, boolean preview) {
        if (review == null) {
            return new AdminReviewReportResponses.Review(targetId, false, null, null, null, null, null, null, null);
        }
        return new AdminReviewReportResponses.Review(review.getId(), true, review.getRating(),
                preview ? AdminReportSupport.preview(review.getComment()) : review.getComment(),
                review.getReviewer().getFullName(), review.getReviewedUser().getFullName(),
                review.getMatch() == null ? null : review.getMatch().getId(), review.getStatus().getValue(), review.getCreatedAt());
    }

    private static ReviewStatus parseReviewStatus(String value) {
        try {
            return ReviewStatus.from(value);
        } catch (IllegalArgumentException exception) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "Trang thai danh gia khong hop le.",
                    Map.of("reviewStatus", "Chi nhan all, visible, hidden hoac reported"));
        }
    }
}
