package com.courtly.service.match;

import com.courtly.api.match.dto.MatchGameRequest;
import com.courtly.common.exception.ApiException;
import com.courtly.common.exception.ErrorCode;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Kiem tra phan luat cau long can thiet de 2.2.2 khong ghi du lieu mau thuan. */
@Component
public class MatchScoreValidator {

    public short validateAndFindWinner(List<MatchGameRequest> games) {
        Set<Integer> gameNumbers = new HashSet<>();
        int team1Wins = 0;
        int team2Wins = 0;

        for (int index = 0; index < games.size(); index++) {
            MatchGameRequest game = games.get(index);
            if (game.gameNo() != index + 1 || !gameNumbers.add(game.gameNo())) {
                invalid("So set phai lien tuc, bat dau tu 1.");
            }
            validateGame(game.team1Score(), game.team2Score());
            if (game.team1Score() > game.team2Score()) {
                team1Wins++;
            } else {
                team2Wins++;
            }
        }

        if (team1Wins != 2 && team2Wins != 2) {
            invalid("Tran dau phai co mot doi thang du hai set.");
        }
        if (games.size() == 3 && (team1Wins == 3 || team2Wins == 3)) {
            invalid("Khong duoc nhap set thu ba khi mot doi da thang hai set lien tiep.");
        }
        return (short) (team1Wins == 2 ? 1 : 2);
    }

    private void validateGame(int team1Score, int team2Score) {
        int winner = Math.max(team1Score, team2Score);
        int loser = Math.min(team1Score, team2Score);
        boolean normalWin = winner == 21 && loser <= 19;
        boolean deuceWin = winner >= 22 && winner <= 29 && winner - loser == 2;
        boolean cappedWin = winner == 30 && loser == 29;
        if (!normalWin && !deuceWin && !cappedWin) {
            invalid("Ty so set khong dung luat cau long.");
        }
    }

    private static void invalid(String message) {
        throw ApiException.badRequest(ErrorCode.MATCH_SCORE_INVALID, message,
                Map.of("games", message));
    }
}
