package com.courtly.api.partner;

import com.courtly.api.partner.dto.CreatePartnerRequestRequest;
import com.courtly.api.partner.dto.PartnerRequestResponse;
import com.courtly.common.PageResponse;
import com.courtly.service.matching.PartnerRequestService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

/** API yeu cau ghep cap cua nguoi choi (2.2.31 - 2.2.35). */
@RestController
@Validated
@RequestMapping("/api/v1/partner-requests")
@RequiredArgsConstructor
public class PartnerRequestController {

    private final PartnerRequestService requestService;

    /** 2.2.31 - gui yeu cau ghep cap. */
    @PostMapping
    public ResponseEntity<PartnerRequestResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreatePartnerRequestRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(requestService.create(currentUserId(jwt), request));
    }

    /** 2.2.32 - xem cac yeu cau ghep cap da nhan. */
    @GetMapping("/received")
    public ResponseEntity<PageResponse<PartnerRequestResponse>> received(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "all") String status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(requestService.received(
                currentUserId(jwt), status, page, size));
    }

    /** Danh sach yeu cau da gui de phuc vu huy yeu cau 2.2.35. */
    @GetMapping("/sent")
    public ResponseEntity<PageResponse<PartnerRequestResponse>> sent(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "all") String status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(requestService.sent(
                currentUserId(jwt), status, page, size));
    }

    /** 2.2.33 - chap nhan yeu cau ghep cap dang cho. */
    @PostMapping("/{requestId}/accept")
    public ResponseEntity<PartnerRequestResponse> accept(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID requestId) {
        return ResponseEntity.ok(requestService.accept(currentUserId(jwt), requestId));
    }

    /** 2.2.34 - tu choi yeu cau ghep cap dang cho. */
    @PostMapping("/{requestId}/reject")
    public ResponseEntity<PartnerRequestResponse> reject(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID requestId) {
        return ResponseEntity.ok(requestService.reject(currentUserId(jwt), requestId));
    }

    /** 2.2.35 - huy yeu cau ghep cap da gui khi con dang cho. */
    @PostMapping("/{requestId}/cancel")
    public ResponseEntity<PartnerRequestResponse> cancel(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID requestId) {
        return ResponseEntity.ok(requestService.cancel(currentUserId(jwt), requestId));
    }

    private static UUID currentUserId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
