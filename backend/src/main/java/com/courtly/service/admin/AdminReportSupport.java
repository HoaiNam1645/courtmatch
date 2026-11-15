package com.courtly.service.admin;

import com.courtly.common.enums.ReportStatus;
import com.courtly.common.exception.ApiException;
import com.courtly.common.exception.ErrorCode;
import java.util.List;
import java.util.Map;

/** Quy tac dung chung khi xu ly bao cao (2.3.36, 2.3.37). */
final class AdminReportSupport {

    private static final int PREVIEW_LENGTH = 120;

    private AdminReportSupport() {
    }

    static List<ReportStatus> parseStatuses(String status) {
        if (status == null || status.isBlank() || "all".equals(status)) {
            return List.of(ReportStatus.values());
        }
        try {
            return List.of(ReportStatus.from(status));
        } catch (IllegalArgumentException exception) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "Trang thai bao cao khong hop le.",
                    Map.of("status", "Chi nhan all, pending, reviewing, resolved hoac rejected"));
        }
    }

    /** Mau LIKE an toan: escape ky tu dac biet va them % hai dau. */
    static String likePattern(String q) {
        return "%" + (q == null ? "" : q.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")) + "%";
    }

    static String cleanNote(String note) {
        String trimmed = note == null ? "" : note.trim();
        if (trimmed.length() < 3) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "Vui long nhap ghi chu (it nhat 3 ky tu).",
                    Map.of("note", "Bat buoc nhap ghi chu khi ket luan"));
        }
        return trimmed;
    }

    static String preview(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String stripped = text.strip();
        return stripped.length() <= PREVIEW_LENGTH ? stripped : stripped.substring(0, PREVIEW_LENGTH) + "...";
    }

    static ApiException stateInvalid(String message) {
        return ApiException.conflict(ErrorCode.REPORT_STATE_INVALID, message, Map.of("status", message));
    }
}
