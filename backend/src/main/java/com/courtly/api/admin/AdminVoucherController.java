package com.courtly.api.admin;

import com.courtly.api.admin.dto.AdminVoucherResponses;
import com.courtly.api.admin.dto.VoucherRequest;
import com.courtly.service.admin.AdminVoucherService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** B4 - quan ly voucher giam gia. */
@Validated
@RestController
@RequestMapping("/api/v1/admin/vouchers")
@RequiredArgsConstructor
public class AdminVoucherController {

    private final AdminVoucherService voucherService;

    @GetMapping
    public ResponseEntity<AdminVoucherResponses.ListResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "all") String state,
            @RequestParam(required = false) @Size(max = 100, message = "Tu khoa toi da 100 ky tu") String q) {
        return ResponseEntity.ok(voucherService.list(AdminJwt.adminId(jwt), state, q));
    }

    @GetMapping("/{voucherId}")
    public ResponseEntity<AdminVoucherResponses.Detail> detail(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID voucherId) {
        return ResponseEntity.ok(voucherService.get(AdminJwt.adminId(jwt), voucherId));
    }

    @PostMapping
    public ResponseEntity<AdminVoucherResponses.Detail> create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody VoucherRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(voucherService.create(AdminJwt.adminId(jwt), request));
    }

    @PutMapping("/{voucherId}")
    public ResponseEntity<AdminVoucherResponses.Detail> update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID voucherId,
                                                               @Valid @RequestBody VoucherRequest request) {
        return ResponseEntity.ok(voucherService.update(AdminJwt.adminId(jwt), voucherId, request));
    }

    @PostMapping("/{voucherId}/lock")
    public ResponseEntity<AdminVoucherResponses.Detail> lock(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID voucherId) {
        return ResponseEntity.ok(voucherService.lock(AdminJwt.adminId(jwt), voucherId));
    }

    @PostMapping("/{voucherId}/unlock")
    public ResponseEntity<AdminVoucherResponses.Detail> unlock(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID voucherId) {
        return ResponseEntity.ok(voucherService.unlock(AdminJwt.adminId(jwt), voucherId));
    }
}
