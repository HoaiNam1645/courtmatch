package com.courtly.api.admin;

import com.courtly.api.admin.dto.AdminDisputeResponses;
import com.courtly.service.admin.AdminDisputeDecisionService;
import com.courtly.service.admin.AdminDisputeService;
import com.courtly.api.admin.dto.ReviewReportNoteRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 2.3.98 - 2.3.102: quan tri vien xu ly bao cao ve don dat san. */
@Validated
@RestController
@RequestMapping("/api/v1/admin/booking-disputes")
@RequiredArgsConstructor
public class AdminDisputeController {

    private final AdminDisputeService service;
    private final AdminDisputeDecisionService decisionService;
    private final com.courtly.service.admin.AdminDisputeVenueStatsService venueStatsService;

    @GetMapping
    public ResponseEntity<AdminDisputeResponses.ListResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "pending") String status,
            @RequestParam(required = false) @Size(max = 100, message = "Tu khoa toi da 100 ky tu") String q,
            @RequestParam(defaultValue = "all") @Size(max = 40, message = "Loai toi da 40 ky tu") String category,
            @RequestParam(defaultValue = "all") String refund,
            @RequestParam(required = false) UUID venueId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "So trang bat dau tu 0") int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "Kich thuoc trang tu 1 den 100")
            @Max(value = 100, message = "Kich thuoc trang tu 1 den 100") int size) {
        return ResponseEntity.ok(service.list(AdminJwt.adminId(jwt), status, q, category, refund, venueId, from, to, page, size));
    }

    /** 2.3.103 - thong ke bao cao theo san. */
    @GetMapping("/venue-stats")
    public ResponseEntity<AdminDisputeResponses.VenueStatsResponse> venueStats(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) @Size(max = 100, message = "Tu khoa toi da 100 ky tu") String q,
            @RequestParam(defaultValue = "lost") String sort,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "So trang bat dau tu 0") int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "Kich thuoc trang tu 1 den 100")
            @Max(value = 100, message = "Kich thuoc trang tu 1 den 100") int size) {
        return ResponseEntity.ok(venueStatsService.list(AdminJwt.adminId(jwt), from, to, q, sort, page, size));
    }

    @GetMapping("/{disputeId}")
    public ResponseEntity<AdminDisputeResponses.Detail> detail(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID disputeId) {
        return ResponseEntity.ok(service.get(AdminJwt.adminId(jwt), disputeId));
    }

    /** 2.3.100 - hoan tien toan phan (dung so nguoi choi yeu cau). */
    @PostMapping("/{disputeId}/refund-full")
    public ResponseEntity<AdminDisputeResponses.Detail> refundFull(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID disputeId,
                                                                   @Valid @RequestBody ReviewReportNoteRequest request) {
        return ResponseEntity.ok(decisionService.refundFull(AdminJwt.adminId(jwt), disputeId, request.note()));
    }

    /** 2.3.101 - hoan tien mot nua (50% so yeu cau, lam tron xuong 1.000d, toi thieu 1.000d). */
    @PostMapping("/{disputeId}/refund-half")
    public ResponseEntity<AdminDisputeResponses.Detail> refundHalf(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID disputeId,
                                                                   @Valid @RequestBody ReviewReportNoteRequest request) {
        return ResponseEntity.ok(decisionService.refundHalf(AdminJwt.adminId(jwt), disputeId, request.note()));
    }

    /** 2.3.102 - bac bo yeu cau hoan tien. */
    @PostMapping("/{disputeId}/reject")
    public ResponseEntity<AdminDisputeResponses.Detail> reject(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID disputeId,
                                                               @Valid @RequestBody ReviewReportNoteRequest request) {
        return ResponseEntity.ok(decisionService.reject(AdminJwt.adminId(jwt), disputeId, request.note()));
    }
}
