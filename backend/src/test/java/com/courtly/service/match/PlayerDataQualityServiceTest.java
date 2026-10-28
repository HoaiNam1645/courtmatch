package com.courtly.service.match;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.courtly.domain.account.User;
import com.courtly.domain.match.Match;
import com.courtly.domain.match.MatchPlayer;
import com.courtly.domain.match.MatchRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlayerDataQualityServiceTest {

    @Test
    void excludesTwentyFirstMeetingWithinSevenDays() {
        MatchRepository repository = mock(MatchRepository.class);
        PlayerDataQualityService service = new PlayerDataQualityService(repository);
        Match match = matchWithOpponents();
        when(repository.findConfirmedWithPlayers(match.getId())).thenReturn(Optional.of(match));
        when(repository.countPriorRatedMeetings(any(), any(), any(), any(), any()))
                .thenReturn(20L);

        service.evaluateConfirmedMatch(match.getId());

        assertThat(match.isExcludedFromRating()).isTrue();
        assertThat(match.getExclusionReason()).isEqualTo(PlayerDataQualityService.PAIR_LIMIT_REASON);
        verify(repository).save(match);
    }

    @Test
    void keepsTwentiethMeetingEligible() {
        MatchRepository repository = mock(MatchRepository.class);
        PlayerDataQualityService service = new PlayerDataQualityService(repository);
        Match match = matchWithOpponents();
        when(repository.findConfirmedWithPlayers(match.getId())).thenReturn(Optional.of(match));
        when(repository.countPriorRatedMeetings(any(), any(), any(), any(), any()))
                .thenReturn(19L);

        service.evaluateConfirmedMatch(match.getId());

        assertThat(match.isExcludedFromRating()).isFalse();
        assertThat(match.getExclusionReason()).isNull();
        verify(repository).countPriorRatedMeetings(
                eq(match.getPlayers().get(0).getUser().getId()),
                eq(match.getPlayers().get(1).getUser().getId()), any(), any(), any());
    }

    private static Match matchWithOpponents() {
        Instant playedAt = Instant.parse("2026-10-20T12:00:00Z");
        Match match = new Match();
        match.setId(UUID.randomUUID());
        match.setScheduledAt(playedAt);
        match.setCreatedAt(playedAt.plusSeconds(3600));
        match.getPlayers().add(player(match, (short) 1));
        match.getPlayers().add(player(match, (short) 2));
        return match;
    }

    private static MatchPlayer player(Match match, short teamNo) {
        User user = new User();
        user.setId(UUID.randomUUID());
        MatchPlayer player = new MatchPlayer();
        player.setMatch(match);
        player.setUser(user);
        player.setTeamNo(teamNo);
        return player;
    }
}
