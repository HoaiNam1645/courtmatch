package com.courtly.service.match;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.courtly.api.match.dto.MatchGameRequest;
import com.courtly.common.exception.ApiException;
import com.courtly.common.exception.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.Test;

class MatchScoreValidatorTest {

    private final MatchScoreValidator validator = new MatchScoreValidator();

    @Test
    void derivesTeamOneAsWinner() {
        short winner = validator.validateAndFindWinner(List.of(
                game(1, 21, 18), game(2, 19, 21), game(3, 30, 29)));
        assertThat(winner).isEqualTo((short) 1);
    }

    @Test
    void acceptsStraightSetsAndDeuce() {
        short winner = validator.validateAndFindWinner(List.of(
                game(1, 22, 20), game(2, 29, 27)));
        assertThat(winner).isEqualTo((short) 1);
    }

    @Test
    void rejectsNonSequentialGames() {
        assertInvalid(List.of(game(1, 21, 18), game(3, 21, 17)));
    }

    @Test
    void rejectsTie() {
        assertInvalid(List.of(game(1, 21, 21), game(2, 21, 18)));
    }

    @Test
    void rejectsTwentyOneTwenty() {
        assertInvalid(List.of(game(1, 21, 20), game(2, 21, 18)));
    }

    @Test
    void rejectsInvalidLargeGapAfterDeuce() {
        assertInvalid(List.of(game(1, 25, 20), game(2, 21, 18)));
    }

    @Test
    void rejectsScoreOverCap() {
        assertInvalid(List.of(game(1, 31, 29), game(2, 21, 18)));
    }

    @Test
    void rejectsThirdGameAfterStraightSets() {
        assertInvalid(List.of(game(1, 21, 18), game(2, 21, 17), game(3, 21, 16)));
    }

    @Test
    void rejectsMatchWithoutTwoGameWinner() {
        assertInvalid(List.of(game(1, 21, 18), game(2, 18, 21)));
    }

    private void assertInvalid(List<MatchGameRequest> games) {
        assertThatThrownBy(() -> validator.validateAndFindWinner(games))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).getCode())
                .isEqualTo(ErrorCode.MATCH_SCORE_INVALID);
    }

    private static MatchGameRequest game(int gameNo, int team1, int team2) {
        return new MatchGameRequest(gameNo, team1, team2);
    }
}
