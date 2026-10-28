package com.courtly.service.match;

import com.courtly.api.match.dto.PlayerPerformanceResponse;
import com.courtly.api.match.dto.PlayerRatingHistoryResponse;
import com.courtly.common.PageResponse;
import com.courtly.common.exception.ErrorCode;
import com.courtly.common.exception.ApiException;
import com.courtly.domain.account.UserRepository;
import com.courtly.domain.match.MatchRepository;
import com.courtly.domain.match.PlayerRatingHistory;
import com.courtly.domain.match.PlayerRatingHistoryRepository;
import com.courtly.domain.match.PlayerStatistics;
import com.courtly.domain.match.PlayerStatisticsRepository;
import com.courtly.domain.matching.PlayerMatchProfile;
import com.courtly.domain.matching.PlayerMatchProfileRepository;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Truy van read-model hieu suat, khong tinh lai du lieu khi mo man hinh. */
@Service
@RequiredArgsConstructor
public class PlayerPerformanceQueryService {

    private static final int MIN_TRAINING_MATCHES = 3;

    private final UserRepository userRepository;
    private final PlayerStatisticsRepository statisticsRepository;
    private final PlayerMatchProfileRepository profileRepository;
    private final PlayerRatingHistoryRepository ratingHistoryRepository;
    private final MatchRepository matchRepository;

    @Transactional(readOnly = true)
    public PlayerPerformanceResponse getCurrentPlayer(UUID userId) {
        requirePlayer(userId);
        PlayerStatistics statistics = statisticsRepository.findById(userId).orElse(null);
        PlayerMatchProfile profile = profileRepository.findById(userId).orElse(null);

        int totalMatches = statistics == null ? 0 : statistics.getTotalMatches();
        BigDecimal rating = statistics == null
                ? PlayerStatistics.DEFAULT_RATING : statistics.getRatingScore();
        BigDecimal rawWinRate = statistics == null ? BigDecimal.ZERO : statistics.getWinRate();
        BigDecimal smoothedWinRate = statistics == null
                ? BigDecimal.valueOf(50) : statistics.getSmoothedWinRate();
        boolean trainingEligible = profile != null
                ? profile.isTrainingEligible() : totalMatches >= MIN_TRAINING_MATCHES;

        return new PlayerPerformanceResponse(
                new PlayerPerformanceResponse.Summary(
                        rating,
                        ratingLabel(rating),
                        totalMatches < 5,
                        totalMatches,
                        statistics == null ? 0 : statistics.getTotalWins(),
                        statistics == null ? 0 : statistics.getTotalLosses(),
                        rawWinRate,
                        smoothedWinRate,
                        statistics == null ? null : statistics.getLastPlayedAt()),
                new PlayerPerformanceResponse.Features(
                        profile == null ? rating : profile.getSkillScore(),
                        profile == null ? smoothedWinRate : profile.getWinRate(),
                        profile == null ? totalMatches : profile.getTotalMatches(),
                        profile == null ? null : profile.getAveragePointDifference(),
                        profile == null ? null : profile.getPerformanceConsistency(),
                        profile == null ? null : profile.getDataRecencyDays()),
                new PlayerPerformanceResponse.DataQuality(
                        trainingEligible,
                        MIN_TRAINING_MATCHES,
                        matchRepository.countExcludedMatchesForUser(userId)));
    }

    @Transactional(readOnly = true)
    public PageResponse<PlayerRatingHistoryResponse> getRatingHistory(UUID userId, int page, int size) {
        requirePlayer(userId);
        Page<PlayerRatingHistory> history = ratingHistoryRepository
                .findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size));
        return PageResponse.from(history, history.getContent().stream()
                .map(PlayerPerformanceQueryService::toHistoryResponse)
                .toList());
    }

    private static PlayerRatingHistoryResponse toHistoryResponse(PlayerRatingHistory history) {
        return new PlayerRatingHistoryResponse(
                history.getId(),
                history.getMatch() == null ? null : history.getMatch().getId(),
                history.getOldRating(), history.getNewRating(), history.getChangeAmount(),
                history.getCreatedAt());
    }

    private void requirePlayer(UUID userId) {
        if (!userRepository.existsById(userId)) {
            throw ApiException.notFound("Khong tim thay tai khoan.");
        }
        if (!userRepository.hasPlayerRole(userId)) {
            throw ApiException.forbidden(ErrorCode.FORBIDDEN,
                    "Chi tai khoan nguoi choi moi xem duoc hieu suat thi dau.");
        }
    }

    private static String ratingLabel(BigDecimal rating) {
        if (rating.compareTo(BigDecimal.valueOf(900)) < 0) {
            return "Moi bat dau";
        }
        if (rating.compareTo(BigDecimal.valueOf(1100)) < 0) {
            return "Co ban";
        }
        if (rating.compareTo(BigDecimal.valueOf(1300)) < 0) {
            return "Kha";
        }
        if (rating.compareTo(BigDecimal.valueOf(1500)) < 0) {
            return "Tot";
        }
        return "Nang cao";
    }
}
