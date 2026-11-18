package com.courtly.service.notification;

import com.courtly.api.notification.dto.NotificationResponses;
import com.courtly.api.notification.dto.UpdatePreferencesRequest;
import com.courtly.common.exception.ApiException;
import com.courtly.common.exception.ErrorCode;
import com.courtly.domain.account.User;
import com.courtly.domain.account.UserRepository;
import com.courtly.domain.notification.NotificationPreference;
import com.courtly.domain.notification.NotificationPreferenceId;
import com.courtly.domain.notification.NotificationPreferenceRepository;
import com.courtly.domain.notification.NotificationType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 2.3.62 - xem va bat/tat tung loai thong bao theo kenh trong ung dung / email. */
@Service
@RequiredArgsConstructor
public class NotificationPreferenceService {

    private record Meta(String label, String description) {
    }

    private static final Map<String, Meta> META = new LinkedHashMap<>();

    static {
        META.put(NotificationType.BOOKING_CONFIRMED, new Meta("Xác nhận đặt sân", "Chủ sân chấp nhận hoặc từ chối đặt sân, xác nhận đã thu tiền."));
        META.put(NotificationType.BOOKING_CANCELLED, new Meta("Hủy đặt sân", "Đặt sân bị hủy hoặc hết hạn."));
        META.put(NotificationType.PAYMENT_STATUS, new Meta("Trạng thái thanh toán", "Báo đã thanh toán, xác nhận hoặc từ chối thanh toán, xử lý công nợ hoa hồng."));
        META.put(NotificationType.BOOKING_REMINDER, new Meta("Nhắc nhở đặt sân", "Nhắc trước giờ chơi của booking đã xác nhận."));
        META.put(NotificationType.PARTNER_REQUEST, new Meta("Yêu cầu ghép cặp", "Có người gửi lời mời ghép cặp cho bạn."));
        META.put(NotificationType.PARTNER_REQUEST_RESPONSE, new Meta("Phản hồi ghép cặp", "Lời mời ghép cặp của bạn được chấp nhận hoặc từ chối."));
        META.put(NotificationType.PARTNER_REQUEST_EXPIRED, new Meta("Lời mời ghép cặp hết hạn", "Lời mời ghép cặp của bạn không được phản hồi kịp thời gian."));
        META.put(NotificationType.VENUE_REVIEW_REPLIED, new Meta("Chủ sân trả lời đánh giá", "Chủ sân trả lời đánh giá sân bạn đã viết."));
        META.put(NotificationType.VENUE_REVIEW_HIDDEN, new Meta("Đánh giá sân bị ẩn", "Quản trị viên ẩn đánh giá sân bạn đã viết vì nội dung không phù hợp."));
        META.put(NotificationType.BOOKING_DISPUTE_OPENED, new Meta("Báo cáo về đơn đặt sân", "Người chơi báo cáo vấn đề về đơn đặt sân của sân bạn, kèm hạn giải trình."));
        META.put(NotificationType.BOOKING_DISPUTE_UPDATED, new Meta("Bổ sung báo cáo đơn đặt sân", "Người chơi bổ sung thông tin cho báo cáo đang chờ bạn giải trình."));
        META.put(NotificationType.BOOKING_DISPUTE_CANCELLED, new Meta("Báo cáo đơn bị huỷ", "Người chơi huỷ báo cáo về đơn đặt sân, bạn không cần giải trình nữa."));
        META.put(NotificationType.BOOKING_DISPUTE_RESPONDED, new Meta("Chủ sân giải trình báo cáo", "Chủ sân đã giải trình về báo cáo đơn đặt sân của bạn, quản trị viên sẽ xem xét."));
        META.put(NotificationType.MATCH_SCHEDULED, new Meta("Lịch thi đấu", "Bạn được ghi vào một trận đấu, kết quả cần bạn xác nhận hoặc trận bị hủy."));
        META.put(NotificationType.MATCH_REMINDER, new Meta("Nhắc nhở trận đấu", "Nhắc xác nhận kết quả trận đấu sắp tự xác nhận."));
        META.put(NotificationType.DEBT_RESTRICTION, new Meta("Hạn chế do công nợ", "Chủ sân bị hạn chế hoặc được gỡ hạn chế vì công nợ hoa hồng quá hạn (bắt buộc)."));
    }

    private final NotificationPreferenceRepository preferenceRepository;
    private final UserRepository userRepository;

    /** Mọi loại thông báo kèm tùy chọn hiện tại; loại chưa có bản ghi dùng mặc định (bật trong ứng dụng và email). */
    @Transactional(readOnly = true)
    public NotificationResponses.PreferenceList get(UUID userId) {
        Map<String, NotificationPreference> saved = new HashMap<>();
        preferenceRepository.findByUserId(userId).forEach(item -> saved.put(item.getId().getType(), item));
        List<NotificationResponses.Preference> result = new ArrayList<>();
        for (Map.Entry<String, Meta> entry : META.entrySet()) {
            NotificationPreference preference = saved.get(entry.getKey());
            boolean mandatory = NotificationType.MANDATORY.contains(entry.getKey());
            result.add(new NotificationResponses.Preference(entry.getKey(), entry.getValue().label(), entry.getValue().description(),
                    mandatory || preference == null || preference.isInAppEnabled(), preference == null || preference.isEmailEnabled(),
                    false, mandatory));
        }
        return new NotificationResponses.PreferenceList(result);
    }

    /**
     * Cập nhật các loại được gửi lên; loại không gửi giữ nguyên. Loại bắt buộc không tắt được kênh trong ứng dụng.
     * Chỉ sửa tùy chọn của chính người dùng đang đăng nhập.
     */
    @Transactional
    public NotificationResponses.PreferenceList update(UUID userId, UpdatePreferencesRequest request) {
        Set<String> seen = new HashSet<>();
        Map<String, String> errors = new LinkedHashMap<>();
        for (UpdatePreferencesRequest.Item item : request.preferences()) {
            if (!META.containsKey(item.type())) {
                errors.put("preferences", "Loai thong bao khong hop le: " + item.type());
            } else if (!seen.add(item.type())) {
                errors.put("preferences", "Loai thong bao bi lap: " + item.type());
            } else if (NotificationType.MANDATORY.contains(item.type()) && !item.inAppEnabled()) {
                errors.put("preferences", "Khong tat duoc thong bao bat buoc: " + item.type());
            }
        }
        if (!errors.isEmpty()) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, errors.values().iterator().next(), errors);
        }
        User user = userRepository.findById(userId).orElseThrow(() -> ApiException.notFound("Khong tim thay tai khoan."));
        for (UpdatePreferencesRequest.Item item : request.preferences()) {
            NotificationPreference preference = preferenceRepository.findById(new NotificationPreferenceId(userId, item.type()))
                    .orElseGet(() -> new NotificationPreference(user, item.type()));
            preference.setInAppEnabled(item.inAppEnabled());
            preference.setEmailEnabled(item.emailEnabled());
            preference.setPushEnabled(false);
            preferenceRepository.save(preference);
        }
        preferenceRepository.flush();
        return get(userId);
    }
}
