package com.courtly.api.admin;

import com.courtly.api.admin.dto.AdminPlayerResponses;
import com.courtly.api.admin.dto.LockPlayerRequest;
import com.courtly.service.admin.AdminPlayerService;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 2.3.31 - quan ly tai khoan nguoi choi. */
@Validated
@RestController
@RequestMapping("/api/v1/admin/players")
@RequiredArgsConstructor
public class AdminPlayerController {

    private final AdminPlayerService playerService;

    @GetMapping
    public ResponseEntity<AdminPlayerResponses.ListResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) @Size(max = 100, message = "Tu khoa toi da 100 ky tu") String q,
            @RequestParam(defaultValue = "all") String status,
            @RequestParam(defaultValue = "all") String skill,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "So trang bat dau tu 0") int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "Kich thuoc trang tu 1 den 100")
            @Max(value = 100, message = "Kich thuoc trang tu 1 den 100") int size) {
        return ResponseEntity.ok(playerService.list(AdminJwt.adminId(jwt), q, status, skill, from, to, page, size));
    }

    @GetMapping("/{playerId}")
    public ResponseEntity<AdminPlayerResponses.Detail> detail(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID playerId) {
        return ResponseEntity.ok(playerService.get(AdminJwt.adminId(jwt), playerId));
    }

    @PostMapping("/{playerId}/lock")
    public ResponseEntity<AdminPlayerResponses.Detail> lock(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID playerId,
                                                            @Valid @RequestBody LockPlayerRequest request) {
        return ResponseEntity.ok(playerService.lock(AdminJwt.adminId(jwt), playerId, request.reason()));
    }

    @PostMapping("/{playerId}/unlock")
    public ResponseEntity<AdminPlayerResponses.Detail> unlock(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID playerId) {
        return ResponseEntity.ok(playerService.unlock(AdminJwt.adminId(jwt), playerId));
    }
}
