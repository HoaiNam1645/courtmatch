package com.courtly.service.notification;

import com.courtly.domain.matching.PartnerRequest;
import com.courtly.domain.notification.NotificationType;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Thong bao ve ghep cap (2.3.56 - 2.3.57). Goi ben trong transaction cua nghiep vu. */
@Component
@RequiredArgsConstructor
public class PartnerNotifier {

    private static final int MAX_MESSAGE = 120;

    private final NotificationService notifications;

    /** 2.3.56 - co nguoi gui yeu cau ghep cap: bao cho nguoi nhan. */
    public void requestSent(PartnerRequest request) {
        notifications.send(request.getReceiver().getId(), NotificationType.PARTNER_REQUEST, "Lời mời ghép cặp mới",
                "%s gửi lời mời ghép cặp cho bạn: “%s”".formatted(request.getRequester().getFullName(), shorten(request.getMessage())),
                "/partners/requests", data(request));
    }

    /** 2.3.57 - nguoi nhan chap nhan: bao cho nguoi gui, goi y dat san cung. */
    public void accepted(PartnerRequest request) {
        notifications.send(request.getRequester().getId(), NotificationType.PARTNER_REQUEST_RESPONSE, "Lời mời ghép cặp được chấp nhận",
                "%s đã chấp nhận lời mời ghép cặp của bạn. Hai bạn đã là đối tác, hãy đặt sân chơi cùng nhau!"
                        .formatted(request.getReceiver().getFullName()),
                "/partners/connections", data(request));
    }

    /** 2.3.57 - nguoi nhan tu choi: bao cho nguoi gui. */
    public void rejected(PartnerRequest request) {
        notifications.send(request.getRequester().getId(), NotificationType.PARTNER_REQUEST_RESPONSE, "Lời mời ghép cặp bị từ chối",
                "%s đã từ chối lời mời ghép cặp của bạn. Bạn có thể tìm đối tác khác phù hợp."
                        .formatted(request.getReceiver().getFullName()),
                "/partners", data(request));
    }

    /** Yeu cau qua han khong duoc phan hoi: bao cho nguoi gui de tim doi tac khac hoac gui lai. */
    public void expired(PartnerRequest request) {
        notifications.send(request.getRequester().getId(), NotificationType.PARTNER_REQUEST_EXPIRED, "Lời mời ghép cặp đã hết hạn",
                "%s chưa phản hồi lời mời ghép cặp của bạn nên lời mời đã tự hủy. Bạn có thể gửi lại hoặc tìm đối tác khác."
                        .formatted(request.getReceiver().getFullName()),
                "/partners", data(request));
    }

    private static Map<String, Object> data(PartnerRequest request) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("requestId", request.getId().toString());
        data.put("requesterId", request.getRequester().getId().toString());
        data.put("receiverId", request.getReceiver().getId().toString());
        return data;
    }

    private static String shorten(String message) {
        String text = message == null ? "" : message.trim();
        return text.length() <= MAX_MESSAGE ? text : text.substring(0, MAX_MESSAGE - 1) + "…";
    }
}
