package com.courtly.api.admin;

import com.courtly.api.admin.dto.AdminReviewReportResponses;
import com.courtly.api.admin.dto.ReviewReportNoteRequest;
import com.courtly.service.admin.AdminReviewReportService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 2.3.37 - quan ly bao cao ve danh gia doi tac. */
@Validated
@RestController
@RequestMapping("/api/v1/admin/review-reports")
@RequiredArgsConstructor
public class AdminReviewReportController {

    private final AdminReviewReportService service;

    @GetMapping
    public ResponseEntity<AdminReviewReportResponses.ListResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "all") String status,
            @RequestParam(defaultValue = "all") @Size(max = 100, message = "Ly do toi da 100 ky tu") String reason,
            @RequestParam(defaultValue = "all") String reviewStatus,
            @RequestParam(required = false) @Size(max = 100, message = "Tu khoa toi da 100 ky tu") String q,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "So trang bat dau tu 0") int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "Kich thuoc trang tu 1 den 100")
            @Max(value = 100, message = "Kich thuoc trang tu 1 den 100") int size) {
        return ResponseEntity.ok(service.list(AdminJwt.adminId(jwt), status, reason, reviewStatus, q, page, size));
    }

    @GetMapping("/{reportId}")
    public ResponseEntity<AdminReviewReportResponses.Detail> detail(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID reportId) {
        return ResponseEntity.ok(service.get(AdminJwt.adminId(jwt), reportId));
    }

    @PostMapping("/{reportId}/start")
    public ResponseEntity<AdminReviewReportResponses.Detail> start(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID reportId) {
        return ResponseEntity.ok(service.start(AdminJwt.adminId(jwt), reportId));
    }

    @PostMapping("/{reportId}/hide")
    public ResponseEntity<AdminReviewReportResponses.Detail> hide(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID reportId,
                                                                  @Valid @RequestBody ReviewReportNoteRequest request) {
        return ResponseEntity.ok(service.hide(AdminJwt.adminId(jwt), reportId, request.note()));
    }

    @PostMapping("/{reportId}/keep")
    public ResponseEntity<AdminReviewReportResponses.Detail> keep(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID reportId,
                                                                  @Valid @RequestBody ReviewReportNoteRequest request) {
        return ResponseEntity.ok(service.keep(AdminJwt.adminId(jwt), reportId, request.note()));
    }

    @PostMapping("/{reportId}/restore")
    public ResponseEntity<AdminReviewReportResponses.Detail> restore(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID reportId,
                                                                     @Valid @RequestBody ReviewReportNoteRequest request) {
        return ResponseEntity.ok(service.restore(AdminJwt.adminId(jwt), reportId, request.note()));
    }
}
