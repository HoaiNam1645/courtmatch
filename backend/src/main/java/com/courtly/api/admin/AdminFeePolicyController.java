package com.courtly.api.admin;

import com.courtly.api.admin.dto.AdminFeePolicyResponses;
import com.courtly.api.admin.dto.CreateFeePolicyRequest;
import com.courtly.service.admin.AdminFeePolicyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
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
}
