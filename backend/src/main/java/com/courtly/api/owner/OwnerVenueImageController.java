package com.courtly.api.owner;

import com.courtly.api.owner.dto.ReorderImagesRequest;
import com.courtly.api.owner.dto.UpdateImageCaptionRequest;
import com.courtly.api.owner.dto.VenueImageItem;
import com.courtly.service.owner.OwnerVenueImageService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 2.3.6 - anh cua san. */
@Validated
@RestController
@RequestMapping("/api/v1/owner/venues/{venueId}/images")
@RequiredArgsConstructor
public class OwnerVenueImageController {

    private final OwnerVenueImageService imageService;

    @GetMapping
    public ResponseEntity<List<VenueImageItem>> list(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID venueId) {
        return ResponseEntity.ok(imageService.list(OwnerVenueController.ownerId(jwt), venueId));
    }

    /** Tai len mot anh (multipart: file, caption). */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<VenueImageItem> upload(@AuthenticationPrincipal Jwt jwt,
                                                 @PathVariable UUID venueId,
                                                 @RequestPart("file") MultipartFile file,
                                                 @RequestParam(value = "caption", required = false)
                                                 @Size(max = 255, message = "Chu thich toi da 255 ky tu") String caption) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(imageService.upload(OwnerVenueController.ownerId(jwt), venueId, file, caption));
    }

    @PatchMapping("/{imageId}")
    public ResponseEntity<VenueImageItem> updateCaption(@AuthenticationPrincipal Jwt jwt,
                                                        @PathVariable UUID venueId,
                                                        @PathVariable UUID imageId,
                                                        @Valid @RequestBody UpdateImageCaptionRequest request) {
        return ResponseEntity.ok(imageService.updateCaption(OwnerVenueController.ownerId(jwt), venueId, imageId, request.caption()));
    }

    @PostMapping("/{imageId}/cover")
    public ResponseEntity<List<VenueImageItem>> setCover(@AuthenticationPrincipal Jwt jwt,
                                                         @PathVariable UUID venueId,
                                                         @PathVariable UUID imageId) {
        return ResponseEntity.ok(imageService.setCover(OwnerVenueController.ownerId(jwt), venueId, imageId));
    }

    @PutMapping("/order")
    public ResponseEntity<List<VenueImageItem>> reorder(@AuthenticationPrincipal Jwt jwt,
                                                        @PathVariable UUID venueId,
                                                        @Valid @RequestBody ReorderImagesRequest request) {
        return ResponseEntity.ok(imageService.reorder(OwnerVenueController.ownerId(jwt), venueId, request.imageIds()));
    }

    @DeleteMapping("/{imageId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt,
                                       @PathVariable UUID venueId,
                                       @PathVariable UUID imageId) {
        imageService.delete(OwnerVenueController.ownerId(jwt), venueId, imageId);
        return ResponseEntity.noContent().build();
    }
}
