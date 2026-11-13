package com.courtly.service.owner;

import com.courtly.api.owner.dto.PriceRuleInput;
import com.courtly.api.owner.dto.VenuePriceRulesResponse;
import com.courtly.common.enums.ActiveStatus;
import com.courtly.common.exception.ApiException;
import com.courtly.common.exception.ErrorCode;
import com.courtly.domain.venue.Court;
import com.courtly.domain.venue.CourtPriceRule;
import com.courtly.domain.venue.CourtPriceRuleRepository;
import com.courtly.domain.venue.Venue;
import com.courtly.service.audit.AuditService;
import com.courtly.service.booking.AvailabilityService;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 2.3.5 - chu san cap nhat bang gia cua tung san con.
 *
 * <p>Gia chi tinh luc bao gia / tao booking: booking da tao giu nguyen so tien da chot
 * (bookings.total_amount, booking_items.price), nen sua bang gia khong anh huong booking cu.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OwnerPriceRuleService {

    private static final String[] DAY_LABELS = {"", "Thu hai", "Thu ba", "Thu tu", "Thu nam", "Thu sau", "Thu bay", "Chu nhat"};

    private final OwnedVenueLookup ownedVenueLookup;
    private final CourtPriceRuleRepository priceRuleRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public VenuePriceRulesResponse list(UUID ownerId, UUID venueId) {
        Venue venue = ownedVenueLookup.requireVenue(ownerId, venueId);
        Map<UUID, List<CourtPriceRule>> rulesByCourt = priceRuleRepository.findAllForVenue(venueId).stream()
                .collect(Collectors.groupingBy(rule -> rule.getCourt().getId(), LinkedHashMap::new, Collectors.toList()));

        return new VenuePriceRulesResponse(venueId, venue.getCourts().stream()
                .sorted(Comparator.comparing(Court::getCourtCode))
                .map(court -> new VenuePriceRulesResponse.CourtPrices(
                        court.getId(), court.getName(), court.getCourtCode(), court.getStatus().getValue(),
                        rulesByCourt.getOrDefault(court.getId(), List.of()).stream()
                                .map(OwnerPriceRuleService::toItem)
                                .toList()))
                .toList());
    }

    @Transactional
    public VenuePriceRulesResponse.PriceRuleItem create(UUID ownerId, UUID venueId, UUID courtId, PriceRuleInput input) {
        Court court = ownedVenueLookup.requireCourt(ownerId, venueId, courtId);
        LocalDate effectiveFrom = input.effectiveFrom() == null ? today() : input.effectiveFrom();
        validate(input, effectiveFrom, null);

        CourtPriceRule rule = new CourtPriceRule();
        rule.setId(UUID.randomUUID());
        rule.setCourt(court);
        apply(rule, input, effectiveFrom);
        rule.setStatus(ActiveStatus.ACTIVE);
        requireNoOverlap(rule);
        // Giu hai chieu quan he dong bo: QuoteService doc gia qua court.getPriceRules().
        court.getPriceRules().add(rule);
        save(rule);

        auditService.record(court.getVenue().getOwner(), "price_rule.create", "court_price_rules", rule.getId(),
                null, snapshot(rule));
        log.info("Chu san {} them quy tac gia {} cho san con {}", ownerId, rule.getId(), courtId);
        return toItem(rule);
    }

    @Transactional
    public VenuePriceRulesResponse.PriceRuleItem update(UUID ownerId, UUID venueId, UUID courtId, UUID ruleId,
                                                        PriceRuleInput input) {
        CourtPriceRule rule = requireRule(ownerId, venueId, courtId, ruleId);
        if (rule.getStatus() != ActiveStatus.ACTIVE) {
            throw ApiException.conflict(ErrorCode.PRICE_RULE_STATE_INVALID,
                    "Quy tac gia nay da ngung ap dung, khong sua duoc nua.", Map.of());
        }
        LocalDate effectiveFrom = input.effectiveFrom() == null ? rule.getEffectiveFrom() : input.effectiveFrom();
        validate(input, effectiveFrom, rule.getEffectiveFrom());

        // Kiem tra tren ban nhap: doi entity dang quan ly truoc khi truy van se bi Hibernate
        // flush xuong database va dam vao constraint truoc khi kip bao loi ro nghia.
        CourtPriceRule draft = new CourtPriceRule();
        draft.setId(rule.getId());
        draft.setCourt(rule.getCourt());
        apply(draft, input, effectiveFrom);
        requireNoOverlap(draft);

        Map<String, Object> before = snapshot(rule);
        apply(rule, input, effectiveFrom);
        save(rule);

        auditService.record(rule.getCourt().getVenue().getOwner(), "price_rule.update", "court_price_rules",
                rule.getId(), before, snapshot(rule));
        return toItem(rule);
    }

    /** Ngung ap dung (khong xoa) de giu lai lich su gia. */
    @Transactional
    public void deactivate(UUID ownerId, UUID venueId, UUID courtId, UUID ruleId) {
        CourtPriceRule rule = requireRule(ownerId, venueId, courtId, ruleId);
        if (rule.getStatus() != ActiveStatus.ACTIVE) {
            throw ApiException.conflict(ErrorCode.PRICE_RULE_STATE_INVALID,
                    "Quy tac gia nay da ngung ap dung truoc do.", Map.of());
        }
        Map<String, Object> before = snapshot(rule);
        rule.setStatus(ActiveStatus.INACTIVE);
        priceRuleRepository.saveAndFlush(rule);
        auditService.record(rule.getCourt().getVenue().getOwner(), "price_rule.deactivate", "court_price_rules",
                rule.getId(), before, snapshot(rule));
    }

    // ---------------------------------------------------------------- helpers

    private CourtPriceRule requireRule(UUID ownerId, UUID venueId, UUID courtId, UUID ruleId) {
        Court court = ownedVenueLookup.requireCourt(ownerId, venueId, courtId);
        return priceRuleRepository.findById(ruleId)
                .filter(rule -> rule.getCourt().getId().equals(court.getId()))
                .orElseThrow(() -> ApiException.notFound("Khong tim thay quy tac gia nay."));
    }

    /**
     * @param currentFrom ngay bat dau hien tai khi sua; quy tac dang chay tu truoc thi giu
     *                    nguyen ngay cu duoc, chi ngay MOI moi phai tu hom nay tro di
     */
    private void validate(PriceRuleInput input, LocalDate effectiveFrom, LocalDate currentFrom) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (!input.endTime().isAfter(input.startTime())) {
            errors.put("endTime", "Gio ket thuc phai sau gio bat dau");
        }
        if (effectiveFrom.isBefore(today()) && !effectiveFrom.equals(currentFrom)) {
            errors.put("effectiveFrom", "Ngay ap dung phai tu hom nay tro di");
        }
        if (input.effectiveTo() != null && input.effectiveTo().isBefore(effectiveFrom)) {
            errors.put("effectiveTo", "Ngay het ap dung phai sau hoac bang ngay bat dau");
        }
        if (!errors.isEmpty()) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "Quy tac gia chua hop le, vui long kiem tra lai.", errors);
        }
    }

    private static void apply(CourtPriceRule rule, PriceRuleInput input, LocalDate effectiveFrom) {
        rule.setDayOfWeek(input.dayOfWeek() == null ? null : input.dayOfWeek().shortValue());
        rule.setStartTime(input.startTime());
        rule.setEndTime(input.endTime());
        rule.setPricePerHour(input.pricePerHour());
        rule.setEffectiveFrom(effectiveFrom);
        rule.setEffectiveTo(input.effectiveTo());
    }

    /** Hai quy tac dang hieu luc chong lan khi trung ngay, giao khung gio va giao thoi gian hieu luc. */
    private void requireNoOverlap(CourtPriceRule candidate) {
        priceRuleRepository.findActiveByCourtId(candidate.getCourt().getId()).stream()
                .filter(other -> !other.getId().equals(candidate.getId()))
                .filter(other -> overlaps(candidate, other))
                .findFirst()
                .ifPresent(other -> {
                    throw overlapError(describe(other));
                });
    }

    static boolean overlaps(CourtPriceRule a, CourtPriceRule b) {
        boolean sameDay = a.getDayOfWeek() == null || b.getDayOfWeek() == null
                || Objects.equals(a.getDayOfWeek(), b.getDayOfWeek());
        boolean timeOverlap = a.getStartTime().isBefore(b.getEndTime()) && b.getStartTime().isBefore(a.getEndTime());
        LocalDate aEnd = a.getEffectiveTo() == null ? LocalDate.MAX : a.getEffectiveTo();
        LocalDate bEnd = b.getEffectiveTo() == null ? LocalDate.MAX : b.getEffectiveTo();
        boolean dateOverlap = !a.getEffectiveFrom().isAfter(bEnd) && !b.getEffectiveFrom().isAfter(aEnd);
        return sameDay && timeOverlap && dateOverlap;
    }

    /** Lop nghiep vu da kiem tra; exclusion constraint cpr_no_overlap chan hai request dong thoi. */
    private void save(CourtPriceRule rule) {
        try {
            priceRuleRepository.saveAndFlush(rule);
        } catch (DataIntegrityViolationException exception) {
            throw overlapError("mot quy tac vua duoc luu");
        }
    }

    private static ApiException overlapError(String conflictWith) {
        return ApiException.conflict(ErrorCode.PRICE_RULE_OVERLAP,
                "Khung gio nay chong lan voi " + conflictWith + ". Hay sua hoac ngung quy tac do truoc.",
                Map.of("startTime", "Chong lan voi " + conflictWith));
    }

    private static String describe(CourtPriceRule rule) {
        String day = rule.getDayOfWeek() == null ? "Moi ngay" : DAY_LABELS[rule.getDayOfWeek()];
        return "quy tac %s %s-%s".formatted(day, format(rule.getStartTime()), format(rule.getEndTime()));
    }

    private static String format(LocalTime time) {
        return "%02d:%02d".formatted(time.getHour(), time.getMinute());
    }

    private static LocalDate today() {
        return LocalDate.now(AvailabilityService.ZONE);
    }

    private static Map<String, Object> snapshot(CourtPriceRule rule) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("venueId", rule.getCourt().getVenue().getId().toString());
        data.put("courtId", rule.getCourt().getId().toString());
        data.put("dayOfWeek", rule.getDayOfWeek());
        data.put("startTime", format(rule.getStartTime()));
        data.put("endTime", format(rule.getEndTime()));
        data.put("pricePerHour", rule.getPricePerHour());
        data.put("effectiveFrom", rule.getEffectiveFrom().toString());
        data.put("effectiveTo", rule.getEffectiveTo() == null ? null : rule.getEffectiveTo().toString());
        data.put("status", rule.getStatus().getValue());
        return data;
    }

    private static VenuePriceRulesResponse.PriceRuleItem toItem(CourtPriceRule rule) {
        return new VenuePriceRulesResponse.PriceRuleItem(
                rule.getId(), rule.getDayOfWeek(), rule.getStartTime(), rule.getEndTime(), rule.getPricePerHour(),
                rule.getEffectiveFrom(), rule.getEffectiveTo(), rule.getStatus().getValue());
    }
}
