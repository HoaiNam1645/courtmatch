package com.courtly.api.venue;

import com.courtly.api.venue.dto.DistrictResponse;
import com.courtly.api.venue.dto.VenueDetailResponse;
import com.courtly.api.venue.dto.VenueSearchQuery;
import com.courtly.api.booking.dto.AvailabilityResponse;
import com.courtly.api.venue.dto.VenueReviewResponse;
import com.courtly.api.venue.dto.VenueSummaryResponse;
import com.courtly.common.PageResponse;
import com.courtly.service.booking.AvailabilityService;
import com.courtly.service.venue.VenueQueryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;

/**
 * Duyet san: tim kiem (2.1.13), quanh vi tri (2.1.14), danh sach tren ban do
 * (2.1.15 - 2.1.17) va chi tiet san (2.1.18).
 *
 * <p>Cac endpoint nay cong khai - nguoi dung xem san duoc truoc khi dang nhap.
 */
@Validated
@RestController
@RequestMapping("/api/v1/venues")
@RequiredArgsConstructor
public class VenueController {

    private final VenueQueryService venueQueryService;
    private final AvailabilityService availabilityService;

    /**
     * Tim kiem san. Khong truyen tham so nao thi tra ve danh sach san dang hoat dong,
     * sap theo diem danh gia.
     *
     * <p>Truyen kem {@code lat}/{@code lng} thi ket qua duoc loc trong ban kinh va
     * sap theo khoang cach tang dan, moi the san co them {@code distanceKm}.
     */
    @GetMapping
    public ResponseEntity<PageResponse<VenueSummaryResponse>> search(@Valid VenueSearchQuery query) {
        return ResponseEntity.ok(venueQueryService.search(query));
    }

    /** Khu vuc co san, dung dung danh sach cho modal bo loc (2.1.19). */
    @GetMapping("/districts")
    public ResponseEntity<List<DistrictResponse>> districts() {
        return ResponseEntity.ok(venueQueryService.listDistricts());
    }

    /** Chi tiet san (2.1.18). Nhan slug hoac id. */
    @GetMapping("/{slugOrId}")
    public ResponseEntity<VenueDetailResponse> detail(@PathVariable String slugOrId) {
        return ResponseEntity.ok(venueQueryService.getDetail(slugOrId));
    }

    /** Danh gia cua san, hien o trang chi tiet (2.1.23). */
    @GetMapping("/{slugOrId}/reviews")
    public ResponseEntity<PageResponse<VenueReviewResponse>> reviews(
            @PathVariable String slugOrId,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "So trang bat dau tu 0") int page,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "Kich thuoc trang tu 1 den 50")
            @Max(value = 50, message = "Kich thuoc trang tu 1 den 50") int size) {
        return ResponseEntity.ok(venueQueryService.listReviews(slugOrId, page, size));
    }

    /**
     * Lich trong cua san trong mot ngay (2.1.26).
     *
     * <p>Tra ve tat ca san con trong mot lan goi de nguoi dung doi san con khong phai
     * cho tai lai.
     */
    @GetMapping("/{venueId}/availability")
    public ResponseEntity<AvailabilityResponse> availability(
            @PathVariable UUID venueId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "60")
            @Min(value = 30, message = "Thoi luong toi thieu 30 phut")
            @Max(value = 300, message = "Thoi luong toi da 300 phut") int durationMinutes) {
        return ResponseEntity.ok(availabilityService.getAvailability(venueId, date, durationMinutes));
    }
}
