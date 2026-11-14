package com.courtly.api.admin;

import com.courtly.api.admin.dto.AdminOwnerResponses;
import com.courtly.api.admin.dto.ReviewOwnerRequest;
import com.courtly.service.admin.AdminOwnerService;
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

/** 2.3.32 - tai khoan chu san duy nhat. */
@RestController
@RequestMapping("/api/v1/admin/owner")
@RequiredArgsConstructor
public class AdminOwnerController {

    private final AdminOwnerService ownerService;

    @GetMapping
    public ResponseEntity<AdminOwnerResponses.Detail> get(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(ownerService.get(AdminJwt.adminId(jwt)));
    }

    @PostMapping("/verification")
    public ResponseEntity<AdminOwnerResponses.Detail> review(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ReviewOwnerRequest request) {
        return ResponseEntity.ok(ownerService.review(AdminJwt.adminId(jwt), request.status(), request.reason()));
    }
}
