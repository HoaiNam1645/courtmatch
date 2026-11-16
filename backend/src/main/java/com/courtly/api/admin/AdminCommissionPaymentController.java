package com.courtly.api.admin;

import com.courtly.api.admin.dto.AdminPaymentResponses;
import com.courtly.api.admin.dto.RejectPaymentRequest;
import com.courtly.api.admin.dto.ReviewPaymentRequest;
import com.courtly.service.admin.AdminCommissionPaymentService;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 2.3.47 - xac nhan thanh toan cong no cua chu san; 2.3.48 - tu choi / yeu cau bo sung; 2.3.50 - lich su thanh toan hoa hong. */
@Validated
@RestController
@RequestMapping("/api/v1/admin/finance/commission-payments")
@RequiredArgsConstructor
public class AdminCommissionPaymentController {

    private final AdminCommissionPaymentService paymentService;

    @GetMapping
    public ResponseEntity<AdminPaymentResponses.ListResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) @Size(max = 100, message = "Tu khoa toi da 100 ky tu") String q,
            @RequestParam(defaultValue = "all") String status,
            @RequestParam(required = false) UUID ownerId,
            @RequestParam(required = false) UUID settlementId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "So trang bat dau tu 0") int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "Kich thuoc trang tu 1 den 100")
            @Max(value = 100, message = "Kich thuoc trang tu 1 den 100") int size) {
        return ResponseEntity.ok(paymentService.list(AdminJwt.adminId(jwt), q, status, ownerId, settlementId, from, to, page, size));
    }

    @PostMapping("/{reportId}/confirm")
    public ResponseEntity<AdminPaymentResponses.Row> confirm(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID reportId,
                                                             @Valid @RequestBody(required = false) ReviewPaymentRequest request) {
        return ResponseEntity.ok(paymentService.confirm(AdminJwt.adminId(jwt), reportId, request == null ? null : request.note()));
    }

    @PostMapping("/{reportId}/reject")
    public ResponseEntity<AdminPaymentResponses.Row> reject(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID reportId,
                                                            @Valid @RequestBody RejectPaymentRequest request) {
        return ResponseEntity.ok(paymentService.reject(AdminJwt.adminId(jwt), reportId, request.reason()));
    }
}
