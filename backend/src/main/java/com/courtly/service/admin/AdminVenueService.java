package com.courtly.service.admin;

import com.courtly.api.admin.dto.AdminVenueResponses;
import com.courtly.common.enums.ActiveStatus;
import com.courtly.common.enums.ApprovalStatus;
import com.courtly.common.enums.CourtStatus;
import com.courtly.common.enums.VenueStatus;
import com.courtly.common.exception.ApiException;
import com.courtly.common.exception.ErrorCode;
import com.courtly.domain.account.User;
import com.courtly.domain.venue.AdminVenueQueries;
import com.courtly.domain.venue.Court;
import com.courtly.domain.venue.CourtPriceRule;
import com.courtly.domain.venue.CourtRepository;
import com.courtly.domain.venue.Venue;
import com.courtly.domain.venue.VenueImage;
import com.courtly.domain.venue.VenueImageRepository;
import com.courtly.domain.venue.VenueOperatingHours;
import com.courtly.domain.venue.VenueOperatingHoursRepository;
import com.courtly.domain.venue.VenueRepository;
import com.courtly.service.audit.AuditService;
import com.courtly.service.owner.OwnerVenueService;
import com.courtly.service.owner.VenueCatalog;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 2.3.34 - quan tri vien duyet san cau long.
 *
 * <p>Chi san dang cho duyet va dat du dieu kien hien thi (nam o Da Nang, co san con hoat dong,
 * gio hoat dong va gia) moi duoc duyet. San duoc duyet chuyen sang {@code approved} + {@code active}
 * nen moi hien voi nguoi choi.
 */
@Service
@RequiredArgsConstructor
public class AdminVenueService {

    static final String APPROVE_PERMISSION = "venue.approve";

    private final AdminAccessService accessService;
    private final AdminVenueQueries venueQueries;
    private final VenueRepository venueRepository;
    private final CourtRepository courtRepository;
    private final VenueImageRepository imageRepository;
    private final VenueOperatingHoursRepository hoursRepository;
    private final OwnerVenueService ownerVenueService;
    private final AuditService auditService;

    /**
     * @param approval all hoac pending, approved, rejected
     * @param district all hoac mot quan / huyen
     */
    @Transactional(readOnly = true)
    public AdminVenueResponses.ListResponse list(UUID adminId, String approval, String district, String q, int page, int size) {
        accessService.requirePermission(adminId, APPROVE_PERMISSION);
        List<ApprovalStatus> statuses = parseApproval(approval);
        boolean anyDistrict = district == null || district.isBlank() || "all".equals(district);
        if (!anyDistrict && !VenueCatalog.DISTRICTS.contains(district)) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "Quan / huyen khong hop le.",
                    Map.of("district", "Quan / huyen khong thuoc Da Nang"));
        }
        String keyword = "%" + (q == null ? "" : q.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")) + "%";
        Page<Venue> venues = venueQueries.search(statuses, anyDistrict, anyDistrict ? "" : district, keyword, PageRequest.of(page, size));

        Map<UUID, Long> courts = new HashMap<>();
        if (!venues.isEmpty()) {
            courtRepository.countByVenueIds(venues.getContent().stream().map(Venue::getId).toList())
                    .forEach(row -> courts.put(row.getVenueId(), row.getCourtCount()));
        }
        Map<String, Long> counts = new LinkedHashMap<>();
        Arrays.stream(ApprovalStatus.values()).forEach(value -> counts.put(value.getValue(), 0L));
        venueQueries.countByApproval().forEach(row -> counts.put(row.getStatus().getValue(), row.getTotal()));

        return new AdminVenueResponses.ListResponse(venues.getContent().stream()
                .map(venue -> new AdminVenueResponses.Summary(venue.getId(), venue.getName(), venue.getDistrict(), venue.getAddress(),
                        venue.getOwner().getFullName(), venue.getOwner().getEmail(), venue.getStatus().getValue(),
                        venue.getApprovalStatus().getValue(), courts.getOrDefault(venue.getId(), 0L), venue.getCreatedAt()))
                .toList(),
                venues.getNumber(), venues.getSize(), venues.getTotalElements(), venues.getTotalPages(), counts);
    }

    @Transactional(readOnly = true)
    public AdminVenueResponses.Detail get(UUID adminId, UUID venueId) {
        accessService.requirePermission(adminId, APPROVE_PERMISSION);
        return toDetail(requireVenue(venueId));
    }

    @Transactional
    public AdminVenueResponses.Detail approve(UUID adminId, UUID venueId) {
        User admin = accessService.requirePermission(adminId, APPROVE_PERMISSION);
        Venue venue = requireVenue(venueId);
        if (venue.getApprovalStatus() != ApprovalStatus.PENDING) {
            throw ApiException.conflict(ErrorCode.VENUE_REVIEW_INVALID, "Chi duyet duoc san dang cho duyet.",
                    Map.of("approvalStatus", "San khong o trang thai cho duyet"));
        }
        List<AdminVenueResponses.Check> checks = checksOf(venue);
        List<String> missing = checks.stream().filter(check -> !check.ok()).map(AdminVenueResponses.Check::label).toList();
        if (!missing.isEmpty()) {
            throw ApiException.conflict(ErrorCode.VENUE_REVIEW_INVALID, "San chua du dieu kien hien thi, chua the duyet.",
                    Map.of("checks", String.join("; ", missing)));
        }

        Map<String, Object> before = Map.of("approvalStatus", venue.getApprovalStatus().getValue(), "status", venue.getStatus().getValue());
        venue.setApprovalStatus(ApprovalStatus.APPROVED);
        venue.setStatus(VenueStatus.ACTIVE);
        venue.setApprovedBy(admin);
        venue.setApprovedAt(Instant.now());
        venueRepository.saveAndFlush(venue);
        auditService.record(admin, "venue.approve", "venues", venue.getId(), before,
                Map.of("approvalStatus", "approved", "status", "active", "venueName", venue.getName()));
        return toDetail(venue);
    }

    private Venue requireVenue(UUID venueId) {
        return venueRepository.findById(venueId).orElseThrow(() -> ApiException.notFound("Khong tim thay san."));
    }

    private AdminVenueResponses.Detail toDetail(Venue venue) {
        List<Court> courts = courtRepository.findAllWithPriceRules(venue.getId());
        List<AdminVenueResponses.Check> checks = checksOf(venue, courts);
        boolean canApprove = venue.getApprovalStatus() == ApprovalStatus.PENDING && checks.stream().allMatch(AdminVenueResponses.Check::ok);
        User owner = venue.getOwner();
        return new AdminVenueResponses.Detail(
                ownerVenueService.get(owner.getId(), venue.getId()),
                new AdminVenueResponses.Owner(owner.getId(), owner.getFullName(), owner.getEmail(), owner.getPhone()),
                imageRepository.findAllByVenueIdOrderByDisplayOrderAsc(venue.getId()).stream()
                        .map(image -> new AdminVenueResponses.Image(image.getImageUrl(), image.getCaption(), image.isCover())).toList(),
                hoursRepository.findAllByVenueIdOrderByDayOfWeekAsc(venue.getId()).stream()
                        .map(hours -> new AdminVenueResponses.Hours(hours.getDayOfWeek(), hours.isClosed(), hours.getOpenTime(), hours.getCloseTime())).toList(),
                courts.stream().sorted(Comparator.comparing(Court::getCourtCode)).map(AdminVenueService::toCourtPrice).toList(),
                checks, canApprove,
                venue.getApprovedBy() == null ? null : venue.getApprovedBy().getFullName(), venue.getApprovedAt());
    }

    private List<AdminVenueResponses.Check> checksOf(Venue venue) {
        return checksOf(venue, courtRepository.findAllWithPriceRules(venue.getId()));
    }

    /** Dieu kien de san du hien thi cho nguoi choi. */
    private List<AdminVenueResponses.Check> checksOf(Venue venue, List<Court> courts) {
        List<Court> active = courts.stream().filter(court -> court.getStatus() == CourtStatus.ACTIVE).toList();
        boolean location = VenueCatalog.PROVINCE.equals(venue.getProvince()) && VenueCatalog.DISTRICTS.contains(venue.getDistrict())
                && venue.getLatitude() != null && venue.getLongitude() != null
                && VenueCatalog.isInDaNang(venue.getLatitude(), venue.getLongitude());
        boolean hours = hoursRepository.findAllByVenueIdOrderByDayOfWeekAsc(venue.getId()).stream()
                .anyMatch(item -> !item.isClosed() && item.getOpenTime() != null && item.getCloseTime() != null);
        boolean prices = !active.isEmpty() && active.stream().allMatch(court -> activeRules(court) > 0);
        List<AdminVenueResponses.Check> checks = new ArrayList<>();
        checks.add(new AdminVenueResponses.Check("location", "Dia chi va toa do nam trong thanh pho Da Nang", location));
        checks.add(new AdminVenueResponses.Check("active_court", "Co it nhat mot san con dang hoat dong", !active.isEmpty()));
        checks.add(new AdminVenueResponses.Check("operating_hours", "Co it nhat mot ngay co gio hoat dong", hours));
        checks.add(new AdminVenueResponses.Check("prices", "Moi san con dang hoat dong deu co bang gia", prices));
        return checks;
    }

    private static int activeRules(Court court) {
        LocalDate today = LocalDate.now();
        return (int) court.getPriceRules().stream()
                .filter(rule -> rule.getStatus() == ActiveStatus.ACTIVE)
                .filter(rule -> rule.getEffectiveTo() == null || !rule.getEffectiveTo().isBefore(today))
                .count();
    }

    private static AdminVenueResponses.CourtPrice toCourtPrice(Court court) {
        List<BigDecimal> prices = court.getPriceRules().stream()
                .filter(rule -> rule.getStatus() == ActiveStatus.ACTIVE).map(CourtPriceRule::getPricePerHour).toList();
        return new AdminVenueResponses.CourtPrice(court.getId(), court.getName(), court.getCourtCode(), court.getStatus().getValue(),
                activeRules(court), prices.stream().min(BigDecimal::compareTo).orElse(null),
                prices.stream().max(BigDecimal::compareTo).orElse(null));
    }

    private static List<ApprovalStatus> parseApproval(String approval) {
        if (approval == null || approval.isBlank() || "all".equals(approval)) {
            return List.of(ApprovalStatus.values());
        }
        try {
            return List.of(ApprovalStatus.from(approval));
        } catch (IllegalArgumentException exception) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "Trang thai duyet khong hop le.",
                    Map.of("approval", "Chi nhan all, pending, approved hoac rejected"));
        }
    }
}
