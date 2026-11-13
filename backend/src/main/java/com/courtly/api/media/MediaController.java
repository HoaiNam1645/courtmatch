package com.courtly.api.media;

import com.courtly.service.storage.VenueImageStorage;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Phuc vu cong khai anh san do chu san tai len (2.3.6).
 *
 * <p>Ten file la UUID ngau nhien va khong bao gio bi ghi de, nen cho trinh duyet luu dem lau.
 */
@RestController
@RequestMapping("/api/v1/media")
@RequiredArgsConstructor
public class MediaController {

    private final VenueImageStorage storage;

    @GetMapping("/venues/{venueId}/{fileName:.+}")
    public ResponseEntity<Resource> venueImage(@PathVariable UUID venueId, @PathVariable String fileName) {
        Path file = storage.resolveExisting(venueId, fileName);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(VenueImageStorage.contentTypeOf(fileName)))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic())
                .header("X-Content-Type-Options", "nosniff")
                .body(new FileSystemResource(file));
    }
}
