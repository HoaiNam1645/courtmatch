package com.courtly.api.owner;

import com.courtly.api.owner.dto.CourtBlockItem;
import com.courtly.api.owner.dto.CreateCourtBlockRequest;
import com.courtly.common.PageResponse;
import com.courtly.service.owner.OwnerCourtBlockService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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

/** Khoa khung gio san (2.3.10). */
@Validated
@RestController
@RequestMapping("/api/v1/owner/venues/{venueId}/blocks")
@RequiredArgsConstructor
public class OwnerCourtBlockController {

    private final OwnerCourtBlockService blockService;

    /** Cac khoang khoa dang hieu luc va chua ket thuc. */
    @GetMapping
    public ResponseEntity<PageResponse<CourtBlockItem>> list(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID venueId,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "So trang bat dau tu 0") int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "Kich thuoc trang tu 1 den 100")
            @Max(value = 100, message = "Kich thuoc trang tu 1 den 100") int size) {
        return ResponseEntity.ok(blockService.list(OwnerVenueController.ownerId(jwt), venueId, page, size));
    }

    /** 2.3.11 - mo khoa mot khoang khoa; khung gio dat duoc lai ngay. */
    @PostMapping("/{blockId}/unlock")
    public ResponseEntity<CourtBlockItem> unlock(@AuthenticationPrincipal Jwt jwt,
                                                 @PathVariable UUID venueId,
                                                 @PathVariable UUID blockId) {
        return ResponseEntity.ok(blockService.unlock(OwnerVenueController.ownerId(jwt), venueId, blockId));
    }

    @PostMapping
    public ResponseEntity<List<CourtBlockItem>> create(@AuthenticationPrincipal Jwt jwt,
                                                       @PathVariable UUID venueId,
                                                       @Valid @RequestBody CreateCourtBlockRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(blockService.create(OwnerVenueController.ownerId(jwt), venueId, request));
    }
}
