package com.courtly.api.admin;

import com.courtly.api.admin.dto.AdminVenueResponses;
import com.courtly.service.admin.AdminVenueService;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 2.3.34 - duyet san cau long. */
@Validated
@RestController
@RequestMapping("/api/v1/admin/venues")
@RequiredArgsConstructor
public class AdminVenueController {

    private final AdminVenueService venueService;

    @GetMapping
    public ResponseEntity<AdminVenueResponses.ListResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "pending") String approval,
            @RequestParam(defaultValue = "all") String district,
            @RequestParam(required = false) @Size(max = 100, message = "Tu khoa toi da 100 ky tu") String q,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "So trang bat dau tu 0") int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "Kich thuoc trang tu 1 den 100")
            @Max(value = 100, message = "Kich thuoc trang tu 1 den 100") int size) {
        return ResponseEntity.ok(venueService.list(AdminJwt.adminId(jwt), approval, district, q, page, size));
    }

    @GetMapping("/{venueId}")
    public ResponseEntity<AdminVenueResponses.Detail> detail(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID venueId) {
        return ResponseEntity.ok(venueService.get(AdminJwt.adminId(jwt), venueId));
    }

    @PostMapping("/{venueId}/approve")
    public ResponseEntity<AdminVenueResponses.Detail> approve(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID venueId) {
        return ResponseEntity.ok(venueService.approve(AdminJwt.adminId(jwt), venueId));
    }
}
