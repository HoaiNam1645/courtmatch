package com.courtly.api.dispute;

import com.courtly.api.dispute.dto.CreateDisputeRequest;
import com.courtly.api.dispute.dto.DisputeCategoryResponse;
import com.courtly.api.dispute.dto.DisputeEvidenceResponse;
import com.courtly.api.dispute.dto.DisputeResponse;
import com.courtly.service.dispute.DisputeEvidenceService;
import java.time.Duration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import com.courtly.service.dispute.BookingDisputeService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Nguoi choi bao cao van de ve don dat san (2.3.85, 2.3.86). */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class BookingDisputeController {

    private final BookingDisputeService disputeService;
    private final DisputeEvidenceService evidenceService;

    /** 2.3.86 - danh muc loai bao cao. */
    @GetMapping("/booking-disputes/categories")
    public ResponseEntity<List<DisputeCategoryResponse>> categories() {
        return ResponseEntity.ok(disputeService.categories());
    }

    /** 2.3.88 - tai len mot anh bang chung (moi lan mot anh, toi da 5MB). */
    @PostMapping(value = "/booking-disputes/{disputeId}/evidences", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DisputeEvidenceResponse> uploadEvidence(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID disputeId,
                                                                  @RequestPart("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(evidenceService.upload(UUID.fromString(jwt.getSubject()), disputeId, file));
    }

    /** 2.3.88 - xoa anh bang chung khi chu san chua giai trinh. */
    @DeleteMapping("/booking-disputes/{disputeId}/evidences/{evidenceId}")
    public ResponseEntity<Void> deleteEvidence(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID disputeId,
                                               @PathVariable UUID evidenceId) {
        evidenceService.delete(UUID.fromString(jwt.getSubject()), disputeId, evidenceId);
        return ResponseEntity.noContent().build();
    }

    /** 2.3.88 - xem anh bang chung: chi nguoi bao cao, chu san cua san va quan tri vien (can dang nhap). */
    @GetMapping("/booking-disputes/{disputeId}/evidences/{evidenceId}/file")
    public ResponseEntity<Resource> evidenceFile(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID disputeId,
                                                 @PathVariable UUID evidenceId) {
        DisputeEvidenceService.EvidenceFile file = evidenceService.file(UUID.fromString(jwt.getSubject()), disputeId, evidenceId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Disposition", "inline")
                .body(new FileSystemResource(file.path()));
    }

    /** 2.3.85 - tao bao cao ve mot don. */
    @PostMapping("/bookings/{bookingId}/disputes")
    public ResponseEntity<DisputeResponse> create(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID bookingId,
                                                  @Valid @RequestBody CreateDisputeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(disputeService.create(UUID.fromString(jwt.getSubject()), bookingId, request));
    }
}
