package com.courtly.api.admin;

import com.courtly.api.admin.dto.AdminReportResponses;
import com.courtly.api.admin.dto.ConcludeReportRequest;
import com.courtly.service.admin.AdminUserReportService;
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

/** 2.3.36 - quan ly nguoi dung bi bao cao. */
@Validated
@RestController
@RequestMapping("/api/v1/admin/user-reports")
@RequiredArgsConstructor
public class AdminUserReportController {

    private final AdminUserReportService reportService;

    @GetMapping
    public ResponseEntity<AdminReportResponses.ListResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "all") String status,
            @RequestParam(defaultValue = "all") @Size(max = 100, message = "Ly do toi da 100 ky tu") String reason,
            @RequestParam(required = false) @Size(max = 100, message = "Tu khoa toi da 100 ky tu") String q,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "So trang bat dau tu 0") int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "Kich thuoc trang tu 1 den 100")
            @Max(value = 100, message = "Kich thuoc trang tu 1 den 100") int size) {
        return ResponseEntity.ok(reportService.list(AdminJwt.adminId(jwt), status, reason, q, page, size));
    }

    @GetMapping("/{reportId}")
    public ResponseEntity<AdminReportResponses.Detail> detail(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID reportId) {
        return ResponseEntity.ok(reportService.get(AdminJwt.adminId(jwt), reportId));
    }

    @PostMapping("/{reportId}/start")
    public ResponseEntity<AdminReportResponses.Detail> start(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID reportId) {
        return ResponseEntity.ok(reportService.start(AdminJwt.adminId(jwt), reportId));
    }

    @PostMapping("/{reportId}/resolve")
    public ResponseEntity<AdminReportResponses.Detail> resolve(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID reportId,
                                                               @Valid @RequestBody ConcludeReportRequest request) {
        return ResponseEntity.ok(reportService.resolve(AdminJwt.adminId(jwt), reportId, request.action(), request.note()));
    }

    @PostMapping("/{reportId}/reject")
    public ResponseEntity<AdminReportResponses.Detail> reject(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID reportId,
                                                              @Valid @RequestBody ConcludeReportRequest request) {
        return ResponseEntity.ok(reportService.reject(AdminJwt.adminId(jwt), reportId, request.note()));
    }
}
