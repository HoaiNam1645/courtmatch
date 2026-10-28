package com.courtly.service.match;

import com.courtly.api.match.dto.MatchSuggestionResponse;
import com.courtly.common.PageResponse;
import com.courtly.domain.booking.Booking;
import com.courtly.domain.booking.BookingItem;
import com.courtly.domain.booking.BookingRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Booking hoan tat chi tao goi y ghi ket qua, khong tu sinh tran rong (2.2.1). */
@Service
@RequiredArgsConstructor
public class MatchSuggestionService {

    private static final int SUGGESTION_DAYS = 30;

    private final BookingRepository bookingRepository;

    @Transactional(readOnly = true)
    public PageResponse<MatchSuggestionResponse> suggestions(UUID userId, int page, int size) {
        Page<Booking> bookings = bookingRepository.findMatchSuggestions(userId,
                Instant.now().minus(SUGGESTION_DAYS, ChronoUnit.DAYS), PageRequest.of(page, size));
        List<MatchSuggestionResponse> content = bookings.getContent().stream()
                .map(MatchSuggestionService::toSuggestion)
                .toList();
        return PageResponse.from(bookings, content);
    }

    private static MatchSuggestionResponse toSuggestion(Booking booking) {
        BookingItem first = booking.getItems().stream()
                .min(Comparator.comparing(BookingItem::getStartTime))
                .orElse(null);
        return new MatchSuggestionResponse(booking.getId(), booking.getBookingCode(),
                booking.getVenue().getId(), booking.getVenue().getName(),
                first == null ? null : first.getCourt().getId(),
                first == null ? null : first.getCourt().getName(),
                first == null ? booking.getCompletedAt() : first.getStartTime(),
                first == null ? 0 : Math.toIntExact(Duration.between(
                        first.getStartTime(), first.getEndTime()).toMinutes()));
    }
}
