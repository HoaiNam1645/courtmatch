package com.courtly.service.booking;

import com.courtly.api.booking.dto.BookingDetailResponse;
import com.courtly.api.booking.dto.BookingSummaryResponse;
import com.courtly.api.booking.dto.CancelBookingRequest;
import com.courtly.api.booking.dto.CreateBookingRequest;
import com.courtly.api.booking.dto.QuoteRequest;
import com.courtly.api.booking.dto.QuoteResponse;
import com.courtly.api.booking.dto.RejectBookingRequest;
import com.courtly.common.PageResponse;
import com.courtly.common.enums.BookingItemStatus;
import com.courtly.common.enums.BookingStatus;
import com.courtly.common.exception.ApiException;
import com.courtly.common.exception.ErrorCode;
import com.courtly.domain.account.User;
import com.courtly.domain.account.UserRepository;
import com.courtly.domain.booking.Booking;
import com.courtly.domain.booking.BookingItem;
import com.courtly.domain.booking.BookingRepository;
import com.courtly.domain.booking.BookingStatusHistory;
import com.courtly.domain.venue.Court;
import com.courtly.domain.venue.CourtRepository;
import com.courtly.domain.venue.VenueImageRepository;
import com.courtly.service.payment.PaymentService;
import com.courtly.service.finance.CommissionService;
import com.courtly.domain.venue.Venue;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Dat san: tao don (2.1.28), xem (2.1.29, 2.1.30), huy (2.1.31), lich su (2.1.32). */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookingService {

    public static final int OWNER_REVIEW_WINDOW_MINUTES = 30;

    private static final DateTimeFormatter CODE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final int MAX_CODE_ATTEMPTS = 5;

    /** Cac tab o man hinh lich su dat san (2.1.32). */
    private static final Map<String, List<BookingStatus>> TABS = Map.of(
            "upcoming", List.of(BookingStatus.CONFIRMED),
            "pending", List.of(BookingStatus.PENDING_OWNER_CONFIRMATION,
                    BookingStatus.AWAITING_PAYMENT),
            "completed", List.of(BookingStatus.COMPLETED),
            "cancelled", List.of(BookingStatus.REJECTED, BookingStatus.CANCELLED,
                    BookingStatus.EXPIRED, BookingStatus.REFUNDED),
            "all", List.of(BookingStatus.values()));

    private final BookingRepository bookingRepository;
    private final CourtRepository courtRepository;
    private final UserRepository userRepository;
    private final VenueImageRepository venueImageRepository;
    private final QuoteService quoteService;
    private final PaymentService paymentService;
    private final CommissionService commissionService;

    /**
     * Tao yeu cau dat san (2.1.28).
     *
     * <p>So tien duoc tinh lai o day tu court_price_rules, khong nhan tu client.
     * Lich trong cung duoc kiem tra lai; neu hai nguoi bam cung luc thi exclusion
     * constraint {@code booking_items_no_overlap} o database chan ban thua.
     */
    @Transactional
    public BookingDetailResponse create(UUID userId, CreateBookingRequest request) {
        QuoteResponse quote = quoteService.quote(new QuoteRequest(
                request.courtId(), request.startTime(), request.durationMinutes()));

        if (!quote.available()) {
            throw ApiException.conflict(ErrorCode.BOOKING_SLOT_TAKEN,
                    quote.unavailableReason(),
                    Map.of("startTime", quote.unavailableReason()));
        }
        if (quote.totalAmount() == null) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "Khung gio nay chua co bang gia.", Map.of());
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("Khong tim thay tai khoan."));
        Court court = courtRepository.findById(request.courtId()).orElseThrow();
        Venue venue = court.getVenue();
        Instant now = Instant.now();

        Booking booking = new Booking();
        booking.setId(UUID.randomUUID());
        booking.setBookingCode(nextBookingCode());
        booking.setUser(user);
        booking.setVenue(venue);
        booking.setStatus(BookingStatus.PENDING_OWNER_CONFIRMATION);
        booking.setTotalAmount(quote.totalAmount());
        booking.setNote(request.note());
        booking.setCreatedAt(now);
        booking.setPriceLockedAt(now);
        booking.setOwnerResponseExpiresAt(now.plus(OWNER_REVIEW_WINDOW_MINUTES, ChronoUnit.MINUTES));

        BookingItem item = new BookingItem();
        item.setId(UUID.randomUUID());
        item.setBooking(booking);
        item.setCourt(court);
        item.setStartTime(quote.startTime());
        item.setEndTime(quote.endTime());
        item.setPrice(quote.totalAmount());
        item.setStatus(BookingItemStatus.PENDING);
        item.setCreatedAt(now);
        booking.getItems().add(item);

        addHistory(booking, null, BookingStatus.PENDING_OWNER_CONFIRMATION,
                "Da gui yeu cau cho chu san", user, now);

        try {
            bookingRepository.saveAndFlush(booking);
        } catch (DataIntegrityViolationException e) {
            // Hai nguoi cung dat mot khung gio: lop nghiep vu o tren da kiem tra nhung
            // van co the cung vuot qua. Exclusion constraint la chot chan cuoi cung.
            log.info("Dat trung khung gio tren san {}", court.getId());
            throw ApiException.conflict(ErrorCode.BOOKING_SLOT_TAKEN,
                    "Khung gio nay vua co nguoi dat. Vui long chon gio khac.",
                    Map.of("startTime", "Khung gio vua bi giu"));
        }

        log.info("Tao don {} cho nguoi dung {}", booking.getBookingCode(), userId);
        return toDetail(booking, now);
    }

    /** Lich su dat san cua chinh nguoi dang dang nhap (2.1.32). */
    @Transactional(readOnly = true)
    public PageResponse<BookingSummaryResponse> list(UUID userId, String tab, String q, int page, int size) {
        List<BookingStatus> statuses = TABS.get(tab == null ? "all" : tab);
        if (statuses == null) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "Tab khong hop le.",
                    Map.of("tab", "Chi nhan upcoming, pending, completed, cancelled hoac all"));
        }

        // "%" nghia la khong loc. Khong truyen NULL - xem ghi chu o BookingRepository.
        String keyword = q == null || q.isBlank() ? "%" : "%" + q.trim() + "%";
        Page<Booking> bookings = bookingRepository.findForUser(
                userId, statuses, keyword, PageRequest.of(page, size));

        // Mot cau truy van cho tat ca anh bia, thay vi moi don mot cau.
        Map<UUID, String> covers = coversFor(bookings.getContent());

        return PageResponse.from(bookings, bookings.getContent().stream()
                .map(booking -> toSummary(booking, covers.get(booking.getVenue().getId())))
                .toList());
    }

    /** Booking queue for the venue owner to review and operate. */
    @Transactional(readOnly = true)
    public PageResponse<BookingSummaryResponse> listForOwner(UUID ownerId, String status, int page, int size) {
        List<BookingStatus> statuses;
        if (status == null || status.isBlank() || "all".equals(status)) {
            statuses = List.of(BookingStatus.values());
        } else {
            try {
                statuses = List.of(BookingStatus.from(status));
            } catch (IllegalArgumentException exception) {
                throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED,
                        "Trang thai booking khong hop le.", Map.of("status", exception.getMessage()));
            }
        }
        Page<Booking> bookings = bookingRepository.findForOwner(
                ownerId, statuses, PageRequest.of(page, size));
        Map<UUID, String> covers = coversFor(bookings.getContent());
        return PageResponse.from(bookings, bookings.getContent().stream()
                .map(booking -> toSummary(booking, covers.get(booking.getVenue().getId())))
                .toList());
    }

    /** Chi tiet don (2.1.29, 2.1.30). Chi chu don moi xem duoc. */
    @Transactional(readOnly = true)
    public BookingDetailResponse getDetail(UUID userId, UUID bookingId) {
        return toDetail(requireOwnBooking(userId, bookingId), Instant.now());
    }

    /**
     * Huy dat san (2.1.31).
     *
     * <p>Chi huy duoc khi don dang cho owner, cho thanh toan hoac confirmed, va chua toi gio choi.
     */
    @Transactional
    public BookingDetailResponse cancel(UUID userId, UUID bookingId, CancelBookingRequest request) {
        Booking booking = requireOwnBooking(userId, bookingId);
        Instant now = Instant.now();

        if (!isCancellable(booking, now)) {
            throw ApiException.conflict(ErrorCode.BOOKING_NOT_CANCELLABLE,
                    booking.getStatus() == BookingStatus.CANCELLED
                            ? "Don nay da duoc huy truoc do."
                            : "Don nay khong con huy duoc.",
                    Map.of());
        }

        BookingStatus previous = booking.getStatus();
        booking.setStatus(BookingStatus.CANCELLED);
        booking.setCancelledAt(now);
        booking.setCancellationReason(request.reason());
        // Doi trang thai tung khung gio de giai phong cho cho nguoi khac dat.
        booking.getItems().forEach(item -> item.setStatus(BookingItemStatus.CANCELLED));
        addHistory(booking, previous, BookingStatus.CANCELLED, request.reason(), booking.getUser(), now);

        bookingRepository.save(booking);
        log.info("Huy don {}", booking.getBookingCode());
        return toDetail(booking, now);
    }

    /** Chi don chua toi gio choi va dang o trang thai con huy duoc. */
    private boolean isCancellable(Booking booking, Instant now) {
        boolean statusAllows = booking.getStatus() == BookingStatus.PENDING_OWNER_CONFIRMATION
                || booking.getStatus() == BookingStatus.AWAITING_PAYMENT
                || booking.getStatus() == BookingStatus.CONFIRMED;
        Instant firstStart = booking.getItems().stream()
                .map(BookingItem::getStartTime)
                .min(Comparator.naturalOrder())
                .orElse(null);
        return statusAllows && firstStart != null && firstStart.isAfter(now);
    }

    private Booking requireOwnBooking(UUID userId, UUID bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> ApiException.notFound("Khong tim thay don dat san."));
        if (!booking.getUser().getId().equals(userId)) {
            // Khong tiet lo don co ton tai hay khong.
            throw ApiException.notFound("Khong tim thay don dat san.");
        }
        return booking;
    }

    /**
     * Ma don dang CT-yyyyMMdd-NNNN.
     *
     * <p>Hai request cung luc co the ra cung so, khi do unique index se chan va vong lap
     * thu lai. Dung lai sau vai lan de khong quay vo han.
     */
    private String nextBookingCode() {
        LocalDate today = LocalDate.now(AvailabilityService.ZONE);
        Instant dayStart = today.atStartOfDay(AvailabilityService.ZONE).toInstant();
        Instant dayEnd = today.plusDays(1).atStartOfDay(AvailabilityService.ZONE).toInstant();
        long countToday = bookingRepository.countCreatedBetween(dayStart, dayEnd);

        for (int attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt++) {
            String code = "CT-%s-%04d".formatted(today.format(CODE_DATE), countToday + 1 + attempt);
            if (!bookingRepository.existsByBookingCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Khong sinh duoc ma don sau " + MAX_CODE_ATTEMPTS + " lan thu.");
    }

    private void addHistory(Booking booking, BookingStatus from, BookingStatus to,
                            String reason, User changedBy, Instant at) {
        BookingStatusHistory history = new BookingStatusHistory();
        history.setId(UUID.randomUUID());
        history.setBooking(booking);
        history.setOldStatus(from);
        history.setNewStatus(to);
        history.setReason(reason);
        history.setChangedBy(changedBy);
        history.setCreatedAt(at);
        booking.getStatusHistory().add(history);
    }

    private static BookingSummaryResponse toSummary(Booking booking, String coverImageUrl) {
        BookingItem first = booking.getItems().stream()
                .min(Comparator.comparing(BookingItem::getStartTime))
                .orElse(null);

        return new BookingSummaryResponse(
                booking.getId(),
                booking.getBookingCode(),
                booking.getStatus(),
                booking.getTotalAmount(),
                booking.getVenue().getId(),
                booking.getVenue().getSlug(),
                booking.getVenue().getName(),
                booking.getVenue().getAddress(),
                coverImageUrl,
                first == null ? null : first.getCourt().getName(),
                first == null ? null : first.getStartTime(),
                first == null ? null : first.getEndTime(),
                activeDeadline(booking),
                booking.getCreatedAt());
    }

    private Map<UUID, String> coversFor(List<Booking> bookings) {
        Set<UUID> venueIds = bookings.stream()
                .map(booking -> booking.getVenue().getId())
                .collect(Collectors.toSet());
        if (venueIds.isEmpty()) {
            return Map.of();
        }
        return venueImageRepository.findCoversByVenueIds(venueIds).stream()
                .collect(Collectors.toMap(
                        VenueImageRepository.VenueCoverView::getVenueId,
                        VenueImageRepository.VenueCoverView::getImageUrl));
    }

    private BookingDetailResponse toDetail(Booking booking, Instant now) {
        Venue venue = booking.getVenue();
        String coverImageUrl = coversFor(List.of(booking)).get(venue.getId());

        return new BookingDetailResponse(
                booking.getId(),
                booking.getBookingCode(),
                booking.getStatus(),
                booking.getTotalAmount(),
                booking.getNote(),
                activeDeadline(booking),
                booking.getConfirmedAt(),
                booking.getCancelledAt(),
                booking.getCancellationReason(),
                booking.getCreatedAt(),
                isCancellable(booking, now),
                new BookingDetailResponse.VenueInfo(
                        venue.getId(), venue.getSlug(), venue.getName(),
                        venue.getAddress(), venue.getPhone(), coverImageUrl,
                        venue.getLatitude(), venue.getLongitude()),
                booking.getItems().stream()
                        .sorted(Comparator.comparing(BookingItem::getStartTime))
                        .map(item -> new BookingDetailResponse.Item(
                                item.getId(), item.getCourt().getId(), item.getCourt().getName(),
                                item.getStartTime(), item.getEndTime(), item.getPrice(), item.getStatus()))
                        .toList(),
                booking.getStatusHistory().stream()
                        .sorted(Comparator.comparing(BookingStatusHistory::getCreatedAt))
                        .map(history -> new BookingDetailResponse.StatusChange(
                                history.getOldStatus(), history.getNewStatus(), history.getReason(),
                                history.getChangedBy() == null, history.getCreatedAt()))
                        .toList());
    }

    /** Owner accepts the request and opens the direct-payment step. */
    @Transactional
    public BookingDetailResponse accept(UUID ownerId, UUID bookingId) {
        Booking booking = requireOwnerBooking(ownerId, bookingId);
        if (booking.getStatus() != BookingStatus.PENDING_OWNER_CONFIRMATION) {
            throw ApiException.conflict(ErrorCode.BOOKING_REVIEW_INVALID,
                    "Booking khong con cho chu san duyet.", Map.of());
        }

        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> ApiException.notFound("Khong tim thay tai khoan chu san."));
        Instant now = Instant.now();
        if (booking.getOwnerResponseExpiresAt() != null
                && !booking.getOwnerResponseExpiresAt().isAfter(now)) {
            throw ApiException.conflict(ErrorCode.BOOKING_REVIEW_INVALID,
                    "Yeu cau da qua han phan hoi.", Map.of());
        }
        booking.setStatus(BookingStatus.AWAITING_PAYMENT);
        booking.setOwnerRespondedAt(now);
        booking.setOwnerRespondedBy(owner);
        booking.setOwnerResponseExpiresAt(null);
        booking.setPaymentExpiresAt(now.plus(PaymentService.PAYMENT_WINDOW_MINUTES, ChronoUnit.MINUTES));
        addHistory(booking, BookingStatus.PENDING_OWNER_CONFIRMATION, BookingStatus.AWAITING_PAYMENT,
                "Chu san da chap nhan, cho nguoi choi thanh toan", owner, now);
        paymentService.createFor(booking, now);
        return toDetail(booking, now);
    }

    /** Owner rejects the request and immediately releases the court slot. */
    @Transactional
    public BookingDetailResponse reject(UUID ownerId, UUID bookingId, RejectBookingRequest request) {
        Booking booking = requireOwnerBooking(ownerId, bookingId);
        if (booking.getStatus() != BookingStatus.PENDING_OWNER_CONFIRMATION) {
            throw ApiException.conflict(ErrorCode.BOOKING_REVIEW_INVALID,
                    "Booking khong con cho chu san duyet.", Map.of());
        }

        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> ApiException.notFound("Khong tim thay tai khoan chu san."));
        Instant now = Instant.now();
        booking.setStatus(BookingStatus.REJECTED);
        booking.setOwnerRespondedAt(now);
        booking.setOwnerRespondedBy(owner);
        booking.setOwnerResponseExpiresAt(null);
        booking.setRejectedAt(now);
        booking.setRejectionReason(request.reason().trim());
        booking.getItems().forEach(item -> item.setStatus(BookingItemStatus.CANCELLED));
        addHistory(booking, BookingStatus.PENDING_OWNER_CONFIRMATION, BookingStatus.REJECTED,
                request.reason().trim(), owner, now);
        return toDetail(booking, now);
    }

    /** Dung cho job het han (2.1.42) va cac cho khac can doi trang thai he thong. */
    @Transactional
    public int expireOverdueBookings() {
        Instant now = Instant.now();
        List<Booking> overdue = bookingRepository.findExpiredPending(now);

        for (Booking booking : overdue) {
            BookingStatus previous = booking.getStatus();
            booking.setStatus(BookingStatus.EXPIRED);
            booking.getItems().forEach(item -> item.setStatus(BookingItemStatus.EXPIRED));
            String reason = previous == BookingStatus.PENDING_OWNER_CONFIRMATION
                    ? "Chu san khong phan hoi dung han"
                    : "Qua han thanh toan";
            addHistory(booking, previous, BookingStatus.EXPIRED, reason, null, now);
        }

        if (!overdue.isEmpty()) {
            bookingRepository.saveAll(overdue);
            log.info("Da huy {} don qua han thanh toan", overdue.size());
        }
        return overdue.size();
    }

    /** Marks played bookings completed and creates exactly one commission snapshot. */
    @Transactional
    public int completeFinishedBookings() {
        Instant now = Instant.now();
        List<Booking> finished = bookingRepository.findConfirmedFinished(now);
        for (Booking booking : finished) {
            booking.setStatus(BookingStatus.COMPLETED);
            booking.setCompletedAt(now);
            booking.getItems().forEach(item -> item.setStatus(BookingItemStatus.COMPLETED));
            addHistory(booking, BookingStatus.CONFIRMED, BookingStatus.COMPLETED,
                    "Khung gio choi da ket thuc", null, now);
            commissionService.createForCompletedBooking(booking, now);
        }
        return finished.size();
    }

    private Booking requireOwnerBooking(UUID ownerId, UUID bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> ApiException.notFound("Khong tim thay don dat san."));
        if (!booking.getVenue().getOwner().getId().equals(ownerId)) {
            throw ApiException.notFound("Khong tim thay don dat san.");
        }
        return booking;
    }

    private static Instant activeDeadline(Booking booking) {
        return booking.getStatus() == BookingStatus.PENDING_OWNER_CONFIRMATION
                ? booking.getOwnerResponseExpiresAt()
                : booking.getPaymentExpiresAt();
    }
}
