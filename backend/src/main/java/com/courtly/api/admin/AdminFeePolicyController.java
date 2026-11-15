package com.courtly.api.admin;

import com.courtly.api.admin.dto.AdminFeePolicyResponses;
import com.courtly.service.admin.AdminFeePolicyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
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
}
