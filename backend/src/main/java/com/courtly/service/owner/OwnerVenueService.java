package com.courtly.service.owner;

import com.courtly.api.owner.dto.CourtInput;
import com.courtly.api.owner.dto.CreateVenueRequest;
import com.courtly.api.owner.dto.UpdateCourtRequest;
import com.courtly.api.owner.dto.UpdateVenueRequest;
import com.courtly.api.owner.dto.OwnerVenueDetailResponse;
import com.courtly.api.owner.dto.OwnerVenueSummaryResponse;
import com.courtly.api.owner.dto.VenueFormOptionsResponse;
import com.courtly.api.owner.dto.VenueServiceInput;
import com.courtly.common.PageResponse;
import com.courtly.common.PhoneNumbers;
import com.courtly.common.enums.ApprovalStatus;
import com.courtly.common.enums.CourtStatus;
import com.courtly.common.enums.VenueStatus;
import com.courtly.common.exception.ApiException;
import com.courtly.common.exception.ErrorCode;
import com.courtly.domain.account.User;
import com.courtly.domain.account.UserRepository;
import com.courtly.domain.booking.BookingItemRepository;
import com.courtly.domain.venue.Court;
import com.courtly.domain.venue.CourtRepository;
import com.courtly.domain.venue.Service;
import com.courtly.domain.venue.ServiceRepository;
import com.courtly.domain.venue.Venue;
import com.courtly.domain.venue.VenueImageRepository;
import com.courtly.domain.venue.VenueRepository;
import com.courtly.domain.venue.VenueService;
import com.courtly.service.audit.AuditService;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

/** Chu san quan ly san cua minh: xem va them moi (2.3.3), cap nhat thong tin va san con (2.3.4). */
@Slf4j
@org.springframework.stereotype.Service
@RequiredArgsConstructor
public class OwnerVenueService {

    private static final int SLUG_MAX_LENGTH = 200;

    private final VenueRepository venueRepository;
    private final CourtRepository courtRepository;
    private final ServiceRepository serviceRepository;
    private final VenueImageRepository venueImageRepository;
    private final UserRepository userRepository;
    private final BookingItemRepository bookingItemRepository;
    private final OwnedVenueLookup ownedVenueLookup;
    private final AuditService auditService;

    /** Danh muc cho form: quan/huyen Da Nang, tien ich, loai san, mat san, khung toa do. */
    @Transactional(readOnly = true)
    public VenueFormOptionsResponse options() {
        return new VenueFormOptionsResponse(
                VenueCatalog.DISTRICTS,
                serviceRepository.findAllByOrderByNameAsc().stream()
                        .map(service -> new VenueFormOptionsResponse.Option(service.getCode(), service.getName()))
                        .toList(),
                toOptions(VenueCatalog.COURT_TYPES),
                toOptions(VenueCatalog.SURFACE_TYPES),
                new VenueFormOptionsResponse.Bounds(
                        VenueCatalog.MIN_LATITUDE, VenueCatalog.MAX_LATITUDE,
                        VenueCatalog.MIN_LONGITUDE, VenueCatalog.MAX_LONGITUDE),
                VenueCatalog.MAX_COURTS);
    }

    /** San cua chu san, moi trang thai (ke ca cho duyet), moi nhat truoc. */
    @Transactional(readOnly = true)
    public PageResponse<OwnerVenueSummaryResponse> list(UUID ownerId, int page, int size) {
        Page<Venue> venues = venueRepository.findByOwnerIdOrderByCreatedAtDesc(ownerId, PageRequest.of(page, size));
        Set<UUID> venueIds = venues.getContent().stream().map(Venue::getId).collect(Collectors.toSet());

        Map<UUID, Long> courtCounts = venueIds.isEmpty() ? Map.of()
                : courtRepository.countByVenueIds(venueIds).stream().collect(Collectors.toMap(
                        CourtRepository.VenueCourtCount::getVenueId,
                        CourtRepository.VenueCourtCount::getCourtCount));
        Map<UUID, String> covers = venueIds.isEmpty() ? Map.of()
                : venueImageRepository.findCoversByVenueIds(venueIds).stream().collect(Collectors.toMap(
                        VenueImageRepository.VenueCoverView::getVenueId,
                        VenueImageRepository.VenueCoverView::getImageUrl));

        return PageResponse.from(venues, venues.getContent().stream()
                .map(venue -> new OwnerVenueSummaryResponse(
                        venue.getId(), venue.getName(), venue.getSlug(), venue.getDistrict(),
                        venue.getAddress(), venue.getStatus().getValue(), venue.getApprovalStatus().getValue(),
                        courtCounts.getOrDefault(venue.getId(), 0L), covers.get(venue.getId()),
                        venue.getCreatedAt()))
                .toList());
    }

    /**
     * Them san moi (2.3.3).
     *
     * <p>San moi o trang thai cho duyet va chua hien voi nguoi choi: tim kiem, chi tiet va dat
     * san cong khai deu chi lay san {@code active} + {@code approved}.
     */
    @Transactional
    public OwnerVenueDetailResponse create(UUID ownerId, CreateVenueRequest request) {
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> ApiException.notFound("Khong tim thay tai khoan chu san."));

        String phone = validateContact(request.district(), request.latitude(), request.longitude(), request.phone());
        Map<String, Service> services = resolveServices(request.services());
        validateCourts(request.courts());

        Venue venue = new Venue();
        venue.setId(UUID.randomUUID());
        venue.setOwner(owner);
        venue.setName(request.name());
        venue.setSlug(uniqueSlug(request.name()));
        venue.setDescription(request.description());
        venue.setAddress(request.address());
        venue.setWard(request.ward());
        venue.setDistrict(request.district());
        venue.setProvince(VenueCatalog.PROVINCE);
        venue.setLatitude(request.latitude());
        venue.setLongitude(request.longitude());
        venue.setPhone(phone);
        venue.setEmail(request.email());
        venue.setStatus(VenueStatus.PENDING);
        venue.setApprovalStatus(ApprovalStatus.PENDING);

        for (VenueServiceInput input : request.services()) {
            venue.getVenueServices().add(new VenueService(
                    venue, services.get(input.code()), input.price(), trimToNull(input.note())));
        }
        for (CourtInput input : request.courts()) {
            Court court = new Court();
            court.setId(UUID.randomUUID());
            court.setVenue(venue);
            applyCourt(court, input);
            court.setStatus(CourtStatus.ACTIVE);
            venue.getCourts().add(court);
        }

        venueRepository.saveAndFlush(venue);
        auditService.record(owner, "venue.create", "venues", venue.getId(), null, Map.of(
                "name", venue.getName(),
                "district", venue.getDistrict(),
                "courtCount", venue.getCourts().size(),
                "approvalStatus", venue.getApprovalStatus().getValue()));
        log.info("Chu san {} them san moi {} ({}), cho duyet", ownerId, venue.getId(), venue.getSlug());
        return toDetail(venue);
    }

    /** Chi tiet mot san cua chu san, ke ca san chua duyet (2.3.4). */
    @Transactional(readOnly = true)
    public OwnerVenueDetailResponse get(UUID ownerId, UUID venueId) {
        return toDetail(ownedVenueLookup.requireVenue(ownerId, venueId));
    }

    /**
     * Cap nhat thong tin chung va tien ich (2.3.4).
     *
     * <p>Khong dua san da duyet ve trang thai cho duyet: chuc nang duyet san cua quan tri vien
     * (2.3.34) chua co, dua ve cho duyet se an san khoi nguoi choi ma khong ai mo lai duoc.
     * Moi thay doi deu duoc ghi audit_logs de quan tri vien doi chieu.
     */
    @Transactional
    public OwnerVenueDetailResponse update(UUID ownerId, UUID venueId, UpdateVenueRequest request) {
        Venue venue = ownedVenueLookup.requireVenue(ownerId, venueId);
        String phone = validateContact(request.district(), request.latitude(), request.longitude(), request.phone());
        Map<String, Service> services = resolveServices(request.services());
        Map<String, Object> before = venueSnapshot(venue);

        venue.setName(request.name());
        venue.setDescription(request.description());
        venue.setAddress(request.address());
        venue.setWard(request.ward());
        venue.setDistrict(request.district());
        venue.setLatitude(request.latitude());
        venue.setLongitude(request.longitude());
        venue.setPhone(phone);
        venue.setEmail(request.email());
        replaceServices(venue, request.services(), services);

        venueRepository.saveAndFlush(venue);
        auditService.record(venue.getOwner(), "venue.update", "venues", venue.getId(), before, venueSnapshot(venue));
        log.info("Chu san {} cap nhat san {}", ownerId, venueId);
        return toDetail(venue);
    }

    /** Them san con vao san da co (2.3.4). */
    /** 2.3.35 - gui lai san bi tu choi de duyet lai sau khi da chinh sua; san quay ve cho duyet. */
    @Transactional
    public OwnerVenueDetailResponse resubmit(UUID ownerId, UUID venueId) {
        Venue venue = ownedVenueLookup.requireVenue(ownerId, venueId);
        if (venue.getApprovalStatus() != ApprovalStatus.REJECTED) {
            throw ApiException.conflict(ErrorCode.VENUE_REVIEW_INVALID, "Chi gui duyet lai duoc san da bi tu choi.",
                    Map.of("approvalStatus", "San khong o trang thai bi tu choi"));
        }
        venue.setApprovalStatus(ApprovalStatus.PENDING);
        venue.setStatus(VenueStatus.PENDING);
        auditService.record(venue.getOwner(), "venue.resubmit", "venues", venue.getId(),
                Map.of("approvalStatus", "rejected"), Map.of("approvalStatus", "pending", "venueName", venue.getName()));
        return toDetail(venue);
    }

    @Transactional
    public OwnerVenueDetailResponse.CourtItem addCourt(UUID ownerId, UUID venueId, CourtInput input) {
        Venue venue = ownedVenueLookup.requireVenue(ownerId, venueId);
        if (venue.getCourts().size() >= VenueCatalog.MAX_COURTS) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "Moi san co toi da " + VenueCatalog.MAX_COURTS + " san con.", Map.of());
        }
        validateCourt(input, "");
        requireUniqueCourtCode(venue, input.courtCode(), null);

        Court court = new Court();
        court.setId(UUID.randomUUID());
        court.setVenue(venue);
        applyCourt(court, input);
        court.setStatus(CourtStatus.ACTIVE);
        venue.getCourts().add(court);
        courtRepository.saveAndFlush(court);

        auditService.record(venue.getOwner(), "court.create", "courts", court.getId(), null, courtSnapshot(court));
        return toCourtItem(court);
    }

    /**
     * Sua san con (2.3.4).
     *
     * <p>Dua san con dang hoat dong sang bao tri / ngung bi chan khi con booking chua choi:
     * nguoi choi da duoc giu cho tren san do, khong duoc de ho den noi ma san khong mo.
     */
    @Transactional
    public OwnerVenueDetailResponse.CourtItem updateCourt(UUID ownerId, UUID venueId, UUID courtId,
                                                          UpdateCourtRequest request) {
        Court court = ownedVenueLookup.requireCourt(ownerId, venueId, courtId);
        CourtInput input = request.toCourtInput();
        validateCourt(input, "");
        requireUniqueCourtCode(court.getVenue(), input.courtCode(), court.getId());

        CourtStatus nextStatus = CourtStatus.from(request.status());
        if (court.getStatus() == CourtStatus.ACTIVE && nextStatus != CourtStatus.ACTIVE) {
            long upcoming = bookingItemRepository.countUpcomingForCourt(court.getId(), java.time.Instant.now());
            if (upcoming > 0) {
                throw ApiException.conflict(ErrorCode.COURT_HAS_UPCOMING_BOOKINGS,
                        "San con con " + upcoming + " khung gio da duoc dat chua choi. "
                                + "Hay xu ly cac booking nay truoc khi tam ngung san con.",
                        Map.of("status", "San con con " + upcoming + " khung gio da duoc dat"));
            }
        }

        Map<String, Object> before = courtSnapshot(court);
        applyCourt(court, input);
        court.setStatus(nextStatus);
        courtRepository.saveAndFlush(court);
        auditService.record(court.getVenue().getOwner(), "court.update", "courts", court.getId(),
                before, courtSnapshot(court));
        return toCourtItem(court);
    }

    // ---------------------------------------------------------------- validate

    private void requireUniqueCourtCode(Venue venue, String courtCode, UUID exceptCourtId) {
        boolean taken = venue.getCourts().stream()
                .anyMatch(other -> !other.getId().equals(exceptCourtId)
                        && other.getCourtCode().equalsIgnoreCase(courtCode));
        if (taken) {
            throw ApiException.conflict(ErrorCode.VALIDATION_FAILED, "Ma san con da ton tai trong san nay.",
                    Map.of("courtCode", "Ma san con da ton tai trong san nay"));
        }
    }

    /**
     * Quan/huyen, toa do va so dien thoai cua san.
     *
     * @return so dien thoai da chuan hoa
     */
    String validateContact(String district, BigDecimal latitude, BigDecimal longitude, String rawPhone) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (!VenueCatalog.DISTRICTS.contains(district)) {
            errors.put("district", "He thong chi phuc vu cac quan/huyen cua Da Nang");
        }
        if (!VenueCatalog.isInDaNang(latitude, longitude)) {
            errors.put("latitude", "Vi tri san phai nam trong thanh pho Da Nang");
        }
        String phone = PhoneNumbers.normalize(rawPhone);
        if (!PhoneNumbers.isContactNumber(phone)) {
            errors.put("phone", "So dien thoai di dong 10 so hoac so co dinh 11 so (vd 0236 3xxx xxx)");
        }
        if (!errors.isEmpty()) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "Thong tin san chua hop le, vui long kiem tra lai.", errors);
        }
        return phone;
    }

    Map<String, Service> resolveServices(List<VenueServiceInput> inputs) {
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < inputs.size(); i++) {
            if (!codes.add(inputs.get(i).code())) {
                throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "Tien ich bi chon trung.",
                        Map.of("services[" + i + "].code", "Tien ich nay da duoc chon"));
            }
        }
        Map<String, Service> found = serviceRepository.findByCodeIn(codes).stream()
                .collect(Collectors.toMap(Service::getCode, Function.identity()));
        for (int i = 0; i < inputs.size(); i++) {
            if (!found.containsKey(inputs.get(i).code())) {
                throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "Tien ich khong ton tai.",
                        Map.of("services[" + i + "].code", "Tien ich khong co trong danh muc"));
            }
        }
        return found;
    }

    void validateCourts(List<CourtInput> courts) {
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < courts.size(); i++) {
            validateCourt(courts.get(i), "courts[" + i + "].");
            if (!codes.add(courts.get(i).courtCode())) {
                throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "Ma san con bi trung.",
                        Map.of("courts[" + i + "].courtCode", "Ma san con da ton tai trong san nay"));
            }
        }
    }

    void validateCourt(CourtInput court, String fieldPrefix) {
        if (!VenueCatalog.COURT_TYPES.containsKey(court.courtType())) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "Loai san khong hop le.",
                    Map.of(fieldPrefix + "courtType", "Chi nhan " + String.join(", ", VenueCatalog.COURT_TYPES.keySet())));
        }
        if (court.surfaceType() != null && !VenueCatalog.SURFACE_TYPES.containsKey(court.surfaceType())) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "Mat san khong hop le.",
                    Map.of(fieldPrefix + "surfaceType", "Chi nhan " + String.join(", ", VenueCatalog.SURFACE_TYPES.keySet())));
        }
    }

    static void applyCourt(Court court, CourtInput input) {
        court.setName(input.name());
        court.setCourtCode(input.courtCode());
        court.setCourtType(input.courtType());
        court.setSurfaceType(input.surfaceType());
        court.setIndoor(input.indoor());
    }

    // ---------------------------------------------------------------- helpers

    /**
     * Cap nhat tien ich theo tung ma thay vi xoa het roi them lai: Hibernate chay INSERT truoc
     * DELETE trong cung mot lan flush, xoa-roi-them cung ma se dam vao khoa chinh.
     */
    private static void replaceServices(Venue venue, List<VenueServiceInput> inputs, Map<String, Service> services) {
        Map<String, VenueServiceInput> wanted = inputs.stream()
                .collect(Collectors.toMap(VenueServiceInput::code, Function.identity()));
        venue.getVenueServices().removeIf(item -> !wanted.containsKey(item.getService().getCode()));
        Map<String, VenueService> existing = venue.getVenueServices().stream()
                .collect(Collectors.toMap(item -> item.getService().getCode(), Function.identity()));
        for (VenueServiceInput input : inputs) {
            VenueService item = existing.get(input.code());
            if (item == null) {
                venue.getVenueServices().add(new VenueService(
                        venue, services.get(input.code()), input.price(), trimToNull(input.note())));
            } else {
                item.setPrice(input.price());
                item.setNote(trimToNull(input.note()));
            }
        }
    }

    private static Map<String, Object> venueSnapshot(Venue venue) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", venue.getName());
        data.put("address", venue.getAddress());
        data.put("ward", venue.getWard());
        data.put("district", venue.getDistrict());
        data.put("latitude", venue.getLatitude());
        data.put("longitude", venue.getLongitude());
        data.put("phone", venue.getPhone());
        data.put("email", venue.getEmail());
        data.put("services", venue.getVenueServices().stream()
                .map(item -> item.getService().getCode()).sorted().toList());
        return data;
    }

    private static Map<String, Object> courtSnapshot(Court court) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("venueId", court.getVenue().getId().toString());
        data.put("name", court.getName());
        data.put("courtCode", court.getCourtCode());
        data.put("courtType", court.getCourtType());
        data.put("surfaceType", court.getSurfaceType());
        data.put("indoor", court.isIndoor());
        data.put("status", court.getStatus().getValue());
        return data;
    }

    private static OwnerVenueDetailResponse.CourtItem toCourtItem(Court court) {
        return new OwnerVenueDetailResponse.CourtItem(
                court.getId(), court.getName(), court.getCourtCode(), court.getCourtType(),
                court.getSurfaceType(), court.isIndoor(), court.getStatus().getValue());
    }

    /** "Sân Cầu Lông Đà Nẵng" -> "san-cau-long-da-nang", them -2, -3 neu trung. */
    private String uniqueSlug(String name) {
        String base = slugify(name);
        String candidate = base;
        int suffix = 2;
        while (venueRepository.existsBySlug(candidate)) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }

    static String slugify(String name) {
        String ascii = Normalizer.normalize(name.replace('đ', 'd').replace('Đ', 'D'), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        String slug = ascii.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        if (slug.isEmpty()) {
            slug = "san";
        }
        return slug.length() > SLUG_MAX_LENGTH ? slug.substring(0, SLUG_MAX_LENGTH) : slug;
    }

    private static List<VenueFormOptionsResponse.Option> toOptions(Map<String, String> values) {
        return values.entrySet().stream()
                .map(entry -> new VenueFormOptionsResponse.Option(entry.getKey(), entry.getValue()))
                .toList();
    }

    static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    OwnerVenueDetailResponse toDetail(Venue venue) {
        return new OwnerVenueDetailResponse(
                venue.getId(), venue.getName(), venue.getSlug(), venue.getDescription(),
                venue.getAddress(), venue.getWard(), venue.getDistrict(), venue.getProvince(),
                venue.getLatitude(), venue.getLongitude(), venue.getPhone(), venue.getEmail(),
                venue.getStatus().getValue(), venue.getApprovalStatus().getValue(),
                venue.getVenueServices().stream()
                        .sorted(Comparator.comparing(item -> item.getService().getName()))
                        .map(item -> new OwnerVenueDetailResponse.ServiceItem(
                                item.getService().getCode(), item.getService().getName(),
                                item.getPrice(), item.getNote()))
                        .toList(),
                venue.getCourts().stream()
                        .sorted(Comparator.comparing(Court::getCourtCode))
                        .map(OwnerVenueService::toCourtItem)
                        .toList(),
                venue.getCreatedAt(), venue.getUpdatedAt(),
                venue.getApprovalStatus() == ApprovalStatus.REJECTED ? venue.getRejectionReason() : null,
                venue.getApprovalStatus() == ApprovalStatus.REJECTED ? venue.getRejectedAt() : null);
    }
}
