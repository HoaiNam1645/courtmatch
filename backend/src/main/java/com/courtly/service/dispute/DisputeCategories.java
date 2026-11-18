package com.courtly.service.dispute;

import com.courtly.api.dispute.dto.DisputeCategoryResponse;
import java.util.List;

/** Danh muc loai bao cao ve don dat san (2.3.86): nam loai co dinh. */
public final class DisputeCategories {

    public static final List<DisputeCategoryResponse> ALL = List.of(
            new DisputeCategoryResponse("venue_closed", "Sân đóng cửa",
                    "Bạn đến sân nhưng sân không mở cửa hoặc không cho vào. Nêu giờ đến và ai bạn đã liên hệ.", 10),
            new DisputeCategoryResponse("venue_mismatch", "Sân khác mô tả",
                    "Sân, mặt sân hoặc tiện ích khác với thông tin trên Courtly. Nêu rõ điểm khác biệt.", 10),
            new DisputeCategoryResponse("owner_cancelled", "Chủ sân tự huỷ",
                    "Chủ sân huỷ hoặc đổi lịch của bạn mà không có thỏa thuận. Nêu thời điểm và cách bạn được thông báo.", 10),
            new DisputeCategoryResponse("extra_charge", "Bị thu thêm tiền",
                    "Bạn bị thu cao hơn số tiền hiển thị trên đơn. Nêu số tiền bị thu thêm và lý do được đưa ra.", 10),
            new DisputeCategoryResponse("other", "Lý do khác",
                    "Mô tả rõ vấn đề bạn gặp phải (ít nhất 20 ký tự) để chủ sân và quản trị viên xử lý đúng.", 20));

    private DisputeCategories() {
    }

    public static DisputeCategoryResponse find(String code) {
        return ALL.stream().filter(item -> item.code().equals(code)).findFirst().orElse(null);
    }
}
