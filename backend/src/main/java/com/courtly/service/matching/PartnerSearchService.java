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
import com.courtly.domain.matching.AiModel;
import com.courtly.domain.matching.AiModelRepository;
import com.courtly.domain.matching.PartnerSearchRepository;
import com.courtly.domain.matching.PlayerMatchProfile;
import com.courtly.domain.matching.PlayerMatchProfileRepository;
import com.courtly.domain.matching.PartnerSuggestion;
import com.courtly.domain.matching.PartnerSuggestionRepository;
import com.courtly.common.enums.SuggestionSource;
import java.util.LinkedHashMap;
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
    private final PartnerSuggestionRepository suggestionRepository;
    private final MatchingRunRecorder runRecorder;
    private final PlayerMatchProfileRepository matchProfileRepository;
    private final PartnerRankingService rankingService;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    private final AiModelRepository modelRepository;

    /** Ket qua mot lan tim: trang can tra, nguon diem va ly do rot ve baseline (neu co). */
    private record Ranked(List<PartnerSearchResponse> list, String source, AiModel model, String fallbackReason, UUID listId,
                          Map<UUID, List<String>> reasonCodes) {
    }

    private static final int POOL_LIMIT = 200;
    private static final BigDecimal PROVISIONAL_CAP = BigDecimal.valueOf(70);
    private static final long LIST_TTL_MINUTES = 10;

    /** Phan luu xuong DB cua mot danh sach da xep hang (model duoc luu qua model_id). */
    private record StoredList(List<PartnerSearchResponse> items, Map<UUID, List<String>> codes) {
    }

    /** Tim doi tac va ghi nhat ky lan tim (2.3.70 - 2.3.76); loi he thong cung duoc ghi lai. */
    @Transactional
    public PageResponse<PartnerSearchResponse> search(UUID requesterId, PartnerSearchQuery query) {
        long startedAt = System.nanoTime();
        try {
            Ranked ranked = rankedList(requesterId, query);
            PageResponse<PartnerSearchResponse> page = pageOf(ranked.list(), query);
            recordSearch(requesterId, query, page, ranked, (System.nanoTime() - startedAt) / 1_000_000);
            return page;
        } catch (ApiException e) {
            throw e;
        } catch (RuntimeException e) {
            runRecorder.recordFailure(MatchingRunRecorder.PARTNER_SEARCH, requesterId,
                    (System.nanoTime() - startedAt) / 1_000_000, e, searchParams(query));
            throw e;
        }
    }

    /**
     * Danh sach da xep hang: trang sau cua cung mot lan tim doc lai tu DB (partner_search_lists, het han sau 10 phut, chi nguoi tim), nguoc lai
     * loc + cham diem moi. Luu DB nen khong mat khi backend khoi dong lai va dung chung giua nhieu may.
     */
    private Ranked rankedList(UUID requesterId, PartnerSearchQuery query) {
        if (query.listId() != null) {
            Ranked stored = loadList(requesterId, query.listId());
            if (stored != null) {
                return stored;
            }
        }
        Ranked ranked = rank(requesterId, query);
        saveList(requesterId, ranked);
        return ranked;
    }

    private Ranked loadList(UUID requesterId, UUID listId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT model_id, scoring_source, fallback_reason, payload_json::text AS payload FROM partner_search_lists WHERE id = ? AND requester_id = ? AND expires_at > now()",
                listId, requesterId);
        if (rows.isEmpty()) {
            return null;
        }
        Map<String, Object> row = rows.getFirst();
        try {
            StoredList stored = objectMapper.readValue((String) row.get("payload"), StoredList.class);
            AiModel model = row.get("model_id") == null ? null : modelRepository.findById((UUID) row.get("model_id")).orElse(null);
            return new Ranked(stored.items(), (String) row.get("scoring_source"), model, (String) row.get("fallback_reason"), listId, stored.codes());
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            return null; // danh sach hong: tinh lai thay vi bao loi cho nguoi choi
        }
    }

    private void saveList(UUID requesterId, Ranked ranked) {
        try {
            String payload = objectMapper.writeValueAsString(new StoredList(ranked.list(), ranked.reasonCodes()));
            jdbc.update("DELETE FROM partner_search_lists WHERE expires_at < now()");
            jdbc.update("INSERT INTO partner_search_lists (id, requester_id, model_id, scoring_source, fallback_reason, payload_json, expires_at) VALUES (?, ?, ?, ?, ?, ?::jsonb, now() + make_interval(mins => ?))",
                    ranked.listId(), requesterId, ranked.model() == null ? null : ranked.model().getId(), ranked.source(),
                    ranked.fallbackReason(), payload, (int) LIST_TTL_MINUTES);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            // khong luu duoc danh sach chi lam trang sau phai tinh lai, khong lam hong lan tim hien tai
        }
    }

    private static PageResponse<PartnerSearchResponse> pageOf(List<PartnerSearchResponse> list, PartnerSearchQuery query) {
        int size = query.pageSize();
        int from = Math.min(list.size(), query.pageNumber() * size);
        int to = Math.min(list.size(), from + size);
        int totalPages = (int) Math.ceil(list.size() / (double) size);
        return new PageResponse<>(list.subList(from, to), query.pageNumber(), size, list.size(), totalPages);
    }

    private void recordSearch(UUID requesterId, PartnerSearchQuery query, PageResponse<PartnerSearchResponse> page,
                              Ranked ranked, long durationMs) {
        AiModel model = ranked.model() != null ? ranked.model() : runRecorder.partnerBaselineModel();
        Map<String, Object> params = searchParams(query);
        params.put("listId", ranked.listId().toString());
        params.put("poolSize", ranked.list().size());
        if (ranked.fallbackReason() != null) {
            params.put("fallbackReason", ranked.fallbackReason());
        }
        UUID searchId = runRecorder.recordSuccess(null, MatchingRunRecorder.PARTNER_SEARCH, requesterId,
                model, ranked.source(), page.content().size(), durationMs, params);
        User requester = userRepository.getReferenceById(requesterId);
        List<PartnerSuggestion> rows = new ArrayList<>();
        int offset = query.pageNumber() * query.pageSize();
        boolean ai = "ai".equals(ranked.source());
        for (int index = 0; index < page.content().size(); index++) {
            PartnerSearchResponse item = page.content().get(index);
            PartnerSuggestion row = new PartnerSuggestion();
            row.setModel(model);
            row.setUser(requester);
            row.setSuggestedUser(userRepository.getReferenceById(item.userId()));
            row.setSkillScore(item.skillSimilarity());
            row.setLocationScore(item.locationCompatibility());
            row.setTimeScore(item.timeCompatibility());
            row.setStyleScore(item.styleCompatibility());
            row.setTotalScore(item.totalScore());
            row.setConfidenceScore(ai ? item.confidence() : null);
            row.setScoringSource(ai ? SuggestionSource.AI : SuggestionSource.BASELINE);
            row.setSearchId(searchId);
            row.setRankNo((short) Math.min(offset + index + 1, Short.MAX_VALUE));
            Map<String, Object> reason = new LinkedHashMap<>();
            reason.put("ratingGap", item.ratingGap());
            reason.put("distanceKm", item.distanceKm());
            reason.put("provisional", item.provisional());
            reason.put("reasons", ranked.reasonCodes().getOrDefault(item.userId(), List.of()));
            row.setReasonJson(reason);
            rows.add(row);
        }
        suggestionRepository.saveAll(rows);
    }

    private static Map<String, Object> searchParams(PartnerSearchQuery query) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("scheduledAt", String.valueOf(query.scheduledAt()));
        params.put("durationMinutes", query.durationMinutes());
        params.put("ratingDifference", query.ratingDifference());
        params.put("page", query.pageNumber());
        params.put("size", query.pageSize());
        return params;
    }

    private Ranked rank(UUID requesterId, PartnerSearchQuery query) {
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
                        PageRequest.of(0, POOL_LIMIT));

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

        // Cham diem CHI cac ung vien da qua bo loc bat buoc (2.2.28); AI loi/qua han/chua co model thi dung baseline.
        Map<UUID, PlayerMatchProfile> profiles = new HashMap<>();
        matchProfileRepository.findAllById(scheduleUserIds).forEach(profile -> profiles.put(profile.getUserId(), profile));
        String requesterPlayType = requester.getPlayerProfile() == null || requester.getPlayerProfile().getPreferredPlayType() == null
                ? null : requester.getPlayerProfile().getPreferredPlayType().getValue();
        List<PartnerRankingService.Input> inputs = content.stream()
                .map(item -> new PartnerRankingService.Input(item.userId(),
                        aiFeatures(requesterRating, profiles.get(requesterId), requesterPlayType, item, profiles.get(item.userId()))))
                .toList();
        PartnerRankingService.Outcome outcome = rankingService.rank(requesterId, inputs);

        List<PartnerSearchResponse> scored = new ArrayList<>();
        Map<UUID, List<String>> reasonCodes = new HashMap<>();
        for (PartnerSearchResponse item : content) {
            BigDecimal baseline = PartnerScoring.total(item.skillSimilarity(), item.styleCompatibility(),
                    item.timeCompatibility(), item.locationCompatibility());
            PartnerRankingService.Scored ai = outcome.ai() ? outcome.scores().get(item.userId()) : null;
            BigDecimal total = ai == null ? baseline : ai.totalScore();
            if (ai != null && item.provisional() && total.compareTo(PROVISIONAL_CAP) > 0) {
                total = PROVISIONAL_CAP; // cold-start: nguoi duoi 5 tran khong duoc day len cao chi vi du lieu it
            }
            BigDecimal confidence = ai == null ? null : ai.confidence();
            List<String> codes = ai == null ? baselineReasons(item) : ai.reasonCodes();
            reasonCodes.put(item.userId(), codes);
            scored.add(withScore(item, total, confidence, codes, outcome.ai()));
        }
        // Xep theo diem giam dan (hoa thi giu thu tu goc: gan Elo roi gan vi tri), roi moi phan trang.
        List<PartnerSearchResponse> ordered = new ArrayList<>(scored);
        ordered.sort(Comparator.comparing(PartnerSearchResponse::totalScore).reversed());
        UUID listId = UUID.randomUUID();
        List<PartnerSearchResponse> finalList = ordered.stream().map(item -> withList(item, listId)).toList();
        return new Ranked(finalList, outcome.source(), outcome.model(), outcome.fallbackReason(), listId, reasonCodes);
    }

    private static Map<String, Object> aiFeatures(BigDecimal requesterRating, PlayerMatchProfile requesterProfile,
                                                  String requesterPlayType, PartnerSearchResponse candidate,
                                                  PlayerMatchProfile candidateProfile) {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("r_rating", requesterRating);
        f.put("c_rating", candidate.ratingScore());
        f.put("rating_gap", candidate.ratingGap());
        f.put("r_win_rate", requesterProfile == null ? BigDecimal.valueOf(50) : requesterProfile.getWinRate());
        f.put("c_win_rate", candidateProfile == null ? BigDecimal.valueOf(50) : candidateProfile.getWinRate());
        f.put("r_matches", requesterProfile == null ? 0 : requesterProfile.getTotalMatches());
        f.put("c_matches", candidate.totalMatches());
        f.put("r_stability", requesterProfile == null ? null : requesterProfile.getPerformanceConsistency());
        f.put("c_stability", candidateProfile == null ? null : candidateProfile.getPerformanceConsistency());
        f.put("r_recency_days", requesterProfile == null ? null : requesterProfile.getDataRecencyDays());
        f.put("c_recency_days", candidateProfile == null ? null : candidateProfile.getDataRecencyDays());
        f.put("r_avg_point_diff", requesterProfile == null ? null : requesterProfile.getAveragePointDifference());
        f.put("c_avg_point_diff", candidateProfile == null ? null : candidateProfile.getAveragePointDifference());
        f.put("skill_similarity", candidate.skillSimilarity());
        f.put("location_score", candidate.locationCompatibility());
        f.put("time_score", candidate.timeCompatibility());
        f.put("style_score", candidate.styleCompatibility());
        f.put("distance_km", candidate.distanceKm());
        f.put("same_play_type", requesterPlayType != null && requesterPlayType.equals(candidate.preferredPlayType()) ? 1 : 0);
        f.put("r_provisional", requesterProfile == null || requesterProfile.getTotalMatches() < PROVISIONAL_MATCH_LIMIT ? 1 : 0);
        f.put("c_provisional", candidate.provisional() ? 1 : 0);
        return f;
    }

    /** Ly do khi dung baseline: cac diem thanh phan cao nhat (toi da 3). */
    static List<String> baselineReasons(PartnerSearchResponse item) {
        record Part(String code, BigDecimal score, double threshold) {
        }
        List<Part> parts = new ArrayList<>(List.of(
                new Part("SKILL_BALANCED", item.skillSimilarity(), 70), new Part("NEAR_LOCATION", item.locationCompatibility(), 60),
                new Part("SAME_TIME", item.timeCompatibility(), 60), new Part("STYLE_MATCH", item.styleCompatibility(), 80)));
        return parts.stream().filter(part -> part.score() != null && part.score().doubleValue() >= part.threshold())
                .sorted(Comparator.comparing((Part part) -> part.score()).reversed()).limit(3).map(Part::code).toList();
    }

    static String confidenceLabel(BigDecimal confidence, boolean provisional) {
        if (provisional) return "Chưa đủ dữ liệu";
        if (confidence == null) return null;
        if (confidence.doubleValue() >= 0.7) return "Cao";
        if (confidence.doubleValue() >= 0.4) return "Trung bình";
        return "Thấp";
    }

    static final Map<String, String> REASON_TEXT = Map.of(
            "SKILL_BALANCED", "Trình độ sát nhau",
            "NEAR_LOCATION", "Gần khu vực chơi của bạn",
            "SAME_TIME", "Trùng khung giờ rảnh",
            "STYLE_MATCH", "Phong cách chơi hợp nhau",
            "SAME_PLAY_TYPE", "Cùng hình thức chơi",
            "GOOD_FORM", "Phong độ tốt gần đây",
            "EXPERIENCED", "Đã chơi nhiều trận");

    private static PartnerSearchResponse withScore(PartnerSearchResponse item, BigDecimal total, BigDecimal confidence,
                                                   List<String> codes, boolean ai) {
        List<String> texts = codes.stream().map(code -> REASON_TEXT.getOrDefault(code, null)).filter(java.util.Objects::nonNull).toList();
        return new PartnerSearchResponse(item.userId(), item.fullName(), item.avatarUrl(), item.playingStyle(),
                item.preferredPlayType(), item.ratingScore(), item.ratingLabel(), item.totalMatches(), item.provisional(),
                item.ratingGap(), item.skillSimilarity(), item.distanceKm(), item.locationCompatibility(),
                item.timeCompatibility(), item.styleCompatibility(), item.availableFrom(), item.availableUntil(),
                total, confidence, confidenceLabel(confidence, item.provisional()), texts,
                ai ? "ai" : "baseline", ai ? "Gợi ý bằng AI" : "Gợi ý theo công thức", null);
    }

    private static PartnerSearchResponse withList(PartnerSearchResponse item, UUID listId) {
        return new PartnerSearchResponse(item.userId(), item.fullName(), item.avatarUrl(), item.playingStyle(),
                item.preferredPlayType(), item.ratingScore(), item.ratingLabel(), item.totalMatches(), item.provisional(),
                item.ratingGap(), item.skillSimilarity(), item.distanceKm(), item.locationCompatibility(),
                item.timeCompatibility(), item.styleCompatibility(), item.availableFrom(), item.availableUntil(),
                item.totalScore(), item.confidence(), item.confidenceLabel(), item.reasons(), item.scoringSource(),
                item.sourceLabel(), listId);
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
                candidate.getAvailableUntil(),
                null, null, null, List.of(), null, null, null);
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

    /** Ty le gio ranh trung nhau giua hai nguoi so voi tong gio ranh cua nguoi it gio hon; null neu mot ben chua khai bao lich. */
    static BigDecimal weeklyOverlap(List<PlayerAvailabilitySlot> first, List<PlayerAvailabilitySlot> second) {
        Map<Short, List<MinuteRange>> left = weeklySchedule(first);
        Map<Short, List<MinuteRange>> right = weeklySchedule(second);
        long leftMinutes = left.values().stream().flatMap(List::stream).mapToLong(MinuteRange::duration).sum();
        long rightMinutes = right.values().stream().flatMap(List::stream).mapToLong(MinuteRange::duration).sum();
        long smaller = Math.min(leftMinutes, rightMinutes);
        if (smaller == 0) return null;
        long overlap = 0;
        for (Map.Entry<Short, List<MinuteRange>> entry : left.entrySet()) {
            overlap += overlapMinutes(entry.getValue(), right.getOrDefault(entry.getKey(), List.of()));
        }
        return clampPercentage(BigDecimal.valueOf(overlap).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(smaller), 8, RoundingMode.HALF_UP));
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

    static Map<Short, List<MinuteRange>> weeklySchedule(List<PlayerAvailabilitySlot> slots) {
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

    static long overlapMinutes(List<MinuteRange> left, List<MinuteRange> right) {
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

    record MinuteRange(int start, int end) {
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
