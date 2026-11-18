package com.courtly.service.dispute;

import com.courtly.api.dispute.dto.DisputeEvidenceResponse;
import com.courtly.common.exception.ApiException;
import com.courtly.common.exception.ErrorCode;
import com.courtly.domain.payment.BookingDispute;
import com.courtly.domain.payment.BookingDisputeRepository;
import com.courtly.service.admin.AdminAccessService;
import com.courtly.service.storage.DisputeEvidenceStorage;
import com.courtly.service.storage.VenueImageStorage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Anh bang chung cua bao cao don dat san (2.3.88).
 *
 * <p>Toi da {@value #MAX_INITIAL} anh cho bao cao ban dau va {@value #MAX_TOTAL} anh moi bao cao (ke ca cac lan bo sung 2.3.91);
 * JPEG, PNG, WebP toi da 5 MB, nhan dang bang noi dung that. Moi lan mot anh. Danh sach nam trong
 * {@code booking_disputes.reporter_evidences_json}. Anh chi doc qua API da kiem tra quyen.
 */
@Service
@RequiredArgsConstructor
public class DisputeEvidenceService {

    public static final int MAX_INITIAL = 5;
    public static final int MAX_TOTAL = 10;
    static final long MAX_BYTES = 5L * 1024 * 1024;
    static final String ACTIVE_BEFORE_OWNER = "awaiting_owner_response";

    private final BookingDisputeRepository disputeRepository;
    private final DisputeEvidenceStorage storage;
    private final AdminAccessService adminAccessService;

    /** Tai len mot anh cho bao cao cua minh khi chu san chua giai trinh. */
    @Transactional
    public DisputeEvidenceResponse upload(UUID userId, UUID disputeId, MultipartFile file) {
        BookingDispute dispute = requireOwn(userId, disputeId, true);
        requireAwaitingOwner(dispute, "Chi tai them anh khi bao cao dang cho chu san giai trinh. Hay bo sung thong tin de gui them anh.");
        if (file == null || file.isEmpty()) {
            throw invalidImage("File anh rong.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw invalidImage("Anh vuot qua dung luong toi da 5MB.");
        }
        List<Map<String, Object>> list = mutableEvidences(dispute);
        long initial = list.stream().filter(item -> item.get("supplementId") == null).count();
        if (initial >= MAX_INITIAL || list.size() >= MAX_TOTAL) {
            throw ApiException.conflict(ErrorCode.IMAGE_LIMIT_REACHED,
                    "Bao cao ban dau co toi da " + MAX_INITIAL + " anh (tong " + MAX_TOTAL + " anh cho ca bao cao). Hay xoa bot anh cu truoc.", Map.of());
        }

        byte[] bytes = readBytes(file);
        String extension = VenueImageStorage.detectExtension(bytes)
                .orElseThrow(() -> invalidImage("Chi nhan anh JPEG, PNG hoac WebP."));
        String fileName = storage.store(disputeId, bytes, extension);

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", UUID.randomUUID().toString());
        item.put("fileName", fileName);
        item.put("originalName", cleanName(file.getOriginalFilename()));
        item.put("size", bytes.length);
        item.put("contentType", VenueImageStorage.CONTENT_TYPES.get(extension));
        item.put("uploadedAt", Instant.now().toString());
        item.put("supplementId", null);
        list.add(item);
        dispute.setReporterEvidences(list);
        disputeRepository.saveAndFlush(dispute);
        return toResponse(disputeId, item);
    }

    /** Xoa anh cua bao cao ban dau khi chu san chua giai trinh; anh cua cac lan bo sung (2.3.91) khong xoa duoc. */
    @Transactional
    public void delete(UUID userId, UUID disputeId, UUID evidenceId) {
        BookingDispute dispute = requireOwn(userId, disputeId, true);
        requireAwaitingOwner(dispute, "Chi xoa duoc anh khi bao cao dang cho chu san giai trinh.");
        List<Map<String, Object>> list = mutableEvidences(dispute);
        Map<String, Object> target = list.stream().filter(item -> evidenceId.toString().equals(item.get("id"))).findFirst()
                .orElseThrow(() -> ApiException.notFound("Khong tim thay anh bang chung."));
        if (target.get("supplementId") != null) {
            throw ApiException.conflict(ErrorCode.BOOKING_DISPUTE_INVALID,
                    "Anh trong phan bo sung khong xoa duoc de giu nguyen lich su.", Map.of());
        }
        list.remove(target);
        dispute.setReporterEvidences(list);
        disputeRepository.saveAndFlush(dispute);
        storage.delete(disputeId, (String) target.get("fileName"));
    }

    /** Anh de hien thi: chi nguoi bao cao, chu san cua san hoac quan tri vien; nguoi khac nhan 404. */
    @Transactional(readOnly = true)
    public EvidenceFile file(UUID userId, UUID disputeId, UUID evidenceId) {
        BookingDispute dispute = disputeRepository.findWithParties(disputeId)
                .orElseThrow(() -> ApiException.notFound("Khong tim thay anh bang chung."));
        boolean party = dispute.getReporter().getId().equals(userId)
                || dispute.getBooking().getVenue().getOwner().getId().equals(userId);
        if (!party && !isActiveAdmin(userId)) {
            throw ApiException.notFound("Khong tim thay anh bang chung.");
        }
        Map<String, Object> item = evidencesOf(dispute, true).stream()
                .filter(entry -> evidenceId.toString().equals(entry.get("id"))).findFirst()
                .orElseThrow(() -> ApiException.notFound("Khong tim thay anh bang chung."));
        String fileName = (String) item.get("fileName");
        return new EvidenceFile(storage.resolveExisting(disputeId, fileName), VenueImageStorage.contentTypeOf(fileName));
    }

    public record EvidenceFile(Path path, String contentType) {
    }

    private boolean isActiveAdmin(UUID userId) {
        try {
            adminAccessService.requireActiveAdmin(userId);
            return true;
        } catch (ApiException exception) {
            return false;
        }
    }

    /** Danh sach anh de tra ve API (khong lo ten file tren dia). */
    public static List<DisputeEvidenceResponse> evidencesOf(BookingDispute dispute) {
        return evidencesOf(dispute, true).stream().map(item -> toResponse(dispute.getId(), item)).toList();
    }

    private static List<Map<String, Object>> evidencesOf(BookingDispute dispute, boolean unused) {
        return dispute.getReporterEvidences() == null ? List.of() : dispute.getReporterEvidences();
    }

    private static List<Map<String, Object>> mutableEvidences(BookingDispute dispute) {
        return new ArrayList<>(evidencesOf(dispute, true));
    }

    static DisputeEvidenceResponse toResponse(UUID disputeId, Map<String, Object> item) {
        String supplement = (String) item.get("supplementId");
        return new DisputeEvidenceResponse(UUID.fromString((String) item.get("id")), (String) item.get("originalName"),
                ((Number) item.get("size")).longValue(), (String) item.get("contentType"), Instant.parse((String) item.get("uploadedAt")),
                supplement == null ? null : UUID.fromString(supplement),
                "/api/v1/booking-disputes/" + disputeId + "/evidences/" + item.get("id") + "/file");
    }

    private BookingDispute requireOwn(UUID userId, UUID disputeId, boolean forUpdate) {
        return (forUpdate ? disputeRepository.findByIdForUpdate(disputeId) : disputeRepository.findWithParties(disputeId))
                .filter(item -> item.getReporter().getId().equals(userId))
                .orElseThrow(() -> ApiException.notFound("Khong tim thay bao cao."));
    }

    private static void requireAwaitingOwner(BookingDispute dispute, String message) {
        if (!ACTIVE_BEFORE_OWNER.equals(dispute.getStatus())) {
            throw ApiException.conflict(ErrorCode.BOOKING_DISPUTE_INVALID, message, Map.of());
        }
    }

    private static byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new UncheckedIOException("Khong doc duoc file tai len", exception);
        }
    }

    /** Ten file goc chi de hien thi: bo duong dan, ky tu dieu khien va cat gon. */
    static String cleanName(String name) {
        String value = name == null ? "anh" : name.replace('\\', '/');
        value = value.substring(value.lastIndexOf('/') + 1).replaceAll("\\p{Cntrl}", "").trim();
        if (value.isEmpty()) value = "anh";
        return value.length() > 120 ? value.substring(0, 120) : value;
    }

    private static ApiException invalidImage(String message) {
        return ApiException.badRequest(ErrorCode.IMAGE_INVALID, message, Map.of("file", message));
    }
}
