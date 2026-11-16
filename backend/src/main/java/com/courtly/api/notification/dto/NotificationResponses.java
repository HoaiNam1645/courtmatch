package com.courtly.api.notification.dto;

import com.courtly.common.PageResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 2.3.61 - 2.3.62: lich su thong bao va tuy chon nhan thong bao. */
public final class NotificationResponses {

    private NotificationResponses() {
    }

    /** @param data metadata dieu huong, thuong co "route"; null neu khong co */
    public record Item(UUID id, String type, String title, String content, Map<String, Object> data, boolean read,
                       Instant readAt, Instant createdAt) {
    }

    /** @param unreadCount so thong bao chua doc cua nguoi dung (khong phu thuoc bo loc) */
    public record ListResponse(PageResponse<Item> page, long unreadCount) {
    }

    public record UnreadCount(long unreadCount) {
    }

    public record MarkAllResult(int updated, long unreadCount) {
    }

    /**
     * @param pushSupported kenh push chua duoc ho tro nen luon false
     * @param mandatory     khong tat duoc kenh trong ung dung
     */
    public record Preference(String type, String label, String description, boolean inAppEnabled, boolean emailEnabled,
                             boolean pushSupported, boolean mandatory) {
    }

    public record PreferenceList(List<Preference> preferences) {
    }
}
