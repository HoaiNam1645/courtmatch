package com.courtly.api.admin.dto;

import com.courtly.api.doubleteam.dto.DoubleTeamSuggestionResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 2.3.76 - lich su chia doi cua nguoi choi cho quan tri vien: moi dong la mot lan chon 4 nguoi (batch). */
public final class AdminTeamHistoryResponse {

    private AdminTeamHistoryResponse() {
    }

    public record Player(UUID id, String fullName) {
    }

    /** Phuong an khuyen nghi cua lan chia doi. */
    public record Recommended(BigDecimal team1Strength, BigDecimal team2Strength, BigDecimal balanceScore,
                              String balanceLabel, BigDecimal team1WinProbability) {
    }

    /**
     * Ket qua doi chieu voi tran that (2.3.71).
     *
     * @param status           none (chua co tran trung cach chia doi) hoac played
     * @param predictedCorrect du doan doi thang dung hay sai; null khi chua co tran hoac xac suat bang 50%
     */
    public record Reconciliation(String status, UUID matchId, Instant playedAt, Integer optionRank,
                                 Boolean optionRecommended, Boolean predictedCorrect) {
    }

    public record Item(UUID batchId, Instant createdAt, AdminPartnerHistoryResponse.PlayerRef requester,
                       String matchType, List<Player> players, int optionCount, Recommended recommended,
                       String scoringSource, String modelVersion, Reconciliation reconciliation) {
    }

    /** Chi tiet: day du 3 phuong an cung suc manh tung nguoi va tuong thich tung cap. */
    public record Detail(Item item, List<DoubleTeamSuggestionResponse.Player> players,
                         List<DoubleTeamSuggestionResponse.Pair> pairs,
                         List<DoubleTeamSuggestionResponse.Option> options) {
    }
}
