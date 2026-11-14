package com.courtly.api.admin;

import com.courtly.api.admin.dto.AdminPermissionResponses;
import com.courtly.api.admin.dto.UpdateRolePermissionsRequest;
import com.courtly.service.admin.AdminPermissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 2.3.30 - ma tran vai tro - quyen. */
@RestController
@RequestMapping("/api/v1/admin/permissions")
@RequiredArgsConstructor
public class AdminPermissionController {

    private final AdminPermissionService permissionService;

    @GetMapping
    public ResponseEntity<AdminPermissionResponses.Matrix> matrix(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(permissionService.matrix(AdminJwt.adminId(jwt)));
    }

    @PutMapping("/roles/{role}")
    public ResponseEntity<AdminPermissionResponses.Matrix> update(@AuthenticationPrincipal Jwt jwt, @PathVariable String role,
                                                                  @Valid @RequestBody UpdateRolePermissionsRequest request) {
        return ResponseEntity.ok(permissionService.update(AdminJwt.adminId(jwt), role, request.permissions()));
    }
}
