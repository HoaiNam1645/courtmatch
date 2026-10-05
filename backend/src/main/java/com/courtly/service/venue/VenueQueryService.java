package com.courtly.service.venue;

import com.courtly.api.venue.dto.DistrictResponse;
import com.courtly.api.venue.dto.VenueDetailResponse;
import com.courtly.api.venue.dto.VenueSearchQuery;
import com.courtly.api.venue.dto.VenueReviewResponse;
import com.courtly.api.venue.dto.VenueSummaryResponse;
import com.courtly.common.PageResponse;
import com.courtly.common.enums.ActiveStatus;
import com.courtly.common.enums.ApprovalStatus;
import com.courtly.common.enums.ReviewStatus;
import com.courtly.common.enums.VenueStatus;
import com.courtly.common.exception.ApiException;
import com.courtly.domain.review.VenueReview;
import com.courtly.domain.review.VenueReviewRepository;
import com.courtly.domain.venue.Court;
import com.courtly.domain.venue.CourtPriceRule;
import com.courtly.domain.venue.CourtRepository;
import com.courtly.domain.venue.Venue;
import com.courtly.domain.venue.VenueImageRepository;
import com.courtly.domain.venue.VenueOperatingHoursRepository;
import com.courtly.domain.venue.VenueRepository;
import com.courtly.domain.venue.VenueServiceRepository;
import com.courtly.domain.venue.VenueSummaryView;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Tim kiem va xem san: 2.1.13 - 2.1.22. */
@Service
@RequiredArgsConstructor
public class VenueQueryService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final VenueRepository venueRepository;
    private final CourtRepository courtRepository;
    private final VenueImageRepository venueImageRepository;
    private final VenueServiceRepository venueServiceRepository;
    private final VenueOperatingHoursRepository operatingHoursRepository;
    private final VenueReviewRepository venueReviewRepository;

    /**
     * Tim kiem san kem loc va sap xep (2.1.13, 2.1.14, 2.1.19 - 2.1.21).
     *
     * <p>Toan bo viec loc, sap xep va phan trang deu lam o database. Khong tai het
     * danh sach roi loc o tang ung dung.
     */
    @Transactional(readOnly = true)
    public PageResponse<VenueSummaryResponse> search(VenueSearchQuery query) {
        short today = (short) LocalDate.now(ZONE).getDayOfWeek().getValue();

        Page<VenueSummaryView> page = venueRepository.search(
                query.keywordPattern(),
                query.district(),
                query.minRating(),
                query.maxPricePerHour(),
                query.availableOnly(),
                query.lat(),
                query.lng(),
                query.radiusMetres(),
                today,
                // Khong dung Sort cua Pageable: thu tu da co trong cau truy van vi
                // sap theo khoang cach can bieu thuc PostGIS.
                PageRequest.of(query.pageNumber(), query.pageSize()));

        return PageResponse.from(page, page.getContent().stream()
                .map(VenueSummaryResponse::from)
                .toList());
    }

    /** Danh gia cua san, hien o trang chi tiet (2.1.23). */
    @Transactional(readOnly = true)
    public PageResponse<VenueReviewResponse> listReviews(String slugOrId, int page, int size) {
        Venue venue = findPublishedVenue(slugOrId)
                .orElseThrow(() -> ApiException.notFound("Khong tim thay san nay."));

        Page<VenueReview> reviews = venueReviewRepository.findVisibleByVenue(
                venue.getId(), ReviewStatus.VISIBLE, PageRequest.of(page, size));

        return PageResponse.from(reviews, reviews.getContent().stream()
                .map(review -> new VenueReviewResponse(
                        review.getId(),
                        review.getUser().getFullName(),
                        review.getUser().getAvatarUrl(),
                        review.getRating(),
                        review.getComment(),
                        review.getCreatedAt()))
                .toList());
    }

    /** Danh sach khu vuc cho modal bo loc (2.1.19). */
    @Transactional(readOnly = true)
    public List<DistrictResponse> listDistricts() {
        return venueRepository.findDistrictsWithVenueCount().stream()
                .map(view -> new DistrictResponse(view.getDistrict(), view.getVenueCount()))
                .toList();
    }

    /**
     * Chi tiet mot san (2.1.18). Nhan duoc ca slug lan id de duong dan than thien
     * hon ma van tuong thich voi lien ket cu dung id.
     */
    @Transactional(readOnly = true)
    public VenueDetailResponse getDetail(String slugOrId) {
        Venue venue = findPublishedVenue(slugOrId)
                .orElseThrow(() -> ApiException.notFound("Khong tim thay san nay."));

        // Doc tung nhom bang mot truy van co chu dich. Neu de Hibernate lazy-load thi
        // moi san con va moi dich vu lai sinh them mot cau truy van (N+1).
        List<Court> courts = courtRepository.findActiveWithPriceRules(venue.getId());

        List<BigDecimal> activePrices = courts.stream()
                .flatMap(court -> court.getPriceRules().stream())
                .filter(rule -> rule.getStatus() == ActiveStatus.ACTIVE)
                .map(CourtPriceRule::getPricePerHour)
                .toList();

        return new VenueDetailResponse(
                venue.getId(),
                venue.getSlug(),
                venue.getName(),
                venue.getDescription(),
                venue.getAddress(),
                venue.getWard(),
                venue.getDistrict(),
                venue.getProvince(),
                venue.getLatitude(),
                venue.getLongitude(),
                venue.getPhone(),
                venue.getEmail(),
                venue.getAverageRating(),
                venue.getReviewCount(),
                activePrices.stream().min(BigDecimal::compareTo).orElse(null),
                activePrices.stream().max(BigDecimal::compareTo).orElse(null),
                venueImageRepository.findAllByVenueIdOrderByDisplayOrderAsc(venue.getId()).stream()
                        .map(image -> new VenueDetailResponse.ImageResponse(
                                image.getId(), image.getImageUrl(), image.getCaption(),
                                image.getDisplayOrder(), image.isCover()))
                        .toList(),
                venueServiceRepository.findAllWithServiceByVenueId(venue.getId()).stream()
                        .map(item -> new VenueDetailResponse.ServiceResponse(
                                item.getService().getCode(),
                                item.getService().getName(),
                                item.getService().getDescription(),
                                item.getPrice(),
                                item.getNote()))
                        .toList(),
                operatingHoursRepository.findAllByVenueIdOrderByDayOfWeekAsc(venue.getId()).stream()
                        .map(hours -> new VenueDetailResponse.OperatingHoursResponse(
                                hours.getDayOfWeek(), hours.getOpenTime(),
                                hours.getCloseTime(), hours.isClosed()))
                        .toList(),
                courts.stream()
                        .map(VenueQueryService::toCourtResponse)
                        .toList());
    }

    private static VenueDetailResponse.CourtResponse toCourtResponse(Court court) {
        return new VenueDetailResponse.CourtResponse(
                court.getId(),
                court.getName(),
                court.getCourtCode(),
                court.getCourtType(),
                court.getSurfaceType(),
                court.isIndoor(),
                court.getPriceRules().stream()
                        .filter(rule -> rule.getStatus() == ActiveStatus.ACTIVE)
                        .sorted(Comparator.comparing(CourtPriceRule::getStartTime))
                        .map(rule -> new VenueDetailResponse.PriceRuleResponse(
                                rule.getDayOfWeek(), rule.getStartTime(),
                                rule.getEndTime(), rule.getPricePerHour()))
                        .toList());
    }

    /** Chi tra ve san da duyet va dang hoat dong; san nhap chua duyet khong lo ra ngoai. */
    private Optional<Venue> findPublishedVenue(String slugOrId) {
        Optional<Venue> found = venueRepository.findBySlug(slugOrId);
        if (found.isEmpty()) {
            found = parseUuid(slugOrId).flatMap(venueRepository::findById);
        }
        return found.filter(venue -> venue.getStatus() == VenueStatus.ACTIVE
                && venue.getApprovalStatus() == ApprovalStatus.APPROVED);
    }

    private static Optional<UUID> parseUuid(String value) {
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }
}
