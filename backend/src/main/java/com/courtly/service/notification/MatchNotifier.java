package com.courtly.service.notification;

import com.courtly.common.enums.MatchType;
import com.courtly.domain.account.User;
import com.courtly.domain.match.Match;
import com.courtly.domain.match.MatchPlayer;
import com.courtly.domain.notification.NotificationType;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Thong bao ve tran dau (2.3.58): duoc ghi vao tran, ket qua can xac nhan, tran bi huy.
 * Chi bao cho cac nguoi choi khac, khong bao cho nguoi vua thuc hien. Goi ben trong transaction cua nghiep vu.
 */
@Component
@RequiredArgsConstructor
public class MatchNotifier {

    private final NotificationService notifications;

    /** Nguoi tao ghi nhan mot tran: bao cho nhung nguoi con lai trong tran. */
    public void created(Match match, User actor) {
        forEachOther(match, actor, player -> notifications.send(player.getUser().getId(), NotificationType.MATCH_SCHEDULED,
                "Bạn được ghi vào một trận đấu",
                "%s ghi nhận bạn tham gia trận %s %s. Sau khi có tỉ số, bạn sẽ được nhắc xác nhận kết quả."
                        .formatted(actor.getFullName(), typeLabel(match.getMatchType()), where(match)),
                "/matches/" + match.getId(), data(match)));
    }

    /** Nguoi tao nhap ket qua: cac nguoi choi khac can xac nhan hoac bao sai. */
    public void resultRecorded(Match match, User actor) {
        forEachOther(match, actor, player -> notifications.send(player.getUser().getId(), NotificationType.MATCH_SCHEDULED,
                "Kết quả trận đấu cần bạn xác nhận",
                "%s đã nhập kết quả trận %s %s. Hãy xác nhận nếu đúng hoặc báo sai nếu có nhầm lẫn."
                        .formatted(actor.getFullName(), typeLabel(match.getMatchType()), where(match)),
                "/matches/" + match.getId(), data(match)));
    }

    /** Tran bi huy: bao cho nhung nguoi con lai. */
    public void cancelled(Match match, User actor) {
        forEachOther(match, actor, player -> notifications.send(player.getUser().getId(), NotificationType.MATCH_SCHEDULED,
                "Trận đấu đã bị hủy",
                "%s đã hủy trận %s %s, trận này không còn được tính vào thành tích."
                        .formatted(actor.getFullName(), typeLabel(match.getMatchType()), where(match)),
                "/matches", data(match)));
    }

    private void forEachOther(Match match, User actor, java.util.function.Consumer<MatchPlayer> action) {
        for (MatchPlayer player : match.getPlayers()) {
            if (!player.getUser().getId().equals(actor.getId())) {
                action.accept(player);
            }
        }
    }

    private static Map<String, Object> data(Match match) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("matchId", match.getId().toString());
        return data;
    }

    private static String typeLabel(MatchType type) {
        return switch (type) {
            case SINGLE -> "đơn";
            case DOUBLE -> "đôi";
            case MIXED_DOUBLE -> "đôi nam nữ";
        };
    }

    private static String where(Match match) {
        String place = match.getVenue() == null ? "" : "tại " + match.getVenue().getName() + " ";
        String when = match.getScheduledAt() == null ? "" : "lúc " + BookingNotifier.NotificationText.vietnamTime(match.getScheduledAt());
        String text = (place + when).trim();
        return text.isEmpty() ? "" : "(" + text + ")";
    }
}
