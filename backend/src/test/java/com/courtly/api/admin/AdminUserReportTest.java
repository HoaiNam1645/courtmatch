package com.courtly.api.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.courtly.common.enums.ReportStatus;
import com.courtly.common.enums.ReportTargetType;
import com.courtly.common.enums.UserStatus;
import com.courtly.domain.account.Role;
import com.courtly.domain.account.User;
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

/** 2.3.36 - Quan ly nguoi dung bi bao cao. */
class AdminUserReportTest extends AdminApiTestBase {

    private static final String PATH = "/api/v1/admin/user-reports";

    @Autowired private ReportRepository reportRepository;
    @Autowired private AuditLogRepository auditLogRepository;

    private User admin;
    private String adminToken;
    private User reporter;
    private User target;

    @BeforeEach
    void setUp() throws Exception {
        ensureAdminSetup();
        admin = persistAdmin("admin-report");
        adminToken = loginAs(admin);
        reporter = persistUser("nguoi-bao-cao", Role.PLAYER);
        target = persistUser("bi-bao-cao", Role.PLAYER);
    }

    private Report report(User from, UUID targetId, ReportTargetType type, String reason, ReportStatus status, int minutesAgo) {
        Report report = new Report();
        report.setId(UUID.randomUUID());
        report.setReporter(from);
        report.setTargetType(type);
        report.setTargetId(targetId);
        report.setReason(reason);
        report.setDescription("Mo ta: " + reason);
        report.setStatus(status);
        report.setCreatedAt(Instant.now().minus(minutesAgo, ChronoUnit.MINUTES));
        return reportRepository.saveAndFlush(report);
    }

    private Report pending() {
        return report(reporter, target.getId(), ReportTargetType.USER, "Spam", ReportStatus.PENDING, 5);
    }

    private String body(String action, String note) {
        return action == null ? "{\"note\":\"" + note + "\"}" : "{\"action\":\"" + action + "\",\"note\":\"" + note + "\"}";
    }

    private org.springframework.test.web.servlet.ResultActions act(String token, String path, String json) throws Exception {
        return mockMvc.perform(post(PATH + path).header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    @DisplayName("Danh sach chi gom bao cao nguoi dung; chua xu ly len dau, sau do moi nhat truoc; kem nguoi bao cao, nguoi bi bao cao va dem theo trang thai")
    void listOrdersOpenFirst() throws Exception {
        User other = persistUser("khac", Role.PLAYER);
        Report closedNew = report(reporter, target.getId(), ReportTargetType.USER, "Quay roi", ReportStatus.RESOLVED, 1);
        Report reviewing = report(reporter, other.getId(), ReportTargetType.USER, "Gian lan", ReportStatus.REVIEWING, 30);
        Report oldPending = report(reporter, other.getId(), ReportTargetType.USER, "Spam", ReportStatus.PENDING, 90);
        Report newPending = report(reporter, target.getId(), ReportTargetType.USER, "Spam", ReportStatus.PENDING, 20);
        report(reporter, UUID.randomUUID(), ReportTargetType.VENUE, "San khong dung", ReportStatus.PENDING, 2);

        mockMvc.perform(get(PATH).param("q", "nguoi-bao-cao").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.content[*].id", contains(newPending.getId().toString(), oldPending.getId().toString(),
                        reviewing.getId().toString(), closedNew.getId().toString())))
                .andExpect(jsonPath("$.content[0].reporter.email").value(reporter.getEmail()))
                .andExpect(jsonPath("$.content[0].target.email").value(target.getEmail()))
                .andExpect(jsonPath("$.content[0].target.status").value("active"))
                .andExpect(jsonPath("$.content[0].target.exists").value(true))
                .andExpect(jsonPath("$.content[0].descriptionPreview").value("Mo ta: Spam"))
                .andExpect(jsonPath("$.statusCounts.pending").isNumber())
                .andExpect(jsonPath("$.statusCounts.reviewing").isNumber())
                .andExpect(jsonPath("$.statusCounts.resolved").isNumber())
                .andExpect(jsonPath("$.statusCounts.rejected").isNumber())
                .andExpect(jsonPath("$.reasons", hasItem("Spam")));
    }

    @Test
    @DisplayName("Loc theo trang thai, ly do, tu khoa (khong dau, ca email) va phan trang")
    void filters() throws Exception {
        report(reporter, target.getId(), ReportTargetType.USER, "Spam", ReportStatus.PENDING, 5);
        report(reporter, target.getId(), ReportTargetType.USER, "Gian lan", ReportStatus.REVIEWING, 6);
        report(reporter, target.getId(), ReportTargetType.USER, "Spam", ReportStatus.REJECTED, 7);

        mockMvc.perform(get(PATH).param("status", "pending").param("q", target.getEmail()).header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get(PATH).param("reason", "Spam").param("q", "bi-bao-cao").header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(get(PATH).param("q", "NGUOI DUNG BI-BAO-CAO").header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$.totalElements").value(3));
        mockMvc.perform(get(PATH).param("q", "nguoi-bao-cao").param("size", "2").param("page", "1").header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$.content", hasSize(1))).andExpect(jsonPath("$.totalPages").value(2));
        mockMvc.perform(get(PATH).param("q", "khong-ton-tai-xyz").header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$.totalElements").value(0)).andExpect(jsonPath("$.content", hasSize(0)));
        mockMvc.perform(get(PATH).param("q", "100%").header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("Chi tiet: nguoi bao cao, nguoi bi bao cao, vai tro, tong bao cao da nhan va cac bao cao khac")
    void detail() throws Exception {
        report(reporter, target.getId(), ReportTargetType.USER, "Quay roi", ReportStatus.RESOLVED, 100);
        report(persistUser("khac-nua", Role.PLAYER), target.getId(), ReportTargetType.USER, "Gian lan", ReportStatus.PENDING, 50);
        Report current = pending();

        mockMvc.perform(get(PATH + "/" + current.getId()).header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reason").value("Spam"))
                .andExpect(jsonPath("$.description").value("Mo ta: Spam"))
                .andExpect(jsonPath("$.reporter.fullName").value(reporter.getFullName()))
                .andExpect(jsonPath("$.target.fullName").value(target.getFullName()))
                .andExpect(jsonPath("$.roles", contains("player")))
                .andExpect(jsonPath("$.totalReceived").value(3))
                .andExpect(jsonPath("$.related", hasSize(2)))
                .andExpect(jsonPath("$.related[0].reason").value("Gian lan"))
                .andExpect(jsonPath("$.canStart").value(true))
                .andExpect(jsonPath("$.canConclude").value(false))
                .andExpect(jsonPath("$.canLockAccount").value(true))
                .andExpect(jsonPath("$.handling").doesNotExist());
        mockMvc.perform(get(PATH + "/" + UUID.randomUUID()).header("Authorization", bearer(adminToken))).andExpect(status().isNotFound());
        mockMvc.perform(get(PATH + "/abc").header("Authorization", bearer(adminToken))).andExpect(status().isBadRequest());
        Report venueReport = report(reporter, UUID.randomUUID(), ReportTargetType.VENUE, "San", ReportStatus.PENDING, 3);
        mockMvc.perform(get(PATH + "/" + venueReport.getId()).header("Authorization", bearer(adminToken))).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Tai khoan bi bao cao da bi xoa: van xem duoc bao cao, danh dau khong con ton tai, khong khoa duoc")
    void missingTarget() throws Exception {
        Report orphan = report(reporter, UUID.randomUUID(), ReportTargetType.USER, "Spam", ReportStatus.REVIEWING, 5);
        mockMvc.perform(get(PATH + "/" + orphan.getId()).header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.target.exists").value(false))
                .andExpect(jsonPath("$.target.fullName").doesNotExist())
                .andExpect(jsonPath("$.canLockAccount").value(false))
                .andExpect(jsonPath("$.roles", hasSize(0)));
        act(adminToken, "/" + orphan.getId() + "/resolve", body("account_banned", "Khoa ngay")).andExpect(status().isConflict());
        act(adminToken, "/" + orphan.getId() + "/resolve", body("warning", "Da nhac nho")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Nhan xu ly: pending -> reviewing, ghi nguoi phu trach va nhat ky; nhan lai bi chan")
    void start() throws Exception {
        Report report = pending();
        act(adminToken, "/" + report.getId() + "/start", "{}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("reviewing"))
                .andExpect(jsonPath("$.canStart").value(false))
                .andExpect(jsonPath("$.canConclude").value(true));
        Report saved = reportRepository.findById(report.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(ReportStatus.REVIEWING);
        assertThat(saved.getHandledBy().getId()).isEqualTo(admin.getId());
        assertThat(saved.getHandledAt()).isNull();
        assertThat(auditLogRepository.findAll().stream().filter(log -> report.getId().equals(log.getEntityId())
                && "report.start".equals(log.getAction()))).hasSize(1);

        act(adminToken, "/" + report.getId() + "/start", "{}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REPORT_STATE_INVALID"));
    }

    @Test
    @DisplayName("Ket luan phai nhan xu ly truoc; chap nhan voi huong xu ly hop le ghi ghi chu, nguoi xu ly, thoi diem; khong sua lai duoc")
    void resolveWarning() throws Exception {
        Report report = pending();
        act(adminToken, "/" + report.getId() + "/resolve", body("warning", "Da nhac nho nguoi dung"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REPORT_STATE_INVALID"));
        act(adminToken, "/" + report.getId() + "/start", "{}").andExpect(status().isOk());

        act(adminToken, "/" + report.getId() + "/resolve", body("warning", "Da nhac nho nguoi dung"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("resolved"))
                .andExpect(jsonPath("$.handling.action").value("warning"))
                .andExpect(jsonPath("$.handling.note").value("Da nhac nho nguoi dung"))
                .andExpect(jsonPath("$.handling.handledBy").value(admin.getFullName()))
                .andExpect(jsonPath("$.handling.handledAt").exists())
                .andExpect(jsonPath("$.canConclude").value(false))
                .andExpect(jsonPath("$.target.status").value("active"));
        assertThat(userRepository.findById(target.getId()).orElseThrow().getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(auditLogRepository.findAll().stream().filter(log -> report.getId().equals(log.getEntityId())
                && "report.resolve".equals(log.getAction()))).hasSize(1);

        act(adminToken, "/" + report.getId() + "/resolve", body("other", "Doi y")).andExpect(status().isConflict());
        act(adminToken, "/" + report.getId() + "/reject", body(null, "Doi y")).andExpect(status().isConflict());
        act(adminToken, "/" + report.getId() + "/start", "{}").andExpect(status().isConflict());
        assertThat(reportRepository.findById(report.getId()).orElseThrow().getStatus()).isEqualTo(ReportStatus.RESOLVED);
    }

    @Test
    @DisplayName("Chap nhan + khoa tai khoan: tai khoan bi khoa, nguoi do het dang nhap duoc, nhat ky co ca hai hanh dong")
    void resolveWithBan() throws Exception {
        Report report = pending();
        act(adminToken, "/" + report.getId() + "/start", "{}").andExpect(status().isOk());
        act(adminToken, "/" + report.getId() + "/resolve", body("account_banned", "Spam nhieu lan"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.handling.action").value("account_banned"))
                .andExpect(jsonPath("$.target.status").value("banned"))
                .andExpect(jsonPath("$.canLockAccount").value(false));
        assertThat(userRepository.findById(target.getId()).orElseThrow().getStatus()).isEqualTo(UserStatus.BANNED);
        assertThat(auditLogRepository.findAll().stream().filter(log -> target.getId().equals(log.getEntityId())
                && "user.status.change".equals(log.getAction()))).hasSize(1);
        assertThat(auditLogRepository.findAll().stream().filter(log -> report.getId().equals(log.getEntityId())
                && "report.resolve".equals(log.getAction()))).hasSize(1);
    }

    @Test
    @DisplayName("Chap nhan + ngung tai khoan: tai khoan chuyen inactive")
    void resolveWithInactive() throws Exception {
        Report report = pending();
        act(adminToken, "/" + report.getId() + "/start", "{}").andExpect(status().isOk());
        act(adminToken, "/" + report.getId() + "/resolve", body("account_inactive", "Tam ngung de xac minh"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.target.status").value("inactive"));
    }

    @Test
    @DisplayName("Khoa tai khoan khong thanh cong thi bao cao khong bi ket luan (rollback): tai khoan da khoa san, thieu quyen user.manage")
    void banFailureRollsBack() throws Exception {
        Report report = pending();
        act(adminToken, "/" + report.getId() + "/start", "{}").andExpect(status().isOk());
        target.setStatus(UserStatus.BANNED);
        userRepository.saveAndFlush(target);
        act(adminToken, "/" + report.getId() + "/resolve", body("account_banned", "Khoa tai khoan"))
                .andExpect(status().isConflict());
        mockMvc.perform(get(PATH + "/" + report.getId()).header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$.status").value("reviewing")).andExpect(jsonPath("$.canLockAccount").value(false));
        // Tai khoan da khoa san van co the ket luan bang canh cao.
        act(adminToken, "/" + report.getId() + "/resolve", body("warning", "Da khoa tu truoc")).andExpect(status().isOk());

        Report second = report(reporter, persistUser("khac-hai", Role.PLAYER).getId(), ReportTargetType.USER, "Spam", ReportStatus.REVIEWING, 4);
        Role adminRole = roleRepository.findByCode(Role.ADMIN).orElseThrow();
        adminRole.getRolePermissions().removeIf(item -> "user.manage".equals(item.getPermission().getCode()));
        roleRepository.saveAndFlush(adminRole);
        mockMvc.perform(get(PATH + "/" + second.getId()).header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$.canLockAccount").value(false));
        act(adminToken, "/" + second.getId() + "/resolve", body("account_banned", "Khoa")).andExpect(status().isForbidden());
        act(adminToken, "/" + second.getId() + "/resolve", body("warning", "Canh cao thoi")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Tu choi bao cao: can ghi chu, khong co huong xu ly, khong dong den tai khoan; khong sua lai duoc")
    void rejectReport() throws Exception {
        Report report = pending();
        act(adminToken, "/" + report.getId() + "/reject", body(null, "Khong co co so")).andExpect(status().isConflict());
        act(adminToken, "/" + report.getId() + "/start", "{}").andExpect(status().isOk());
        act(adminToken, "/" + report.getId() + "/reject", "{\"note\":\"  \"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.note").exists());
        act(adminToken, "/" + report.getId() + "/reject", "{\"note\":\"ab\"}").andExpect(status().isBadRequest());
        act(adminToken, "/" + report.getId() + "/reject", "{}").andExpect(status().isBadRequest());
        act(adminToken, "/" + report.getId() + "/reject", body(null, "x".repeat(1001))).andExpect(status().isBadRequest());

        act(adminToken, "/" + report.getId() + "/reject", body("account_banned", "Khong co co so ro rang"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("rejected"))
                .andExpect(jsonPath("$.handling.action").doesNotExist())
                .andExpect(jsonPath("$.handling.note").value("Khong co co so ro rang"));
        assertThat(userRepository.findById(target.getId()).orElseThrow().getStatus()).isEqualTo(UserStatus.ACTIVE);
        act(adminToken, "/" + report.getId() + "/resolve", body("warning", "Doi y")).andExpect(status().isConflict());
        assertThat(auditLogRepository.findAll().stream().filter(log -> report.getId().equals(log.getEntityId())
                && "report.reject".equals(log.getAction()))).hasSize(1);
    }

    @Test
    @DisplayName("Chap nhan: huong xu ly va ghi chu bat buoc, sai thi 400 va bao cao van dang reviewing")
    void resolveValidation() throws Exception {
        Report report = pending();
        act(adminToken, "/" + report.getId() + "/start", "{}").andExpect(status().isOk());
        act(adminToken, "/" + report.getId() + "/resolve", body(null, "Ghi chu hop le")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.action").exists());
        act(adminToken, "/" + report.getId() + "/resolve", body("delete_everything", "Ghi chu hop le")).andExpect(status().isBadRequest());
        act(adminToken, "/" + report.getId() + "/resolve", body("warning", "")).andExpect(status().isBadRequest());
        assertThat(reportRepository.findById(report.getId()).orElseThrow().getStatus()).isEqualTo(ReportStatus.REVIEWING);
    }

    @Test
    @DisplayName("Khong xu ly bao cao ve chinh minh; khong tu khoa minh")
    void selfReport() throws Exception {
        Report report = report(reporter, admin.getId(), ReportTargetType.USER, "Lam dung quyen", ReportStatus.PENDING, 5);
        mockMvc.perform(get(PATH + "/" + report.getId()).header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$.selfTarget").value(true)).andExpect(jsonPath("$.canStart").value(false))
                .andExpect(jsonPath("$.canLockAccount").value(false));
        act(adminToken, "/" + report.getId() + "/start", "{}").andExpect(status().isConflict());
        // Quan tri vien khac van xu ly duoc bao cao nay.
        String otherAdminToken = loginAs(persistAdmin("admin-khac"));
        act(otherAdminToken, "/" + report.getId() + "/start", "{}").andExpect(status().isOk());
        act(otherAdminToken, "/" + report.getId() + "/resolve", body("account_banned", "Lam dung quyen quan tri")).andExpect(status().isOk());
        assertThat(userRepository.findById(admin.getId()).orElseThrow().getStatus()).isEqualTo(UserStatus.BANNED);
    }

    @Test
    @DisplayName("Tham so sai 400; can quyen report.handle; chu san, nguoi choi 403; chua dang nhap 401")
    void validationAndAccess() throws Exception {
        Report report = pending();
        mockMvc.perform(get(PATH).param("status", "xyz").header("Authorization", bearer(adminToken)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.status").exists());
        mockMvc.perform(get(PATH).param("size", "101").header("Authorization", bearer(adminToken))).andExpect(status().isBadRequest());
        mockMvc.perform(get(PATH).param("page", "-1").header("Authorization", bearer(adminToken))).andExpect(status().isBadRequest());
        mockMvc.perform(get(PATH).param("q", "x".repeat(101)).header("Authorization", bearer(adminToken))).andExpect(status().isBadRequest());
        mockMvc.perform(get(PATH).header("Authorization", bearer(loginAs(persistOwner("o"))))).andExpect(status().isForbidden());
        mockMvc.perform(get(PATH).header("Authorization", bearer(loginAs(reporter)))).andExpect(status().isForbidden());
        act(loginAs(reporter), "/" + report.getId() + "/start", "{}").andExpect(status().isForbidden());
        mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());

        Role adminRole = roleRepository.findByCode(Role.ADMIN).orElseThrow();
        adminRole.getRolePermissions().removeIf(item -> "report.handle".equals(item.getPermission().getCode()));
        roleRepository.saveAndFlush(adminRole);
        mockMvc.perform(get(PATH).header("Authorization", bearer(adminToken)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        mockMvc.perform(get(PATH + "/" + report.getId()).header("Authorization", bearer(adminToken))).andExpect(status().isForbidden());
        act(adminToken, "/" + report.getId() + "/start", "{}").andExpect(status().isForbidden());
        assertThat(reportRepository.findById(report.getId()).orElseThrow().getStatus()).isEqualTo(ReportStatus.PENDING);
    }
}
