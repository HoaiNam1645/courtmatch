package com.courtly.service.notification;

import com.courtly.api.notification.dto.NotificationResponses;
import com.courtly.common.PageResponse;
import com.courtly.common.exception.ApiException;
import com.courtly.common.exception.ErrorCode;
import com.courtly.domain.notification.Notification;
import com.courtly.domain.notification.NotificationRepository;
import com.courtly.domain.notification.NotificationType;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 2.3.61 - xem lich su thong bao cua chinh minh, danh dau da doc. */
@Service
@RequiredArgsConstructor
public class NotificationQueryService {

    private final NotificationRepository notificationRepository;

    /** @param type null hoac mot loai thong bao hop le; unreadOnly chi lay chua doc */
    @Transactional(readOnly = true)
    public NotificationResponses.ListResponse list(UUID userId, String type, boolean unreadOnly, int page, int size) {
        String filter = validType(type);
        Page<Notification> found = notificationRepository.findForUser(userId, filter, unreadOnly, PageRequest.of(page, size));
        PageResponse<NotificationResponses.Item> response = PageResponse.from(found,
                found.getContent().stream().map(NotificationQueryService::toItem).toList());
        return new NotificationResponses.ListResponse(response, notificationRepository.countByUserIdAndReadAtIsNull(userId));
    }

    @Transactional(readOnly = true)
    public NotificationResponses.UnreadCount unreadCount(UUID userId) {
        return new NotificationResponses.UnreadCount(notificationRepository.countByUserIdAndReadAtIsNull(userId));
    }

    /** Danh dau mot thong bao la da doc; chi thong bao cua chinh minh, doc lai khong doi gio doc. */
    @Transactional
    public NotificationResponses.Item markRead(UUID userId, UUID notificationId) {
        Notification notification = notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> ApiException.notFound("Khong tim thay thong bao."));
        if (notification.getReadAt() == null) {
            notification.setReadAt(Instant.now());
            notificationRepository.save(notification);
        }
        return toItem(notification);
    }

    @Transactional
    public NotificationResponses.MarkAllResult markAllRead(UUID userId, String type) {
        int updated = notificationRepository.markAllRead(userId, validType(type), Instant.now());
        return new NotificationResponses.MarkAllResult(updated, notificationRepository.countByUserIdAndReadAtIsNull(userId));
    }

    private static String validType(String type) {
        if (type == null || type.isBlank() || "all".equals(type)) {
            return null;
        }
        if (!List.of(NotificationType.ALL).contains(type)) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "Loai thong bao khong hop le.",
                    Map.of("type", "Chi nhan all hoac " + String.join(", ", NotificationType.ALL)));
        }
        return type;
    }

    static NotificationResponses.Item toItem(Notification notification) {
        return new NotificationResponses.Item(notification.getId(), notification.getType(), notification.getTitle(),
                notification.getContent(), notification.getDataJson(), notification.getReadAt() != null, notification.getReadAt(),
                notification.getCreatedAt());
    }
}
