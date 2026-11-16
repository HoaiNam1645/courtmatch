package com.courtly.api.admin;

import com.courtly.api.admin.dto.AdminSettlementResponses;
import com.courtly.api.admin.dto.CreateSettlementRequest;
import com.courtly.service.admin.AdminSettlementService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
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

/** 2.3.45 - danh sach ky cong no hoa hong va chot ky; 2.3.46 - chi tiet mot ky. */
@Validated
@RestController
@RequestMapping("/api/v1/admin/finance/settlements")
@RequiredArgsConstructor
public class AdminSettlementController {

    private final AdminSettlementService settlementService;

    @GetMapping
    public ResponseEntity<AdminSettlementResponses.ListResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) @Size(max = 100, message = "Tu khoa toi da 100 ky tu") String q,
            @RequestParam(defaultValue = "all") String status,
            @RequestParam(required = false) UUID ownerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "false") boolean overdue,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "So trang bat dau tu 0") int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "Kich thuoc trang tu 1 den 100")
            @Max(value = 100, message = "Kich thuoc trang tu 1 den 100") int size) {
        return ResponseEntity.ok(settlementService.list(AdminJwt.adminId(jwt), q, status, ownerId, from, to, overdue, page, size));
    }

    @PostMapping
    public ResponseEntity<AdminSettlementResponses.Row> create(@AuthenticationPrincipal Jwt jwt,
                                                               @Valid @RequestBody CreateSettlementRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(settlementService.create(AdminJwt.adminId(jwt), request));
    }

    @GetMapping("/{settlementId}")
    public ResponseEntity<AdminSettlementResponses.Detail> detail(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID settlementId,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "So trang bat dau tu 0") int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "Kich thuoc trang tu 1 den 100")
            @Max(value = 100, message = "Kich thuoc trang tu 1 den 100") int size) {
        return ResponseEntity.ok(settlementService.get(AdminJwt.adminId(jwt), settlementId, page, size));
    }
}
