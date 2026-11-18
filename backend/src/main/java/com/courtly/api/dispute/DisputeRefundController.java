package com.courtly.api.dispute;

import com.courtly.api.admin.dto.ReviewReportNoteRequest;
import com.courtly.api.dispute.dto.DisputeRefundResponse;
import com.courtly.service.dispute.DisputeRefundService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 2.3.108 - theo doi va xac nhan khoan hoan tien truc tiep theo bao cao don dat san. */
@Validated
@RestController
@RequiredArgsConstructor
public class DisputeRefundController {

    private final DisputeRefundService service;

    public record ComplaintRequest(@NotBlank(message = "Hay nhap ly do") @Size(min = 5, max = 1000, message = "Ly do tu 5 den 1000 ky tu") String reason) {
    }

    private static UUID id(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    /** Khoan hoan cua bao cao cho nguoi bao cao, chu san, quan tri vien; chua co khoan hoan thi {@code refund} null. */
    @GetMapping("/api/v1/booking-disputes/{disputeId}/refund")
    public ResponseEntity<Map<String, Object>> view(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID disputeId) {
        DisputeRefundResponse refund = service.view(id(jwt), disputeId);
        return ResponseEntity.ok(refund == null ? new java.util.HashMap<>(Map.of()) : Map.of("refund", refund));
    }

    @PostMapping(value = "/api/v1/owner/refunds/{refundId}/report-paid", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DisputeRefundResponse> ownerReport(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID refundId,
                                                             @RequestPart("file") MultipartFile file,
                                                             @RequestParam(required = false) String note) {
        return ResponseEntity.ok(service.ownerReport(id(jwt), refundId, note, file));
    }

    @PostMapping("/api/v1/refunds/{refundId}/confirm")
    public ResponseEntity<DisputeRefundResponse> confirm(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID refundId) {
        return ResponseEntity.ok(service.playerConfirm(id(jwt), refundId));
    }

    @PostMapping("/api/v1/refunds/{refundId}/dispute")
    public ResponseEntity<DisputeRefundResponse> complain(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID refundId,
                                                          @Valid @RequestBody ComplaintRequest request) {
        return ResponseEntity.ok(service.playerComplain(id(jwt), refundId, request.reason()));
    }

    @PostMapping("/api/v1/admin/refunds/{refundId}/confirm")
    public ResponseEntity<DisputeRefundResponse> adminConfirm(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID refundId,
                                                              @Valid @RequestBody ReviewReportNoteRequest request) {
        return ResponseEntity.ok(service.adminConfirm(id(jwt), refundId, request.note()));
    }

    @GetMapping("/api/v1/refunds/{refundId}/proof")
    public ResponseEntity<Resource> proof(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID refundId) {
        DisputeRefundService.EvidenceFile file = service.proof(id(jwt), refundId);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(file.contentType()))
                .cacheControl(CacheControl.noStore()).header("X-Content-Type-Options", "nosniff")
                .body(new FileSystemResource(file.path()));
    }
}
