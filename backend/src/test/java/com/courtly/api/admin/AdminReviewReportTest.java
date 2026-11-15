package com.courtly.api.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.courtly.common.enums.ReportStatus;
import com.courtly.common.enums.ReportTargetType;
import com.courtly.common.enums.ReviewStatus;
import com.courtly.domain.account.Role;
import com.courtly.domain.account.User;
import com.courtly.domain.review.PartnerReview;
import com.courtly.domain.review.PartnerReviewRepository;
import com.courtly.domain.review.Report;
import com.courtly.domain.review.ReportRepository;
import com.courtly.domain.system.AuditLogRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/** 2.3.37 - Quan ly bao cao ve danh gia doi tac. */
class AdminReviewReportTest extends AdminApiTestBase {

    private static final String PATH = "/api/v1/admin/review-reports";

    @Autowired private ReportRepository reportRepository;
    @Autowired private PartnerReviewRepository reviewRepository;
    @Autowired private AuditLogRepository auditLogRepository;

    private User admin;
    private String adminToken;
    private User reviewer;
    private User reviewed;
    private User reporter;

    @BeforeEach
    void setUp() throws Exception {
        ensureAdminSetup();
        admin = persistAdmin("admin-review-report");
        adminToken = loginAs(admin);
        reviewer = persistUser("nguoi-danh-gia", Role.PLAYER);
        reviewed = persistUser("duoc-danh-gia", Role.PLAYER);
        reporter = persistUser("nguoi-bao-cao", Role.PLAYER);
    }

    private PartnerReview review(User by, User about, int rating, String comment, ReviewStatus status) {
        PartnerReview review = new PartnerReview();
        review.setId(UUID.randomUUID());
        review.setReviewer(by);
        review.setReviewedUser(about);
        review.setRating((short) rating);
        review.setComment(comment);
        review.setStatus(status);
        review.setCreatedAt(Instant.now().minus(1, ChronoUnit.DAYS));
        return reviewRepository.saveAndFlush(review);
    }

    private Report report(PartnerReview review, String reason, ReportStatus status, int minutesAgo) {
        return report(review.getId(), reporter, reason, status, minutesAgo);
    }

    private Report report(UUID targetId, User from, String reason, ReportStatus status, int minutesAgo) {
        Report report = new Report();
        report.setId(UUID.randomUUID());
        report.setReporter(from);
        report.setTargetType(ReportTargetType.PARTNER_REVIEW);
        report.setTargetId(targetId);
        report.setReason(reason);
        report.setDescription("Noi dung bao cao: " + reason);
        report.setStatus(status);
        report.setCreatedAt(Instant.now().minus(minutesAgo, ChronoUnit.MINUTES));
        return reportRepository.saveAndFlush(report);
    }

    private PartnerReview defaultReview() {
        return review(reviewer, reviewed, 1, "Choi te, thai do xau", ReviewStatus.VISIBLE);
    }

    private org.springframework.test.web.servlet.ResultActions act(String token, String path, String json) throws Exception {
        return mockMvc.perform(post(PATH + path).header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static String note(String text) {
        return "{\"note\":\"" + text + "\"}";
    }

    private String publicReviews(User viewer, User player) throws Exception {
        return mockMvc.perform(get("/api/v1/players/" + player.getId() + "/partner-reviews").header("Authorization", bearer(loginAs(viewer))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    @Test
    @DisplayName("Danh sach chi gom bao cao ve danh gia: chua xu ly len dau, kem noi dung danh gia, chi hien ten, dem theo trang thai")
    void list() throws Exception {
        PartnerReview first = defaultReview();
        PartnerReview second = review(reviewer, persistUser("khac", Role.PLAYER), 5, "Rat tot", ReviewStatus.VISIBLE);
        Report closed = report(first, "Noi dung xuc pham", ReportStatus.RESOLVED, 1);
        Report older = report(first, "Spam", ReportStatus.PENDING, 40);
        Report newer = report(second, "Sai su that", ReportStatus.PENDING, 5);
        Report userReport = report(reviewed.getId(), reporter, "User", ReportStatus.PENDING, 3);
        userReport.setTargetType(ReportTargetType.USER);
        reportRepository.saveAndFlush(userReport);

        mockMvc.perform(get(PATH).param("q", "nguoi-bao-cao").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", contains(newer.getId().toString(), older.getId().toString(), closed.getId().toString())));
        mockMvc.perform(get(PATH).param("q", "Nguoi Dung duoc-danh-gia").header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].review.exists").value(true))
                .andExpect(jsonPath("$.content[0].review.rating").value(1))
                .andExpect(jsonPath("$.content[0].review.reviewerName").value(reviewer.getFullName()))
                .andExpect(jsonPath("$.content[0].review.reviewedName").value(reviewed.getFullName()))
                .andExpect(jsonPath("$.content[0].review.status").value("visible"))
                .andExpect(jsonPath("$.content[0].review.reviewer").doesNotExist())
                .andExpect(jsonPath("$.content[0].review.email").doesNotExist())
                .andExpect(jsonPath("$.statusCounts.pending").isNumber())
                .andExpect(jsonPath("$.reasons").isArray());
    }

    @Test
    @DisplayName("Loc theo trang thai bao cao, trang thai danh gia, ly do, tu khoa (ca noi dung danh gia) va phan trang")
    void filters() throws Exception {
        PartnerReview visible = review(reviewer, reviewed, 2, "Tre gio nhieu lan", ReviewStatus.VISIBLE);
        PartnerReview hidden = review(reviewer, reviewed, 1, "Noi tuc", ReviewStatus.HIDDEN);
        report(visible, "Spam", ReportStatus.PENDING, 10);
        report(hidden, "Noi dung xuc pham", ReportStatus.RESOLVED, 20);
        report(hidden, "Spam", ReportStatus.REJECTED, 30);

        mockMvc.perform(get(PATH).param("q", "nguoi-danh-gia").param("reviewStatus", "hidden").header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(get(PATH).param("q", "nguoi-danh-gia").param("reviewStatus", "visible").header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get(PATH).param("q", "nguoi-danh-gia").param("status", "resolved").header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get(PATH).param("q", "nguoi-danh-gia").param("reason", "Spam").header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(get(PATH).param("q", "TRE GIO").header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get(PATH).param("q", "nguoi-danh-gia").param("size", "2").param("page", "1").header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$.content", hasSize(1))).andExpect(jsonPath("$.totalPages").value(2));
        mockMvc.perform(get(PATH).param("q", "khong-co-xyz").header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("Chi tiet: noi dung danh gia, nguoi danh gia, nguoi duoc danh gia (chi ten), tran lien quan, bao cao khac cung danh gia")
    void detail() throws Exception {
        PartnerReview review = defaultReview();
        report(review, "Spam", ReportStatus.RESOLVED, 100);
        Report current = report(review, "Noi dung xuc pham", ReportStatus.PENDING, 5);

        mockMvc.perform(get(PATH + "/" + current.getId()).header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reason").value("Noi dung xuc pham"))
                .andExpect(jsonPath("$.description").value("Noi dung bao cao: Noi dung xuc pham"))
                .andExpect(jsonPath("$.reporterName").value(reporter.getFullName()))
                .andExpect(jsonPath("$.review.rating").value(1))
                .andExpect(jsonPath("$.review.comment").value("Choi te, thai do xau"))
                .andExpect(jsonPath("$.review.reviewerName").value(reviewer.getFullName()))
                .andExpect(jsonPath("$.review.reviewedName").value(reviewed.getFullName()))
                .andExpect(jsonPath("$.review.status").value("visible"))
                .andExpect(jsonPath("$.totalReports").value(2))
                .andExpect(jsonPath("$.related", hasSize(1)))
                .andExpect(jsonPath("$.canStart").value(true))
                .andExpect(jsonPath("$.canConclude").value(false))
                .andExpect(jsonPath("$.canHide").value(false))
                .andExpect(jsonPath("$.involved").value(false));
        String body = mockMvc.perform(get(PATH + "/" + current.getId()).header("Authorization", bearer(adminToken)))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain(reviewer.getEmail()).doesNotContain(reviewed.getEmail()).doesNotContain(reporter.getEmail());
        mockMvc.perform(get(PATH + "/" + UUID.randomUUID()).header("Authorization", bearer(adminToken))).andExpect(status().isNotFound());
        mockMvc.perform(get(PATH + "/abc").header("Authorization", bearer(adminToken))).andExpect(status().isBadRequest());
        Report userReport = report(reviewed.getId(), reporter, "User", ReportStatus.PENDING, 3);
        userReport.setTargetType(ReportTargetType.USER);
        reportRepository.saveAndFlush(userReport);
        mockMvc.perform(get(PATH + "/" + userReport.getId()).header("Authorization", bearer(adminToken))).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Go danh gia: danh gia chuyen hidden, bao cao resolved kem ghi chu, bien mat khoi ho so cong khai va diem trung binh")
    void hideReview() throws Exception {
        PartnerReview bad = defaultReview();
        review(persistUser("khac-hai", Role.PLAYER), reviewed, 5, "Choi rat hay", ReviewStatus.VISIBLE);
        Report report = report(bad, "Noi dung xuc pham", ReportStatus.PENDING, 5);
        mockMvc.perform(get("/api/v1/players/" + reviewed.getId() + "/partner-reviews").header("Authorization", bearer(loginAs(reporter))))
                .andExpect(jsonPath("$.totalReviews").value(2)).andExpect(jsonPath("$.averageRating").value(3.0));

        act(adminToken, "/" + report.getId() + "/hide", note("Noi dung xuc pham")).andExpect(status().isConflict());
        act(adminToken, "/" + report.getId() + "/start", "{}").andExpect(status().isOk());
        act(adminToken, "/" + report.getId() + "/hide", note("Vi pham quy tac cong dong"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("resolved"))
                .andExpect(jsonPath("$.review.status").value("hidden"))
                .andExpect(jsonPath("$.handling.action").value("review_hidden"))
                .andExpect(jsonPath("$.handling.note").value("Vi pham quy tac cong dong"))
                .andExpect(jsonPath("$.handling.handledBy").value(admin.getFullName()))
                .andExpect(jsonPath("$.canRestore").value(true))
                .andExpect(jsonPath("$.canConclude").value(false));
        assertThat(reviewRepository.findById(bad.getId()).orElseThrow().getStatus()).isEqualTo(ReviewStatus.HIDDEN);
        mockMvc.perform(get("/api/v1/players/" + reviewed.getId() + "/partner-reviews").header("Authorization", bearer(loginAs(reporter))))
                .andExpect(jsonPath("$.totalReviews").value(1)).andExpect(jsonPath("$.averageRating").value(5.0));
        assertThat(auditLogRepository.findAll().stream().filter(log -> bad.getId().equals(log.getEntityId())
                && "review.hide".equals(log.getAction()))).hasSize(1);

        act(adminToken, "/" + report.getId() + "/hide", note("Lan nua")).andExpect(status().isConflict());
        act(adminToken, "/" + report.getId() + "/keep", note("Doi y")).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Giu nguyen danh gia: bao cao rejected, danh gia van visible; danh gia dang reported tro lai visible")
    void keepReview() throws Exception {
        PartnerReview fine = review(reviewer, reviewed, 4, "Choi tot nhung hay tre", ReviewStatus.REPORTED);
        Report report = report(fine, "Sai su that", ReportStatus.PENDING, 5);
        act(adminToken, "/" + report.getId() + "/start", "{}").andExpect(status().isOk());
        act(adminToken, "/" + report.getId() + "/keep", "{\"note\":\"  \"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.note").exists());
        act(adminToken, "/" + report.getId() + "/keep", note("Khong co co so go danh gia"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("rejected"))
                .andExpect(jsonPath("$.handling.action").value("review_kept"))
                .andExpect(jsonPath("$.review.status").value("visible"))
                .andExpect(jsonPath("$.canRestore").value(false));
        assertThat(reviewRepository.findById(fine.getId()).orElseThrow().getStatus()).isEqualTo(ReviewStatus.VISIBLE);
        act(adminToken, "/" + report.getId() + "/hide", note("Doi y")).andExpect(status().isConflict());
        mockMvc.perform(get("/api/v1/players/" + reviewed.getId() + "/partner-reviews").header("Authorization", bearer(loginAs(reporter))))
                .andExpect(jsonPath("$.totalReviews").value(1));
    }

    @Test
    @DisplayName("Giu nguyen khi danh gia da bi an truoc do: bao cao rejected nhung danh gia van hidden")
    void keepAlreadyHidden() throws Exception {
        PartnerReview hidden = review(reviewer, reviewed, 1, "Da an", ReviewStatus.HIDDEN);
        Report report = report(hidden, "Spam", ReportStatus.REVIEWING, 5);
        act(adminToken, "/" + report.getId() + "/keep", note("Bao cao khong dung")).andExpect(status().isOk());
        assertThat(reviewRepository.findById(hidden.getId()).orElseThrow().getStatus()).isEqualTo(ReviewStatus.HIDDEN);
    }

    @Test
    @DisplayName("Go danh gia da an san bang bao cao khac: chi ket luan bao cao, khong ghi nhat ky an lan hai")
    void hideAlreadyHiddenIsIdempotent() throws Exception {
        PartnerReview review = defaultReview();
        Report first = report(review, "Spam", ReportStatus.REVIEWING, 10);
        Report second = report(review, "Xuc pham", ReportStatus.REVIEWING, 5);
        act(adminToken, "/" + first.getId() + "/hide", note("Vi pham")).andExpect(status().isOk());
        act(adminToken, "/" + second.getId() + "/hide", note("Trung voi bao cao truoc")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("resolved"));
        assertThat(auditLogRepository.findAll().stream().filter(log -> review.getId().equals(log.getEntityId())
                && "review.hide".equals(log.getAction()))).hasSize(1);
    }

    @Test
    @DisplayName("Khoi phuc danh gia da go: hidden -> visible, hien lai o ho so; bao cao giu nguyen resolved; can ghi chu; khong khoi phuc hai lan")
    void restoreReview() throws Exception {
        PartnerReview review = defaultReview();
        Report report = report(review, "Noi dung xuc pham", ReportStatus.REVIEWING, 5);
        act(adminToken, "/" + report.getId() + "/restore", note("Chua go")).andExpect(status().isConflict());
        act(adminToken, "/" + report.getId() + "/hide", note("Vi pham quy tac")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/players/" + reviewed.getId() + "/partner-reviews").header("Authorization", bearer(loginAs(reporter))))
                .andExpect(jsonPath("$.totalReviews").value(0));

        act(adminToken, "/" + report.getId() + "/restore", "{}").andExpect(status().isBadRequest());
        act(adminToken, "/" + report.getId() + "/restore", note("Xem lai thay danh gia hop le"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.review.status").value("visible"))
                .andExpect(jsonPath("$.status").value("resolved"))
                .andExpect(jsonPath("$.handling.action").value("review_hidden"))
                .andExpect(jsonPath("$.canRestore").value(false));
        mockMvc.perform(get("/api/v1/players/" + reviewed.getId() + "/partner-reviews").header("Authorization", bearer(loginAs(reporter))))
                .andExpect(jsonPath("$.totalReviews").value(1));
        assertThat(auditLogRepository.findAll().stream().filter(log -> review.getId().equals(log.getEntityId())
                && "review.restore".equals(log.getAction()))).hasSize(1);
        act(adminToken, "/" + report.getId() + "/restore", note("Lan nua")).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Danh gia khong con ton tai: van xem duoc bao cao, khong go / khoi phuc duoc, van giu nguyen (dong bao cao) duoc")
    void missingReview() throws Exception {
        Report report = report(UUID.randomUUID(), reporter, "Spam", ReportStatus.REVIEWING, 5);
        mockMvc.perform(get(PATH + "/" + report.getId()).header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.review.exists").value(false))
                .andExpect(jsonPath("$.review.comment").doesNotExist())
                .andExpect(jsonPath("$.canHide").value(false))
                .andExpect(jsonPath("$.canConclude").value(true));
        act(adminToken, "/" + report.getId() + "/hide", note("Go danh gia")).andExpect(status().isConflict());
        act(adminToken, "/" + report.getId() + "/keep", note("Danh gia da khong con")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Quan tri vien la mot ben cua danh gia khong xu ly duoc bao cao nay, quan tri vien khac thi duoc")
    void involvedAdmin() throws Exception {
        PartnerReview review = review(admin, reviewed, 1, "Danh gia cua admin", ReviewStatus.VISIBLE);
        Report report = report(review, "Spam", ReportStatus.PENDING, 5);
        mockMvc.perform(get(PATH + "/" + report.getId()).header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$.involved").value(true)).andExpect(jsonPath("$.canStart").value(false));
        act(adminToken, "/" + report.getId() + "/start", "{}").andExpect(status().isConflict());
        String other = loginAs(persistAdmin("admin-khac"));
        act(other, "/" + report.getId() + "/start", "{}").andExpect(status().isOk());
        act(other, "/" + report.getId() + "/hide", note("Vi pham")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Nhan xu ly: pending -> reviewing, nhan lai bi chan; ket luan phai nhan truoc; ghi chu bat buoc")
    void lifecycle() throws Exception {
        PartnerReview review = defaultReview();
        Report report = report(review, "Spam", ReportStatus.PENDING, 5);
        act(adminToken, "/" + report.getId() + "/hide", note("Go ngay")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REPORT_STATE_INVALID"));
        act(adminToken, "/" + report.getId() + "/start", "{}").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("reviewing")).andExpect(jsonPath("$.canHide").value(true));
        act(adminToken, "/" + report.getId() + "/start", "{}").andExpect(status().isConflict());
        act(adminToken, "/" + report.getId() + "/hide", note("ab")).andExpect(status().isBadRequest());
        act(adminToken, "/" + report.getId() + "/hide", note("x".repeat(1001))).andExpect(status().isBadRequest());
        assertThat(reviewRepository.findById(review.getId()).orElseThrow().getStatus()).isEqualTo(ReviewStatus.VISIBLE);
        assertThat(reportRepository.findById(report.getId()).orElseThrow().getStatus()).isEqualTo(ReportStatus.REVIEWING);
    }

    @Test
    @DisplayName("Tham so sai 400; can quyen report.handle; chu san, nguoi choi 403; chua dang nhap 401")
    void validationAndAccess() throws Exception {
        Report report = report(defaultReview(), "Spam", ReportStatus.PENDING, 5);
        mockMvc.perform(get(PATH).param("status", "xyz").header("Authorization", bearer(adminToken)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.status").exists());
        mockMvc.perform(get(PATH).param("reviewStatus", "xyz").header("Authorization", bearer(adminToken)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.reviewStatus").exists());
        mockMvc.perform(get(PATH).param("size", "101").header("Authorization", bearer(adminToken))).andExpect(status().isBadRequest());
        mockMvc.perform(get(PATH).header("Authorization", bearer(loginAs(persistOwner("o"))))).andExpect(status().isForbidden());
        mockMvc.perform(get(PATH).header("Authorization", bearer(loginAs(reporter)))).andExpect(status().isForbidden());
        act(loginAs(reporter), "/" + report.getId() + "/hide", note("Thu go")).andExpect(status().isForbidden());
        mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());

        Role adminRole = roleRepository.findByCode(Role.ADMIN).orElseThrow();
        adminRole.getRolePermissions().removeIf(item -> "report.handle".equals(item.getPermission().getCode()));
        roleRepository.saveAndFlush(adminRole);
        mockMvc.perform(get(PATH).header("Authorization", bearer(adminToken)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        act(adminToken, "/" + report.getId() + "/start", "{}").andExpect(status().isForbidden());
        act(adminToken, "/" + report.getId() + "/restore", note("Khoi phuc")).andExpect(status().isForbidden());
    }
}
