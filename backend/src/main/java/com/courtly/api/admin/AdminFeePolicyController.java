package com.courtly.api.admin;

import com.courtly.api.admin.dto.AdminFeePolicyResponses;
import com.courtly.api.admin.dto.CreateFeePolicyRequest;
import com.courtly.api.admin.dto.UpdateDebtRestrictionsRequest;
import com.courtly.api.admin.dto.UpdateFeePolicyRequest;
import java.util.UUID;
import com.courtly.service.admin.AdminFeePolicyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 2.3.39 - 2.3.41 - cau hinh phi nen tang. */
@RestController
@RequestMapping("/api/v1/admin/fee-policies")
@RequiredArgsConstructor
public class AdminFeePolicyController {

    private final AdminFeePolicyService feePolicyService;

    /** 2.3.41 - xem cau hinh phi nen tang. */
    @GetMapping
    public ResponseEntity<AdminFeePolicyResponses.ListResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(feePolicyService.list(AdminJwt.adminId(jwt)));
    }

    /** 2.3.39 - thiet lap chinh sach phi moi, ap dung ngay. */
    @PostMapping
    public ResponseEntity<AdminFeePolicyResponses.ListResponse> create(@AuthenticationPrincipal Jwt jwt,
                                                                       @Valid @RequestBody CreateFeePolicyRequest request) {
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED)
                .body(feePolicyService.create(AdminJwt.adminId(jwt), request.name(), request.feeType(), request.feeValue()));
    }

    /** 2.3.40 - sua ten, loai phi, gia tri cua mot chinh sach. */
    @PutMapping("/{policyId}")
    public ResponseEntity<AdminFeePolicyResponses.ListResponse> update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID policyId,
                                                                       @Valid @RequestBody UpdateFeePolicyRequest request) {
        return ResponseEntity.ok(feePolicyService.update(AdminJwt.adminId(jwt), policyId, request.name(), request.feeType(), request.feeValue()));
    }

    /** 2.3.40 - kich hoat lai chinh sach cu, chinh sach dang active se dung ap dung. */
    @PostMapping("/{policyId}/activate")
    public ResponseEntity<AdminFeePolicyResponses.ListResponse> activate(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID policyId) {
        return ResponseEntity.ok(feePolicyService.activate(AdminJwt.adminId(jwt), policyId));
    }

    /** 2.3.40 - cap nhat nguong han che chu san co cong no qua han. */
    @PutMapping("/debt-restrictions")
    public ResponseEntity<AdminFeePolicyResponses.ListResponse> updateRestrictions(@AuthenticationPrincipal Jwt jwt,
                                                                                  @Valid @RequestBody UpdateDebtRestrictionsRequest request) {
        return ResponseEntity.ok(feePolicyService.updateRestrictions(AdminJwt.adminId(jwt), request.levels()));
    }
}
