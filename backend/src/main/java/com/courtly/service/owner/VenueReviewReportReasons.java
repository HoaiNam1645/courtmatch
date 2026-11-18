package com.courtly.service.owner;

import com.courtly.api.owner.dto.OwnerVenueReviewResponses.ReportReason;
import java.util.List;

/** Danh muc ly do bao cao danh gia san khong phu hop (2.3.83). */
public final class VenueReviewReportReasons {

    public static final List<ReportReason> ALL = List.of(
            new ReportReason("offensive", "Xúc phạm, lăng mạ", "Lời lẽ thô tục, công kích cá nhân hoặc nhân viên sân.", false),
            new ReportReason("defamation", "Vu khống, sai sự thật", "Nêu thông tin sai sự thật về sân; hãy mô tả điểm sai nếu có.", false),
            new ReportReason("spam", "Quảng cáo, spam", "Đánh giá nhằm quảng cáo hoặc chèn liên kết, không liên quan trải nghiệm.", false),
            new ReportReason("personal_info", "Lộ thông tin cá nhân", "Đăng số điện thoại, địa chỉ hoặc thông tin riêng tư.", false),
            new ReportReason("other", "Lý do khác", "Mô tả rõ vì sao đánh giá không phù hợp (ít nhất 10 ký tự).", true));

    private VenueReviewReportReasons() {
    }

    public static ReportReason find(String code) {
        return ALL.stream().filter(reason -> reason.code().equals(code)).findFirst().orElse(null);
    }

    /** Nhan hien thi; ma khong co trong danh muc (du lieu cu) tra lai chinh ma. */
    public static String label(String code) {
        ReportReason reason = find(code);
        return reason == null ? code : reason.label();
    }
}
