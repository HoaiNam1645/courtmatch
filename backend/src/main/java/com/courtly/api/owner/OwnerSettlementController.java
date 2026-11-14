package com.courtly.api.owner;

import com.courtly.api.owner.dto.SettlementResponses;
import com.courtly.service.owner.OwnerSettlementService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 2.3.23 - cac ky doi soat hoa hong. */
@Validated
@RestController
@RequestMapping("/api/v1/owner/settlements")
@RequiredArgsConstructor
public class OwnerSettlementController {

    private final OwnerSettlementService settlementService;

    @GetMapping
    public ResponseEntity<SettlementResponses.ListResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "all") String status,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "So trang bat dau tu 0") int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "Kich thuoc trang tu 1 den 100")
            @Max(value = 100, message = "Kich thuoc trang tu 1 den 100") int size) {
        return ResponseEntity.ok(settlementService.list(OwnerVenueController.ownerId(jwt), status, page, size));
    }

    @GetMapping("/{settlementId}")
    public ResponseEntity<SettlementResponses.Detail> detail(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID settlementId,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "So trang bat dau tu 0") int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "Kich thuoc trang tu 1 den 100")
            @Max(value = 100, message = "Kich thuoc trang tu 1 den 100") int size) {
        return ResponseEntity.ok(settlementService.get(OwnerVenueController.ownerId(jwt), settlementId, page, size));
    }
}
