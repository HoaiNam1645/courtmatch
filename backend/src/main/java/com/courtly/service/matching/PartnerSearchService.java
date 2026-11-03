package com.courtly.service.matching;

import com.courtly.api.partner.dto.PartnerSearchQuery;
import com.courtly.api.partner.dto.PartnerSearchResponse;
import com.courtly.common.PageResponse;
import com.courtly.common.enums.PlayingStyle;
import com.courtly.common.enums.UserStatus;
import com.courtly.common.exception.ApiException;
import com.courtly.common.exception.ErrorCode;
import com.courtly.domain.account.PlayerAvailabilitySlot;
import com.courtly.domain.account.PlayerAvailabilitySlotRepository;
import com.courtly.domain.account.PlayerPreferredLocation;
import com.courtly.domain.account.PlayerPreferredLocationRepository;
import com.courtly.domain.account.User;
import com.courtly.domain.account.UserRepository;
import com.courtly.domain.match.PlayerStatistics;
import com.courtly.domain.match.PlayerStatisticsRepository;
import com.courtly.domain.matching.PartnerSearchRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Tim doi tac va tinh cac dac trung tuong thich (2.2.20 - 2.2.27). */
@Service
@RequiredArgsConstructor
public class PartnerSearchService {

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final int PROVISIONAL_MATCH_LIMIT = 5;

    private final UserRepository userRepository;
    private final PlayerPreferredLocationRepository preferredLocationRepository;
    private final PlayerAvailabilitySlotRepository availabilitySlotRepository;
    private final PlayerStatisticsRepository statisticsRepository;
    private final PartnerSearchRepository partnerSearchRepository;

    @Transactional(readOnly = true)
    public PageResponse<PartnerSearchResponse> search(UUID requesterId, PartnerSearchQuery query) {
        User requester = requireActivePlayer(requesterId);
        PlayerPreferredLocation location = preferredLocationRepository
                .findByIdAndUserId(query.preferredLocationId(), requesterId)
                .orElseThrow(() -> ApiException.notFound("Khong tim thay khu vuc choi da chon."));
        if (location.getLocation() == null) {
            throw ApiException.badRequest(ErrorCode.PARTNER_LOCATION_REQUIRED,
                    "Khu vuc choi chua duoc ghim toa do.",
                    Map.of("preferredLocationId", "Hay ghim vi tri tren ban do truoc khi tim doi tac"));
        }
        if (query.durationMinutes() % 30 != 0) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "Thoi luong choi khong hop le.",
                    Map.of("durationMinutes", "Thoi luong phai theo buoc 30 phut"));
        }

        LocalDateTime start = LocalDateTime.ofInstant(query.scheduledAt(), VIETNAM_ZONE);
        LocalDateTime end = start.plusMinutes(query.durationMinutes());
        if (!start.toLocalDate().equals(end.toLocalDate())) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "Khung gio tim kiem phai nam trong cung mot ngay.",
                    Map.of("scheduledAt", "Khung gio khong duoc keo dai sang ngay ke tiep"));
        }

        BigDecimal requesterRating = statisticsRepository.findById(requesterId)
                .map(PlayerStatistics::getRatingScore)
                .orElse(PlayerStatistics.DEFAULT_RATING);
        Page<PartnerSearchRepository.PartnerCandidateRow> candidates =
                partnerSearchRepository.searchCandidates(
                        requesterId,
                        query.preferredLocationId(),
                        requesterRating,
                        BigDecimal.valueOf(query.ratingDifference()),
                        (short) start.getDayOfWeek().getValue(),
                        start.toLocalTime(),
                        end.toLocalTime(),
                        PageRequest.of(query.pageNumber(), query.pageSize()));

        Set<UUID> scheduleUserIds = candidates.getContent().stream()
                .map(PartnerSearchRepository.PartnerCandidateRow::getUserId)
                .collect(Collectors.toCollection(HashSet::new));
        scheduleUserIds.add(requesterId);
        Map<UUID, List<PlayerAvailabilitySlot>> availabilityByUser = availabilitySlotRepository
                .findAllByUserIds(scheduleUserIds).stream()
                .collect(Collectors.groupingBy(slot -> slot.getUser().getId()));
        PlayingStyle requesterStyle = requester.getPlayerProfile() == null
                ? null : requester.getPlayerProfile().getPlayingStyle();
        List<PlayerAvailabilitySlot> requesterAvailability = availabilityByUser
                .getOrDefault(requesterId, List.of());

        List<PartnerSearchResponse> content = candidates.getContent().stream()
                .map(candidate -> toResponse(
                        candidate,
                        requesterRating,
                        location.getRadiusKm(),
                        requesterStyle,
                        requesterAvailability,
                        availabilityByUser.getOrDefault(candidate.getUserId(), List.of()),
                        (short) start.getDayOfWeek().getValue(),
                        start.toLocalTime(),
                        end.toLocalTime()))
                .toList();
        return PageResponse.from(candidates, content);
    }

    private User requireActivePlayer(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("Khong tim thay tai khoan."));
        if (!userRepository.hasPlayerRole(userId)) {
            throw ApiException.forbidden(ErrorCode.FORBIDDEN,
                    "Chi tai khoan nguoi choi moi co the tim doi tac.");
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw ApiException.forbidden(ErrorCode.ACCOUNT_DISABLED,
                    "Tai khoan hien khong the su dung tinh nang tim doi tac.");
        }
        return user;
    }

    private static PartnerSearchResponse toResponse(
            PartnerSearchRepository.PartnerCandidateRow candidate,
            BigDecimal requesterRating,
            BigDecimal requesterRadiusKm,
            PlayingStyle requesterStyle,
            List<PlayerAvailabilitySlot> requesterAvailability,
            List<PlayerAvailabilitySlot> candidateAvailability,
            short requestedDay,
            LocalTime requestedStart,
            LocalTime requestedEnd) {
        BigDecimal rating = candidate.getRatingScore();
        BigDecimal gap = rating.subtract(requesterRating).abs().setScale(2, RoundingMode.HALF_UP);
        PlayingStyle candidateStyle = candidate.getPlayingStyle() == null
                ? null : PlayingStyle.from(candidate.getPlayingStyle());
        return new PartnerSearchResponse(
                candidate.getUserId(),
                candidate.getFullName(),
                candidate.getAvatarUrl(),
                candidate.getPlayingStyle(),
                candidate.getPreferredPlayType(),
                rating.setScale(2, RoundingMode.HALF_UP),
                ratingLabel(rating),
                candidate.getTotalMatches(),
                candidate.getTotalMatches() < PROVISIONAL_MATCH_LIMIT,
                gap,
                skillSimilarity(requesterRating, rating),
                candidate.getDistanceKm().setScale(2, RoundingMode.HALF_UP),
                locationCompatibility(candidate.getDistanceKm(), requesterRadiusKm),
                timeCompatibility(requesterAvailability, candidateAvailability,
                        requestedDay, requestedStart, requestedEnd),
                styleCompatibility(requesterStyle, candidateStyle),
                candidate.getAvailableFrom(),
                candidate.getAvailableUntil());
    }

    /** 100 khi hai Elo bang nhau, giam dan theo xac suat ky vong cua cong thuc Elo. */
    static BigDecimal skillSimilarity(BigDecimal requesterRating, BigDecimal candidateRating) {
        double expectedWin = 1.0 / (1.0 + Math.pow(10.0,
                candidateRating.subtract(requesterRating).doubleValue() / 400.0));
        double similarity = 100.0 * (1.0 - Math.abs(2.0 * expectedWin - 1.0));
        return BigDecimal.valueOf(similarity).setScale(2, RoundingMode.HALF_UP);
    }

    /** 100 tai cung vi tri, giam tuyen tinh ve 0 tai ranh gioi ban kinh cua nguoi tim. */
    static BigDecimal locationCompatibility(BigDecimal distanceKm, BigDecimal requesterRadiusKm) {
        if (requesterRadiusKm == null || requesterRadiusKm.signum() <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal score = BigDecimal.valueOf(100).multiply(
                BigDecimal.ONE.subtract(distanceKm.divide(requesterRadiusKm, 8, RoundingMode.HALF_UP)));
        return clampPercentage(score);
    }

    /** Ty le lich ranh cua nguoi tim duoc ung vien bao phu trong mot tuan. */
    static BigDecimal timeCompatibility(
            List<PlayerAvailabilitySlot> requesterSlots,
            List<PlayerAvailabilitySlot> candidateSlots,
            short requestedDay,
            LocalTime requestedStart,
            LocalTime requestedEnd) {
        Map<Short, List<MinuteRange>> requesterSchedule = weeklySchedule(requesterSlots);
        requesterSchedule.computeIfAbsent(requestedDay, ignored -> new ArrayList<>())
                .add(new MinuteRange(toMinute(requestedStart), toMinute(requestedEnd)));
        mergeSchedule(requesterSchedule);

        Map<Short, List<MinuteRange>> candidateSchedule = weeklySchedule(candidateSlots);
        long requesterMinutes = requesterSchedule.values().stream()
                .flatMap(List::stream)
                .mapToLong(MinuteRange::duration)
                .sum();
        if (requesterMinutes == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        long overlapMinutes = 0;
        for (Map.Entry<Short, List<MinuteRange>> entry : requesterSchedule.entrySet()) {
            overlapMinutes += overlapMinutes(entry.getValue(),
                    candidateSchedule.getOrDefault(entry.getKey(), List.of()));
        }
        return clampPercentage(BigDecimal.valueOf(overlapMinutes)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(requesterMinutes), 8, RoundingMode.HALF_UP));
    }

    /** Cung phong cach duoc uu tien; balanced van hop voi hai phong cach con lai. */
    static BigDecimal styleCompatibility(PlayingStyle requesterStyle, PlayingStyle candidateStyle) {
        if (requesterStyle == null || candidateStyle == null) {
            return new BigDecimal("50.00");
        }
        if (requesterStyle == candidateStyle) {
            return new BigDecimal("100.00");
        }
        if (requesterStyle == PlayingStyle.BALANCED || candidateStyle == PlayingStyle.BALANCED) {
            return new BigDecimal("80.00");
        }
        return new BigDecimal("65.00");
    }

    private static Map<Short, List<MinuteRange>> weeklySchedule(List<PlayerAvailabilitySlot> slots) {
        Map<Short, List<MinuteRange>> schedule = new HashMap<>();
        for (PlayerAvailabilitySlot slot : slots) {
            schedule.computeIfAbsent(slot.getDayOfWeek(), ignored -> new ArrayList<>())
                    .add(new MinuteRange(toMinute(slot.getStartTime()), toMinute(slot.getEndTime())));
        }
        mergeSchedule(schedule);
        return schedule;
    }

    private static void mergeSchedule(Map<Short, List<MinuteRange>> schedule) {
        schedule.replaceAll((day, ranges) -> mergeRanges(ranges));
    }

    private static List<MinuteRange> mergeRanges(List<MinuteRange> ranges) {
        if (ranges.isEmpty()) return List.of();
        List<MinuteRange> sorted = ranges.stream()
                .sorted(Comparator.comparingInt(MinuteRange::start))
                .toList();
        List<MinuteRange> merged = new ArrayList<>();
        MinuteRange current = sorted.getFirst();
        for (int index = 1; index < sorted.size(); index++) {
            MinuteRange next = sorted.get(index);
            if (next.start() <= current.end()) {
                current = new MinuteRange(current.start(), Math.max(current.end(), next.end()));
            } else {
                merged.add(current);
                current = next;
            }
        }
        merged.add(current);
        return merged;
    }

    private static long overlapMinutes(List<MinuteRange> left, List<MinuteRange> right) {
        long overlap = 0;
        int leftIndex = 0;
        int rightIndex = 0;
        while (leftIndex < left.size() && rightIndex < right.size()) {
            MinuteRange first = left.get(leftIndex);
            MinuteRange second = right.get(rightIndex);
            overlap += Math.max(0, Math.min(first.end(), second.end())
                    - Math.max(first.start(), second.start()));
            if (first.end() <= second.end()) leftIndex++;
            else rightIndex++;
        }
        return overlap;
    }

    private static int toMinute(LocalTime time) {
        return time.toSecondOfDay() / 60;
    }

    private static BigDecimal clampPercentage(BigDecimal value) {
        return value.max(BigDecimal.ZERO).min(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private record MinuteRange(int start, int end) {
        long duration() {
            return end - start;
        }
    }

    private static String ratingLabel(BigDecimal rating) {
        if (rating.compareTo(BigDecimal.valueOf(900)) < 0) return "Moi bat dau";
        if (rating.compareTo(BigDecimal.valueOf(1100)) < 0) return "Co ban";
        if (rating.compareTo(BigDecimal.valueOf(1300)) < 0) return "Kha";
        if (rating.compareTo(BigDecimal.valueOf(1500)) < 0) return "Tot";
        return "Nang cao";
    }
}
