package com.courtly.api.admin;

import com.courtly.api.admin.dto.AdminUserResponses;
import com.courtly.api.admin.dto.ChangeUserStatusRequest;
import com.courtly.service.admin.AdminUserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
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

/** 2.3.28 - quan ly tai khoan nguoi dung. */
@Validated
@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService userService;

    @GetMapping
    public ResponseEntity<AdminUserResponses.ListResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) @Size(max = 100, message = "Tu khoa toi da 100 ky tu") String q,
            @RequestParam(defaultValue = "all") String role,
            @RequestParam(defaultValue = "all") String status,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "So trang bat dau tu 0") int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "Kich thuoc trang tu 1 den 100")
            @Max(value = 100, message = "Kich thuoc trang tu 1 den 100") int size) {
        return ResponseEntity.ok(userService.list(AdminJwt.adminId(jwt), q, role, status, page, size));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<AdminUserResponses.Detail> detail(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID userId) {
        return ResponseEntity.ok(userService.get(AdminJwt.adminId(jwt), userId));
    }

    @PostMapping("/{userId}/status")
    public ResponseEntity<AdminUserResponses.Detail> changeStatus(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID userId,
                                                                  @Valid @RequestBody ChangeUserStatusRequest request) {
        return ResponseEntity.ok(userService.changeStatus(AdminJwt.adminId(jwt), userId, request.status(), request.reason()));
    }
}
