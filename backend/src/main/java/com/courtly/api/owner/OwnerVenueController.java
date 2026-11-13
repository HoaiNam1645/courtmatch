package com.courtly.api.owner;

import com.courtly.api.owner.dto.CourtInput;
import com.courtly.api.owner.dto.CreateVenueRequest;
import com.courtly.api.owner.dto.UpdateCourtRequest;
import com.courtly.api.owner.dto.UpdateVenueRequest;
import com.courtly.api.owner.dto.OwnerVenueDetailResponse;
import com.courtly.api.owner.dto.OwnerVenueSummaryResponse;
import com.courtly.api.owner.dto.VenueFormOptionsResponse;
import com.courtly.common.PageResponse;
import com.courtly.service.owner.OwnerVenueService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** San cua chu san: danh sach va them san moi (2.3.3), cap nhat thong tin va san con (2.3.4). */
@Validated
@RestController
@RequestMapping("/api/v1/owner")
@RequiredArgsConstructor
public class OwnerVenueController {

    private final OwnerVenueService ownerVenueService;

    /** Danh muc lua chon cho form san: quan/huyen, tien ich, loai san, mat san. */
    @GetMapping("/venue-options")
    public ResponseEntity<VenueFormOptionsResponse> options() {
        return ResponseEntity.ok(ownerVenueService.options());
    }

    @GetMapping("/venues")
    public ResponseEntity<PageResponse<OwnerVenueSummaryResponse>> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "So trang bat dau tu 0") int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "Kich thuoc trang tu 1 den 100")
            @Max(value = 100, message = "Kich thuoc trang tu 1 den 100") int size) {
        return ResponseEntity.ok(ownerVenueService.list(ownerId(jwt), page, size));
    }

    /** 2.3.3 - them san moi; san o trang thai cho quan tri vien duyet. */
    @PostMapping("/venues")
    public ResponseEntity<OwnerVenueDetailResponse> create(@AuthenticationPrincipal Jwt jwt,
                                                           @Valid @RequestBody CreateVenueRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ownerVenueService.create(ownerId(jwt), request));
    }

    /** 2.3.4 - chi tiet san cua chu san, ke ca san chua duyet. */
    @GetMapping("/venues/{venueId}")
    public ResponseEntity<OwnerVenueDetailResponse> get(@AuthenticationPrincipal Jwt jwt,
                                                        @PathVariable UUID venueId) {
        return ResponseEntity.ok(ownerVenueService.get(ownerId(jwt), venueId));
    }

    /** 2.3.4 - cap nhat thong tin chung va tien ich. */
    @PutMapping("/venues/{venueId}")
    public ResponseEntity<OwnerVenueDetailResponse> update(@AuthenticationPrincipal Jwt jwt,
                                                           @PathVariable UUID venueId,
                                                           @Valid @RequestBody UpdateVenueRequest request) {
        return ResponseEntity.ok(ownerVenueService.update(ownerId(jwt), venueId, request));
    }

    /** 2.3.4 - them san con. */
    @PostMapping("/venues/{venueId}/courts")
    public ResponseEntity<OwnerVenueDetailResponse.CourtItem> addCourt(@AuthenticationPrincipal Jwt jwt,
                                                                       @PathVariable UUID venueId,
                                                                       @Valid @RequestBody CourtInput request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ownerVenueService.addCourt(ownerId(jwt), venueId, request));
    }

    /** 2.3.4 - sua san con, ke ca doi trang thai hoat dong / bao tri / ngung. */
    @PutMapping("/venues/{venueId}/courts/{courtId}")
    public ResponseEntity<OwnerVenueDetailResponse.CourtItem> updateCourt(@AuthenticationPrincipal Jwt jwt,
                                                                          @PathVariable UUID venueId,
                                                                          @PathVariable UUID courtId,
                                                                          @Valid @RequestBody UpdateCourtRequest request) {
        return ResponseEntity.ok(ownerVenueService.updateCourt(ownerId(jwt), venueId, courtId, request));
    }

    static UUID ownerId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
