package com.courtly.service.owner;

import com.courtly.api.owner.dto.CourtBlockItem;
import com.courtly.api.owner.dto.CreateCourtBlockRequest;
import com.courtly.common.PageResponse;
import com.courtly.common.enums.BlockStatus;
import com.courtly.common.exception.ApiException;
import com.courtly.common.exception.ErrorCode;
import com.courtly.domain.account.User;
import com.courtly.domain.booking.BookingItem;
import com.courtly.domain.booking.BookingItemRepository;
import com.courtly.domain.venue.Court;
import com.courtly.domain.venue.CourtBlock;
import com.courtly.domain.venue.CourtBlockRepository;
import com.courtly.domain.venue.Venue;
import com.courtly.service.audit.AuditService;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Chu san khoa (2.3.10) khung gio san vi bao tri hoac su kien.
 *
 * <p>Khung gio bi khoa bien mat khoi lich trong cua nguoi choi (2.1.26) va khong bao gia duoc
 * (2.1.27). Khong cho khoa len khung gio dang co booking: khoa se de nguoi choi da duoc giu
 * cho den san ma san khong mo.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OwnerCourtBlockService {

    /** Mot lan khoa toi da 31 ngay, tranh khoa nham ca nam. */
    static final Duration MAX_DURATION = Duration.ofDays(31);

    private final OwnedVenueLookup ownedVenueLookup;
    private final CourtBlockRepository blockRepository;
    private final BookingItemRepository bookingItemRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public PageResponse<CourtBlockItem> list(UUID ownerId, UUID venueId, int page, int size) {
        ownedVenueLookup.requireVenue(ownerId, venueId);
        Page<CourtBlock> blocks = blockRepository.findUpcomingActiveByVenue(venueId, Instant.now(), PageRequest.of(page, size));
        return PageResponse.from(blocks, blocks.getContent().stream().map(OwnerCourtBlockService::toItem).toList());
    }

    @Transactional
    public List<CourtBlockItem> create(UUID ownerId, UUID venueId, CreateCourtBlockRequest request) {
        Venue venue = ownedVenueLookup.requireVenue(ownerId, venueId);
        validateRange(request.startTime(), request.endTime());

        Set<UUID> requested = new HashSet<>(request.courtIds());
        if (requested.size() != request.courtIds().size()) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "San con bi chon trung.",
                    Map.of("courtIds", "Moi san con chi duoc chon mot lan"));
        }
        Map<UUID, Court> courts = venue.getCourts().stream()
                .filter(court -> requested.contains(court.getId()))
                .collect(Collectors.toMap(Court::getId, court -> court));
        if (courts.size() != requested.size()) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "Co san con khong thuoc san nay.",
                    Map.of("courtIds", "San con khong ton tai trong san nay"));
        }

        requireNoBookings(courts, request);
        requireNoOverlappingBlock(courts, request);

        User actor = venue.getOwner();
        List<CourtBlock> created = new ArrayList<>();
        for (UUID courtId : request.courtIds()) {
            CourtBlock block = new CourtBlock();
            block.setId(UUID.randomUUID());
            block.setCourt(courts.get(courtId));
            block.setStartTime(request.startTime());
            block.setEndTime(request.endTime());
            block.setReason(request.reason());
            block.setStatus(BlockStatus.ACTIVE);
            block.setCreatedBy(actor);
            created.add(block);
        }
        blockRepository.saveAllAndFlush(created);
        for (CourtBlock block : created) {
            auditService.record(actor, "court_block.create", "court_blocks", block.getId(), null, snapshot(block));
        }
        log.info("Chu san {} khoa {} san con cua san {} tu {} den {}", ownerId, created.size(), venueId,
                request.startTime(), request.endTime());
        return created.stream().map(OwnerCourtBlockService::toItem).toList();
    }

    private static void validateRange(Instant start, Instant end) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (!end.isAfter(start)) {
            errors.put("endTime", "Thoi diem ket thuc phai sau thoi diem bat dau");
        } else if (Duration.between(start, end).compareTo(MAX_DURATION) > 0) {
            errors.put("endTime", "Moi lan khoa toi da 31 ngay");
        }
        if (!end.isAfter(Instant.now())) {
            errors.put("startTime", "Khong khoa duoc khoang thoi gian da qua");
        }
        if (!errors.isEmpty()) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "Khoang khoa chua hop le.", errors);
        }
    }

    private void requireNoBookings(Map<UUID, Court> courts, CreateCourtBlockRequest request) {
        List<BookingItem> items = bookingItemRepository.findOccupiedWithBookingForCourts(
                courts.keySet(), request.startTime(), request.endTime());
        if (items.isEmpty()) {
            return;
        }
        String codes = items.stream().map(item -> item.getBooking().getBookingCode()).distinct().limit(5)
                .collect(Collectors.joining(", "));
        throw ApiException.conflict(ErrorCode.BLOCK_HAS_BOOKINGS,
                "Khoang gio nay dang co " + items.size() + " khung da duoc dat (" + codes + "). "
                        + "Hay xu ly cac booking nay truoc khi khoa.",
                Map.of("startTime", "Trung booking " + codes));
    }

    private void requireNoOverlappingBlock(Map<UUID, Court> courts, CreateCourtBlockRequest request) {
        List<CourtBlock> overlapping = blockRepository.findActiveOverlapping(
                courts.keySet(), request.startTime(), request.endTime());
        if (!overlapping.isEmpty()) {
            String names = overlapping.stream().map(block -> block.getCourt().getName()).distinct()
                    .collect(Collectors.joining(", "));
            throw ApiException.conflict(ErrorCode.BLOCK_OVERLAP,
                    "Khoang gio nay da duoc khoa tren: " + names + ".",
                    Map.of("startTime", "Da co khoang khoa trung tren " + names));
        }
    }

    private static Map<String, Object> snapshot(CourtBlock block) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("venueId", block.getCourt().getVenue().getId().toString());
        data.put("courtId", block.getCourt().getId().toString());
        data.put("startTime", block.getStartTime().toString());
        data.put("endTime", block.getEndTime().toString());
        data.put("reason", block.getReason());
        data.put("status", block.getStatus().getValue());
        return data;
    }

    static CourtBlockItem toItem(CourtBlock block) {
        return new CourtBlockItem(block.getId(), block.getCourt().getId(), block.getCourt().getName(),
                block.getCourt().getCourtCode(), block.getStartTime(), block.getEndTime(), block.getReason(),
                block.getStatus().getValue(),
                block.getCreatedBy() == null ? null : block.getCreatedBy().getFullName(), block.getCreatedAt());
    }
}
