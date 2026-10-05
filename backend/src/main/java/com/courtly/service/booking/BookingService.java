package com.courtly.service.booking;

import com.courtly.api.booking.dto.BookingDetailResponse;
import com.courtly.api.booking.dto.BookingSummaryResponse;
import com.courtly.api.booking.dto.CancelBookingRequest;
import com.courtly.api.booking.dto.CreateBookingRequest;
import com.courtly.api.booking.dto.QuoteRequest;
import com.courtly.api.booking.dto.QuoteResponse;
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

    /** Thoi gian giu cho de thanh toan. Qua han thi job tu dong huy (2.1.42). */
    public static final int PAYMENT_WINDOW_MINUTES = 10;

    private static final DateTimeFormatter CODE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final int MAX_CODE_ATTEMPTS = 5;

    /** Cac tab o man hinh lich su dat san (2.1.32). */
    private static final Map<String, List<BookingStatus>> TABS = Map.of(
            "upcoming", List.of(BookingStatus.CONFIRMED),
            "pending", List.of(BookingStatus.PENDING_PAYMENT),
            "completed", List.of(BookingStatus.COMPLETED),
            "cancelled", List.of(BookingStatus.CANCELLED, BookingStatus.EXPIRED, BookingStatus.REFUNDED),
            "all", List.of(BookingStatus.values()));

    private final BookingRepository bookingRepository;
    private final CourtRepository courtRepository;
    private final UserRepository userRepository;
    private final VenueImageRepository venueImageRepository;
    private final QuoteService quoteService;
    private final PaymentService paymentService;

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
        booking.setStatus(BookingStatus.PENDING_PAYMENT);
        booking.setTotalAmount(quote.totalAmount());
        booking.setNote(request.note());
        booking.setCreatedAt(now);
        booking.setExpiresAt(now.plus(PAYMENT_WINDOW_MINUTES, ChronoUnit.MINUTES));

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

        addHistory(booking, null, BookingStatus.PENDING_PAYMENT, "Tao yeu cau dat san", user, now);

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

        // SePay la phuong thuc duy nhat hien tai (2.1.33). Giao dich phai sinh ra cung luc voi
        // don, neu khong se co don treo khong co duong nao tra tien.
        paymentService.createFor(booking);

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

    /** Chi tiet don (2.1.29, 2.1.30). Chi chu don moi xem duoc. */
    @Transactional(readOnly = true)
    public BookingDetailResponse getDetail(UUID userId, UUID bookingId) {
        return toDetail(requireOwnBooking(userId, bookingId), Instant.now());
    }

    /**
     * Huy dat san (2.1.31).
     *
     * <p>Chi huy duoc khi don dang pending_payment hoac confirmed, va chua toi gio choi.
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
        boolean statusAllows = booking.getStatus() == BookingStatus.PENDING_PAYMENT
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
                booking.getExpiresAt(),
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
                booking.getExpiresAt(),
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

    /**
     * Xac nhan don sau khi webhook SePay bao da nhan du tien (2.1.38, 2.1.40).
     *
     * <p>De o day chu khong o tang thanh toan: doi trang thai don va khung gio la nghiep vu cua
     * booking. Goi trong transaction cua webhook nen khong tu luu, de JPA flush cung luc.
     *
     * <p>Khong lam gi neu don khong con cho thanh toan - tien ve muon hon luc job huy don thi
     * phai hoan tay, khong duoc tu dong hoi sinh mot khung gio da nha cho nguoi khac.
     *
     * @return true neu don vua duoc chuyen sang confirmed
     */
    public boolean confirmPaid(Booking booking, Instant now) {
        if (booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
            return false;
        }
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setExpiresAt(null);
        booking.getItems().forEach(item -> item.setStatus(BookingItemStatus.CONFIRMED));
        addHistory(booking, BookingStatus.PENDING_PAYMENT, BookingStatus.CONFIRMED,
                "Da nhan thanh toan qua SePay", null, now);
        return true;
    }

    /** Dung cho job het han (2.1.42) va cac cho khac can doi trang thai he thong. */
    @Transactional
    public int expireOverdueBookings() {
        Instant now = Instant.now();
        List<Booking> overdue = bookingRepository.findExpiredPendingPayment(now);

        for (Booking booking : overdue) {
            booking.setStatus(BookingStatus.EXPIRED);
            booking.getItems().forEach(item -> item.setStatus(BookingItemStatus.EXPIRED));
            addHistory(booking, BookingStatus.PENDING_PAYMENT, BookingStatus.EXPIRED,
                    "Qua han thanh toan " + PAYMENT_WINDOW_MINUTES + " phut", null, now);
        }

        if (!overdue.isEmpty()) {
            bookingRepository.saveAll(overdue);
            log.info("Da huy {} don qua han thanh toan", overdue.size());
        }
        return overdue.size();
    }
}
