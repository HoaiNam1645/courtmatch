package com.courtly.service.match;

import com.courtly.api.match.dto.CreateMatchRequest;
import com.courtly.api.match.dto.DisputeMatchRequest;
import com.courtly.api.match.dto.MatchGameRequest;
import com.courtly.api.match.dto.MatchPlayerOptionResponse;
import com.courtly.api.match.dto.MatchResponse;
import com.courtly.api.match.dto.MatchHistoryResponse;
import com.courtly.api.match.dto.UpdateMatchGamesRequest;
import com.courtly.common.PageResponse;
import com.courtly.common.enums.BookingStatus;
import com.courtly.common.enums.JoinedStatus;
import com.courtly.common.enums.MatchDataSource;
import com.courtly.common.enums.MatchStatus;
import com.courtly.common.enums.MatchType;
import com.courtly.common.enums.UserStatus;
import com.courtly.common.enums.VenueStatus;
import com.courtly.common.exception.ApiException;
import com.courtly.common.exception.ErrorCode;
import com.courtly.domain.account.User;
import com.courtly.domain.account.UserRepository;
import com.courtly.domain.booking.Booking;
import com.courtly.domain.booking.BookingItem;
import com.courtly.domain.booking.BookingRepository;
import com.courtly.domain.match.Match;
import com.courtly.domain.match.MatchGame;
import com.courtly.domain.match.MatchGameRepository;
import com.courtly.domain.match.MatchPlayer;
import com.courtly.domain.match.MatchRepository;
import com.courtly.domain.match.MatchResult;
import com.courtly.domain.match.PlayerRatingHistoryRepository;
import com.courtly.domain.venue.Venue;
import com.courtly.domain.venue.VenueRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Thu thap va xac nhan ket qua tran dau (2.2.1-2.2.2). */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchService {

    private static final int AUTO_CONFIRM_HOURS = 72;
    private static final int DISPUTE_EXPIRY_DAYS = 7;

    private final MatchRepository matchRepository;
    private final MatchGameRepository matchGameRepository;
    private final PlayerRatingHistoryRepository ratingHistoryRepository;
    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final VenueRepository venueRepository;
    private final MatchScoreValidator scoreValidator;
    private final PlayerPerformanceService performanceService;

    @Transactional(readOnly = true)
    public PageResponse<MatchHistoryResponse> getHistory(UUID userId, String state,
                                                         int page, int size) {
        requireActiveUser(userId);
        String normalizedState = normalizeHistoryState(state);
        Page<UUID> ids = "pending".equals(normalizedState)
                ? matchRepository.findPendingHistoryIds(userId, PageRequest.of(page, size))
                : matchRepository.findHistoryIds(userId, historyStatuses(normalizedState),
                        PageRequest.of(page, size));
        if (ids.isEmpty()) {
            return PageResponse.from(ids, List.of());
        }

        List<UUID> orderedIds = ids.getContent();
        Map<UUID, Match> matches = matchRepository.findHistoryDetails(orderedIds).stream()
                .collect(java.util.stream.Collectors.toMap(Match::getId, item -> item));
        Map<UUID, List<MatchGame>> games = matchGameRepository
                .findByMatchIdInOrderByMatchIdAscGameNoAsc(orderedIds).stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        item -> item.getMatch().getId(), java.util.LinkedHashMap::new,
                        java.util.stream.Collectors.toList()));
        Map<UUID, java.math.BigDecimal> ratingChanges = ratingHistoryRepository
                .findByUserIdAndMatchIdIn(userId, orderedIds).stream()
                .collect(java.util.stream.Collectors.toMap(
                        item -> item.getMatch().getId(),
                        com.courtly.domain.match.PlayerRatingHistory::getChangeAmount,
                        (first, latest) -> latest));

        List<MatchHistoryResponse> content = orderedIds.stream()
                .map(matches::get)
                .filter(java.util.Objects::nonNull)
                .map(match -> toHistoryResponse(match, games.getOrDefault(match.getId(), List.of()),
                        ratingChanges.get(match.getId()), userId))
                .toList();
        return PageResponse.from(ids, content);
    }

    @Transactional(readOnly = true)
    public PageResponse<MatchPlayerOptionResponse> searchParticipants(UUID userId, String query,
                                                                      int page, int size) {
        String keyword = query == null || query.isBlank() ? "%" : "%" + query.trim() + "%";
        Page<User> users = userRepository.searchActivePlayers(userId, keyword, PageRequest.of(page, size));
        return PageResponse.from(users, users.getContent().stream()
                .map(user -> new MatchPlayerOptionResponse(user.getId(), user.getFullName(), user.getAvatarUrl()))
                .toList());
    }

    @Transactional(readOnly = true)
    public MatchResponse getDetail(UUID userId, UUID matchId) {
        return toResponse(requireParticipantMatch(userId, matchId), userId);
    }

    @Transactional
    public MatchResponse create(UUID userId, CreateMatchRequest request) {
        Instant now = Instant.now();
        User creator = requireActiveUser(userId);
        validateTeams(request.matchType(), request.team1UserIds(), request.team2UserIds(), userId);
        Map<UUID, User> players = requirePlayers(request.team1UserIds(), request.team2UserIds());
        short winningTeam = scoreValidator.validateAndFindWinner(request.games());

        Booking booking = null;
        Venue venue;
        Instant scheduledAt;
        MatchDataSource source;
        if (request.bookingId() != null) {
            booking = requireCompletedOwnBooking(userId, request.bookingId());
            if (matchRepository.existsByBookingIdAndStatusNot(booking.getId(), MatchStatus.CANCELLED)) {
                throw ApiException.conflict(ErrorCode.MATCH_ALREADY_RECORDED,
                        "Booking nay da duoc ghi nhan thanh tran dau.", Map.of());
            }
            venue = booking.getVenue();
            scheduledAt = booking.getItems().stream()
                    .map(BookingItem::getStartTime)
                    .min(Comparator.naturalOrder())
                    .orElseThrow(() -> ApiException.badRequest(ErrorCode.MATCH_STATE_INVALID,
                            "Booking khong co khung gio choi.", Map.of()));
            source = MatchDataSource.BOOKING;
        } else {
            if (request.scheduledAt() == null) {
                throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED,
                        "Can nhap thoi gian tran dau.", Map.of("scheduledAt", "Khong duoc de trong"));
            }
            scheduledAt = request.scheduledAt();
            venue = request.venueId() == null ? null : requireActiveVenue(request.venueId());
            source = MatchDataSource.MANUAL;
        }
        if (scheduledAt.isAfter(now)) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "Chi ghi ket qua cho tran da dien ra.",
                    Map.of("scheduledAt", "Thoi gian khong duoc o tuong lai"));
        }

        Match match = new Match();
        match.setId(UUID.randomUUID());
        match.setBooking(booking);
        match.setVenue(venue);
        match.setMatchType(request.matchType());
        match.setScheduledAt(scheduledAt);
        match.setStatus(MatchStatus.SCHEDULED);
        match.setDataSource(source);
        match.setCreatedBy(creator);
        match.setCreatedAt(now);
        addPlayers(match, request.team1UserIds(), (short) 1, players, userId, now);
        addPlayers(match, request.team2UserIds(), (short) 2, players, userId, now);
        replaceGames(match, request.games());

        MatchResult result = new MatchResult(match, winningTeam);
        result.setRecordedBy(creator);
        match.setResult(result);
        try {
            matchRepository.saveAndFlush(match);
        } catch (DataIntegrityViolationException exception) {
            if (booking != null) {
                throw ApiException.conflict(ErrorCode.MATCH_ALREADY_RECORDED,
                        "Booking nay vua duoc ghi nhan thanh tran dau.", Map.of());
            }
            throw exception;
        }
        return toResponse(match, userId);
    }

    @Transactional
    public MatchResponse updateGames(UUID userId, UUID matchId, UpdateMatchGamesRequest request) {
        Match match = requireParticipantMatch(userId, matchId);
        requireCreator(match, userId);
        if (match.getStatus() == MatchStatus.COMPLETED || match.getStatus() == MatchStatus.CANCELLED) {
            invalidState("Tran da ket thuc nen khong sua duoc ty so.");
        }
        short winningTeam = scoreValidator.validateAndFindWinner(request.games());
        match.getGames().clear();
        matchRepository.flush();
        replaceGames(match, request.games());
        match.getResult().setWinningTeam(winningTeam);
        match.getResult().setConfirmedAt(null);
        match.getResult().setNote(null);
        Instant now = Instant.now();
        match.getPlayers().forEach(player -> {
            player.setResultConfirmedAt(player.getUser().getId().equals(userId) ? now : null);
            player.setResultDisputedAt(null);
        });
        match.setStatus(MatchStatus.SCHEDULED);
        matchRepository.save(match);
        return toResponse(match, userId);
    }

    @Transactional
    public MatchResponse confirm(UUID userId, UUID matchId) {
        Match match = requireParticipantMatch(userId, matchId);
        if (match.getStatus() != MatchStatus.SCHEDULED || match.getResult() == null) {
            invalidState("Tran nay khong o trang thai cho xac nhan.");
        }
        MatchPlayer current = participant(match, userId);
        if (current.getResultConfirmedAt() == null) {
            current.setResultConfirmedAt(Instant.now());
            current.setResultDisputedAt(null);
        }
        if (bothTeamsConfirmed(match)) {
            complete(match, Instant.now(), null);
        }
        matchRepository.saveAndFlush(match);
        if (match.getStatus() == MatchStatus.COMPLETED) {
            performanceService.processConfirmedMatch(matchId);
        }
        return toResponse(match, userId);
    }

    @Transactional
    public MatchResponse dispute(UUID userId, UUID matchId, DisputeMatchRequest request) {
        Match match = requireParticipantMatch(userId, matchId);
        if (match.getStatus() != MatchStatus.SCHEDULED || match.getResult() == null) {
            invalidState("Tran nay khong con nhan bao sai.");
        }
        MatchPlayer current = participant(match, userId);
        current.setResultConfirmedAt(null);
        current.setResultDisputedAt(Instant.now());
        match.setStatus(MatchStatus.DISPUTED);
        match.getResult().setNote(request.reason().trim());
        matchRepository.save(match);
        return toResponse(match, userId);
    }

    @Transactional
    public void cancel(UUID userId, UUID matchId) {
        Match match = requireParticipantMatch(userId, matchId);
        requireCreator(match, userId);
        if (match.getStatus() == MatchStatus.COMPLETED
                || match.getStatus() == MatchStatus.CANCELLED
                || match.getResult() != null && match.getResult().getConfirmedAt() != null) {
            invalidState("Tran da xac nhan hoac da huy nen khong huy lai duoc.");
        }
        match.setStatus(MatchStatus.CANCELLED);
        matchRepository.save(match);
    }

    @Transactional
    public void autoConfirmOverdueMatches() {
        Instant now = Instant.now();
        List<Match> matches = matchRepository.findAwaitingConfirmationBefore(
                now.minus(AUTO_CONFIRM_HOURS, ChronoUnit.HOURS));
        for (Match match : matches) {
            boolean disputed = match.getPlayers().stream()
                    .anyMatch(player -> player.getResultDisputedAt() != null);
            if (disputed || match.getResult() == null) {
                continue;
            }
            match.getPlayers().forEach(player -> player.setResultConfirmedAt(now));
            complete(match, now, "Tu dong xac nhan sau 72 gio khong co bao sai.");
            matchRepository.saveAndFlush(match);
            performanceService.processConfirmedMatch(match.getId());
        }
    }

    @Transactional
    public void cancelExpiredDisputes() {
        List<Match> matches = matchRepository.findDisputedBefore(
                Instant.now().minus(DISPUTE_EXPIRY_DAYS, ChronoUnit.DAYS));
        matches.forEach(match -> match.setStatus(MatchStatus.CANCELLED));
        matchRepository.saveAll(matches);
    }

    private Booking requireCompletedOwnBooking(UUID userId, UUID bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> ApiException.notFound("Khong tim thay booking."));
        if (!booking.getUser().getId().equals(userId)) {
            throw ApiException.notFound("Khong tim thay booking.");
        }
        if (booking.getStatus() != BookingStatus.COMPLETED) {
            invalidState("Chi booking da hoan thanh moi co the ghi ket qua.");
        }
        return booking;
    }

    private User requireActiveUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("Khong tim thay tai khoan."));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw ApiException.forbidden(ErrorCode.FORBIDDEN, "Tai khoan khong hoat dong.");
        }
        return user;
    }

    private Venue requireActiveVenue(UUID venueId) {
        Venue venue = venueRepository.findById(venueId)
                .orElseThrow(() -> ApiException.notFound("Khong tim thay san."));
        if (venue.getStatus() != VenueStatus.ACTIVE) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "San khong hoat dong.", Map.of("venueId", "San khong hoat dong"));
        }
        return venue;
    }

    private Map<UUID, User> requirePlayers(List<UUID> team1, List<UUID> team2) {
        Set<UUID> ids = new HashSet<>(team1);
        ids.addAll(team2);
        Map<UUID, User> players = new HashMap<>();
        userRepository.findAllById(ids).forEach(user -> players.put(user.getId(), user));
        if (players.size() != ids.size() || players.values().stream()
                .anyMatch(user -> user.getStatus() != UserStatus.ACTIVE)) {
            throw ApiException.badRequest(ErrorCode.MATCH_PLAYERS_INVALID,
                    "Danh sach nguoi choi co tai khoan khong ton tai hoac khong hoat dong.",
                    Map.of("players", "Kiem tra lai nguoi tham gia"));
        }
        return players;
    }

    private static void validateTeams(MatchType type, List<UUID> team1, List<UUID> team2,
                                      UUID creatorId) {
        int teamSize = type == MatchType.SINGLE ? 1 : 2;
        if (team1.size() != teamSize || team2.size() != teamSize) {
            throw ApiException.badRequest(ErrorCode.MATCH_PLAYERS_INVALID,
                    type == MatchType.SINGLE ? "Danh don phai dung 1 nguoi moi doi."
                            : "Danh doi phai dung 2 nguoi moi doi.", Map.of("players", "Sai so nguoi"));
        }
        List<UUID> all = new ArrayList<>(team1);
        all.addAll(team2);
        if (new HashSet<>(all).size() != all.size()) {
            throw ApiException.badRequest(ErrorCode.MATCH_PLAYERS_INVALID,
                    "Mot nguoi khong the xuat hien o ca hai doi.", Map.of("players", "Nguoi choi bi trung"));
        }
        if (!all.contains(creatorId)) {
            throw ApiException.forbidden(ErrorCode.FORBIDDEN,
                    "Nguoi tao bat buoc phai tham gia tran dau.");
        }
    }

    private static void addPlayers(Match match, List<UUID> ids, short teamNo,
                                   Map<UUID, User> users, UUID creatorId, Instant now) {
        short position = 1;
        for (UUID id : ids) {
            MatchPlayer player = new MatchPlayer();
            player.setId(UUID.randomUUID());
            player.setMatch(match);
            player.setUser(users.get(id));
            player.setTeamNo(teamNo);
            player.setPositionNo(position++);
            player.setJoinedStatus(JoinedStatus.JOINED);
            player.setCreatedAt(now);
            if (id.equals(creatorId)) {
                player.setResultConfirmedAt(now);
            }
            match.getPlayers().add(player);
        }
    }

    private static void replaceGames(Match match, List<MatchGameRequest> requests) {
        for (MatchGameRequest request : requests) {
            MatchGame game = new MatchGame();
            game.setId(UUID.randomUUID());
            game.setMatch(match);
            game.setGameNo(request.gameNo().shortValue());
            game.setTeam1Score(request.team1Score());
            game.setTeam2Score(request.team2Score());
            match.getGames().add(game);
        }
    }

    private Match requireParticipantMatch(UUID userId, UUID matchId) {
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> ApiException.notFound("Khong tim thay tran dau."));
        if (match.getPlayers().stream().noneMatch(player -> player.getUser().getId().equals(userId))) {
            throw ApiException.notFound("Khong tim thay tran dau.");
        }
        return match;
    }

    private static MatchPlayer participant(Match match, UUID userId) {
        return match.getPlayers().stream()
                .filter(player -> player.getUser().getId().equals(userId))
                .findFirst()
                .orElseThrow();
    }

    private static void requireCreator(Match match, UUID userId) {
        if (match.getCreatedBy() == null || !match.getCreatedBy().getId().equals(userId)) {
            throw ApiException.forbidden(ErrorCode.FORBIDDEN,
                    "Chi nguoi tao tran moi duoc thuc hien thao tac nay.");
        }
    }

    private static boolean bothTeamsConfirmed(Match match) {
        return match.getPlayers().stream().anyMatch(p -> p.getTeamNo() == 1 && p.getResultConfirmedAt() != null)
                && match.getPlayers().stream().anyMatch(p -> p.getTeamNo() == 2 && p.getResultConfirmedAt() != null);
    }

    private static void complete(Match match, Instant now, String note) {
        match.setStatus(MatchStatus.COMPLETED);
        match.getResult().setConfirmedAt(now);
        if (note != null) {
            match.getResult().setNote(note);
        }
    }

    private static MatchResponse toResponse(Match match, UUID currentUserId) {
        MatchResult result = match.getResult();
        MatchPlayer current = participant(match, currentUserId);
        boolean pending = match.getStatus() == MatchStatus.SCHEDULED;
        return new MatchResponse(match.getId(),
                match.getBooking() == null ? null : match.getBooking().getId(),
                match.getMatchType(), match.getStatus(), match.getDataSource(), match.getScheduledAt(),
                match.getVenue() == null ? null : new MatchResponse.Venue(match.getVenue().getId(),
                        match.getVenue().getName(), match.getVenue().getAddress()),
                playersOf(match, (short) 1), playersOf(match, (short) 2),
                match.getGames().stream().sorted(Comparator.comparingInt(MatchGame::getGameNo))
                        .map(game -> new MatchResponse.Game(game.getGameNo(), game.getTeam1Score(),
                                game.getTeam2Score())).toList(),
                result == null ? null : result.getWinningTeam(),
                result == null ? null : result.getConfirmedAt(),
                result == null ? null : result.getNote(),
                match.getCreatedBy() != null && match.getCreatedBy().getId().equals(currentUserId)
                        && (match.getStatus() == MatchStatus.SCHEDULED || match.getStatus() == MatchStatus.DISPUTED),
                match.getCreatedBy() != null && match.getCreatedBy().getId().equals(currentUserId)
                        && match.getStatus() != MatchStatus.COMPLETED && match.getStatus() != MatchStatus.CANCELLED,
                pending && current.getResultConfirmedAt() == null,
                pending);
    }

    private static List<MatchResponse.Player> playersOf(Match match, short teamNo) {
        return match.getPlayers().stream().filter(player -> player.getTeamNo() == teamNo)
                .sorted(Comparator.comparing(MatchPlayer::getPositionNo,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(player -> new MatchResponse.Player(player.getUser().getId(),
                        player.getUser().getFullName(), player.getUser().getAvatarUrl(),
                        player.getResultConfirmedAt() != null, player.getResultDisputedAt() != null))
                .toList();
    }

    private static MatchHistoryResponse toHistoryResponse(
            Match match, List<MatchGame> games, java.math.BigDecimal ratingChange,
            UUID currentUserId) {
        MatchPlayer current = participant(match, currentUserId);
        MatchResult result = match.getResult();
        return new MatchHistoryResponse(
                match.getId(), match.getMatchType(), match.getStatus(), match.getDataSource(),
                match.getScheduledAt(),
                match.getVenue() == null ? null : new MatchHistoryResponse.Venue(
                        match.getVenue().getId(), match.getVenue().getName(),
                        match.getVenue().getAddress()),
                historyPlayers(match, (short) 1), historyPlayers(match, (short) 2),
                games.stream().map(game -> new MatchHistoryResponse.Game(
                        game.getGameNo(), game.getTeam1Score(), game.getTeam2Score())).toList(),
                result == null ? null : result.getWinningTeam(), ratingChange,
                match.getStatus() == MatchStatus.SCHEDULED
                        && current.getResultConfirmedAt() == null);
    }

    private static List<MatchHistoryResponse.Player> historyPlayers(Match match, short teamNo) {
        return match.getPlayers().stream()
                .filter(player -> player.getTeamNo() == teamNo)
                .sorted(Comparator.comparing(MatchPlayer::getPositionNo,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(player -> new MatchHistoryResponse.Player(
                        player.getUser().getId(), player.getUser().getFullName(),
                        player.getUser().getAvatarUrl(), player.getResultConfirmedAt() != null,
                        player.getResultDisputedAt() != null))
                .toList();
    }

    private static String normalizeHistoryState(String rawState) {
        String state = rawState == null ? "all" : rawState.trim().toLowerCase();
        if (!Set.of("all", "pending", "scheduled", "completed", "disputed", "cancelled")
                .contains(state)) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "Trang thai loc lich su khong hop le.",
                    Map.of("state", "Chi chap nhan all, pending, scheduled, completed, disputed, cancelled"));
        }
        return state;
    }

    private static List<MatchStatus> historyStatuses(String state) {
        return switch (state) {
            case "all" -> List.of(MatchStatus.SCHEDULED, MatchStatus.COMPLETED,
                    MatchStatus.DISPUTED, MatchStatus.CANCELLED);
            case "scheduled" -> List.of(MatchStatus.SCHEDULED);
            case "completed" -> List.of(MatchStatus.COMPLETED);
            case "disputed" -> List.of(MatchStatus.DISPUTED);
            case "cancelled" -> List.of(MatchStatus.CANCELLED);
            default -> throw new IllegalArgumentException("Pending history uses a dedicated query");
        };
    }

    private static void invalidState(String message) {
        throw ApiException.conflict(ErrorCode.MATCH_STATE_INVALID, message, Map.of());
    }
}
