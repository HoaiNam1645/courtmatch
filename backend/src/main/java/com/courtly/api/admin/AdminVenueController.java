package com.courtly.api.admin;

import com.courtly.api.admin.dto.AdminVenueResponses;
import com.courtly.api.admin.dto.RejectVenueRequest;
import com.courtly.service.admin.AdminVenueService;
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

    /** 2.3.35 - danh muc ly do tu choi san. */
    @GetMapping("/reject-reasons")
    public ResponseEntity<AdminVenueResponses.RejectReasons> rejectReasons(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(venueService.rejectReasons(AdminJwt.adminId(jwt)));
    }

    /** 2.3.35 - tu choi san cho duyet kem ly do. */
    @PostMapping("/{venueId}/reject")
    public ResponseEntity<AdminVenueResponses.Detail> reject(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID venueId,
                                                             @Valid @RequestBody RejectVenueRequest request) {
        return ResponseEntity.ok(venueService.reject(AdminJwt.adminId(jwt), venueId, request.reasonCode(), request.note()));
    }

    /** 2.3.104 - bat lai san bi tu dong tam ngung do bi xu thua nhieu lan. */
    @PostMapping("/{venueId}/reactivate")
    public ResponseEntity<AdminVenueResponses.Detail> reactivate(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID venueId,
                                                                 @jakarta.validation.Valid @org.springframework.web.bind.annotation.RequestBody com.courtly.api.admin.dto.ReviewReportNoteRequest request) {
        return ResponseEntity.ok(venueService.reactivate(AdminJwt.adminId(jwt), venueId, request.note()));
    }
}
