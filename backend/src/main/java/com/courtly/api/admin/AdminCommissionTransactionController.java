package com.courtly.api.admin;

import com.courtly.api.admin.dto.AdminCommissionDetailResponse;
import com.courtly.api.admin.dto.AdminCommissionTransactionResponse;
import com.courtly.service.admin.AdminCommissionDetailService;
import com.courtly.service.admin.AdminCommissionTransactionService;
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

/** 2.3.43 - xem danh sach giao dich doi soat; 2.3.44 - xem chi tiet doi soat cua mot booking. */
@Validated
@RestController
@RequestMapping("/api/v1/admin/finance")
@RequiredArgsConstructor
public class AdminCommissionTransactionController {

    private final AdminCommissionTransactionService transactionService;
    private final AdminCommissionDetailService detailService;

    @GetMapping("/commission-transactions")
    public ResponseEntity<AdminCommissionTransactionResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) @Size(max = 100, message = "Tu khoa toi da 100 ky tu") String q,
            @RequestParam(defaultValue = "all") String status,
            @RequestParam(required = false) UUID venueId,
            @RequestParam(required = false) UUID settlementId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "false") boolean overdue,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "So trang bat dau tu 0") int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "Kich thuoc trang tu 1 den 100")
            @Max(value = 100, message = "Kich thuoc trang tu 1 den 100") int size) {
        return ResponseEntity.ok(transactionService.list(AdminJwt.adminId(jwt), q, status, venueId, settlementId, from, to, overdue, page, size));
    }

    @GetMapping("/commission-transactions/{commissionId}")
    public ResponseEntity<AdminCommissionDetailResponse> detail(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID commissionId) {
        return ResponseEntity.ok(detailService.get(AdminJwt.adminId(jwt), commissionId));
    }
}
