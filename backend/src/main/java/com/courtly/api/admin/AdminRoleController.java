package com.courtly.api.admin;

import com.courtly.api.admin.dto.AdminRoleResponses;
import com.courtly.api.admin.dto.AdminUserResponses;
import com.courtly.api.admin.dto.AssignRoleRequest;
import com.courtly.service.admin.AdminRoleService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 2.3.29 - phan quyen vai tro nguoi dung. */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminRoleController {

    private final AdminRoleService roleService;

    @GetMapping("/roles")
    public ResponseEntity<AdminRoleResponses.RoleList> roles(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(roleService.list(AdminJwt.adminId(jwt)));
    }

    @PostMapping("/users/{userId}/roles")
    public ResponseEntity<AdminUserResponses.Detail> assign(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID userId,
                                                            @Valid @RequestBody AssignRoleRequest request) {
        return ResponseEntity.ok(roleService.assign(AdminJwt.adminId(jwt), userId, request.role()));
    }

    @DeleteMapping("/users/{userId}/roles/{role}")
    public ResponseEntity<AdminUserResponses.Detail> remove(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID userId,
                                                            @PathVariable String role) {
        return ResponseEntity.ok(roleService.remove(AdminJwt.adminId(jwt), userId, role));
    }
}
