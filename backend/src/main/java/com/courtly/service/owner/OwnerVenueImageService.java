package com.courtly.service.owner;

import com.courtly.api.owner.dto.VenueImageItem;
import com.courtly.common.exception.ApiException;
import com.courtly.common.exception.ErrorCode;
import com.courtly.domain.venue.Venue;
import com.courtly.domain.venue.VenueImage;
import com.courtly.domain.venue.VenueImageRepository;
import com.courtly.service.audit.AuditService;
import com.courtly.service.storage.VenueImageStorage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

/**
 * 2.3.6 - chu san tai len, sap xep, chon anh bia va xoa anh san.
 *
 * <p>Thu tu anh o day chinh la thu tu nguoi choi thay o trang chi tiet san (2.1.24).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OwnerVenueImageService {

    public static final int MAX_IMAGES_PER_VENUE = 20;
    public static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;

    private final OwnedVenueLookup ownedVenueLookup;
    private final VenueImageRepository imageRepository;
    private final VenueImageStorage storage;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<VenueImageItem> list(UUID ownerId, UUID venueId) {
        ownedVenueLookup.requireVenue(ownerId, venueId);
        return imageRepository.findAllByVenueIdOrderByDisplayOrderAsc(venueId).stream()
                .map(OwnerVenueImageService::toItem)
                .toList();
    }

    /** Anh dau tien cua san tu dong thanh anh bia; anh moi xep cuoi danh sach. */
    @Transactional
    public VenueImageItem upload(UUID ownerId, UUID venueId, MultipartFile file, String caption) {
        Venue venue = ownedVenueLookup.requireVenue(ownerId, venueId);
        if (file.isEmpty()) {
            throw invalidImage("File anh rong.");
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw invalidImage("Anh vuot qua dung luong toi da 5MB.");
        }
        List<VenueImage> existing = imageRepository.findAllByVenueIdOrderByDisplayOrderAsc(venueId);
        if (existing.size() >= MAX_IMAGES_PER_VENUE) {
            throw ApiException.conflict(ErrorCode.IMAGE_LIMIT_REACHED,
                    "Moi san co toi da " + MAX_IMAGES_PER_VENUE + " anh. Hay xoa bot anh cu truoc.", Map.of());
        }

        byte[] bytes = readBytes(file);
        String extension = VenueImageStorage.detectExtension(bytes)
                .orElseThrow(() -> invalidImage("Chi nhan anh JPEG, PNG hoac WebP."));

        String url = storage.store(venueId, bytes, extension);
        VenueImage image = new VenueImage();
        image.setId(UUID.randomUUID());
        image.setVenue(venue);
        image.setImageUrl(url);
        image.setCaption(OwnerVenueService.trimToNull(caption));
        image.setDisplayOrder(existing.stream().mapToInt(VenueImage::getDisplayOrder).max().orElse(0) + 1);
        image.setCover(existing.stream().noneMatch(VenueImage::isCover));
        try {
            imageRepository.saveAndFlush(image);
        } catch (RuntimeException exception) {
            // Khong de lai file mo coi khi khong ghi duoc ban ghi.
            storage.deleteIfStored(url);
            throw exception;
        }
        auditService.record(venue.getOwner(), "venue_image.upload", "venue_images", image.getId(),
                null, Map.of("venueId", venueId.toString(), "imageUrl", url, "bytes", bytes.length));
        log.info("Chu san {} tai anh {} cho san {}", ownerId, image.getId(), venueId);
        return toItem(image);
    }

    @Transactional
    public VenueImageItem updateCaption(UUID ownerId, UUID venueId, UUID imageId, String caption) {
        VenueImage image = requireImage(ownerId, venueId, imageId);
        image.setCaption(OwnerVenueService.trimToNull(caption));
        imageRepository.saveAndFlush(image);
        return toItem(image);
    }

    /** Doi anh bia. Bo co anh bia cu va flush truoc, vi moi san chi duoc mot anh bia (unique index). */
    @Transactional
    public List<VenueImageItem> setCover(UUID ownerId, UUID venueId, UUID imageId) {
        VenueImage target = requireImage(ownerId, venueId, imageId);
        List<VenueImage> images = imageRepository.findAllByVenueIdOrderByDisplayOrderAsc(venueId);
        images.stream().filter(VenueImage::isCover).filter(image -> !image.getId().equals(imageId))
                .forEach(image -> image.setCover(false));
        imageRepository.flush();
        target.setCover(true);
        imageRepository.saveAndFlush(target);
        auditService.record(target.getVenue().getOwner(), "venue_image.cover", "venue_images", imageId,
                null, Map.of("venueId", venueId.toString()));
        return images.stream().map(OwnerVenueImageService::toItem).toList();
    }

    /** Sap lai thu tu: danh sach gui len phai gom dung va du moi anh cua san. */
    @Transactional
    public List<VenueImageItem> reorder(UUID ownerId, UUID venueId, List<UUID> imageIds) {
        Venue venue = ownedVenueLookup.requireVenue(ownerId, venueId);
        List<VenueImage> images = imageRepository.findAllByVenueIdOrderByDisplayOrderAsc(venueId);
        Set<UUID> current = new HashSet<>(images.stream().map(VenueImage::getId).toList());
        if (imageIds.size() != images.size() || !current.equals(new HashSet<>(imageIds))) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "Danh sach anh khong khop voi anh hien co cua san. Vui long tai lai trang.",
                    Map.of("imageIds", "Phai gom du moi anh cua san, khong trung lap"));
        }
        Map<UUID, VenueImage> byId = new LinkedHashMap<>();
        images.forEach(image -> byId.put(image.getId(), image));
        for (int index = 0; index < imageIds.size(); index++) {
            byId.get(imageIds.get(index)).setDisplayOrder(index + 1);
        }
        imageRepository.saveAllAndFlush(images);
        auditService.record(venue.getOwner(), "venue_image.reorder", "venues", venueId,
                null, Map.of("venueId", venueId.toString(), "imageIds", imageIds.stream().map(UUID::toString).toList()));
        return imageIds.stream().map(byId::get).map(OwnerVenueImageService::toItem).toList();
    }

    /** Xoa anh; xoa anh bia thi anh dung dau con lai thanh anh bia. File chi xoa sau khi commit. */
    @Transactional
    public void delete(UUID ownerId, UUID venueId, UUID imageId) {
        VenueImage image = requireImage(ownerId, venueId, imageId);
        boolean wasCover = image.isCover();
        String url = image.getImageUrl();
        imageRepository.delete(image);
        imageRepository.flush();

        if (wasCover) {
            imageRepository.findAllByVenueIdOrderByDisplayOrderAsc(venueId).stream().findFirst().ifPresent(next -> {
                next.setCover(true);
                imageRepository.saveAndFlush(next);
            });
        }
        auditService.record(image.getVenue().getOwner(), "venue_image.delete", "venue_images", imageId,
                Map.of("venueId", venueId.toString(), "imageUrl", url), null);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                storage.deleteIfStored(url);
            }
        });
    }

    private VenueImage requireImage(UUID ownerId, UUID venueId, UUID imageId) {
        ownedVenueLookup.requireVenue(ownerId, venueId);
        return imageRepository.findById(imageId)
                .filter(image -> image.getVenue().getId().equals(venueId))
                .orElseThrow(() -> ApiException.notFound("Khong tim thay anh nay."));
    }

    private static byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new UncheckedIOException("Khong doc duoc file tai len", exception);
        }
    }

    private static ApiException invalidImage(String message) {
        return ApiException.badRequest(ErrorCode.IMAGE_INVALID, message, Map.of("file", message));
    }

    private static VenueImageItem toItem(VenueImage image) {
        return new VenueImageItem(image.getId(), image.getImageUrl(), image.getCaption(),
                image.getDisplayOrder(), image.isCover());
    }
}
