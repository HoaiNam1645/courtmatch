package com.courtly.service.owner;

import com.courtly.api.owner.dto.CommissionPaymentHistoryResponse;
import com.courtly.common.exception.ApiException;
import com.courtly.common.exception.ErrorCode;
import com.courtly.domain.finance.CommissionPaymentReport;
import com.courtly.domain.finance.CommissionPaymentReportRepository;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 2.3.26 - chu san xem lich su thanh toan hoa hong cho Courtly. */
@Service
@RequiredArgsConstructor
public class OwnerCommissionPaymentHistoryService {

    static final List<String> ALL_STATUSES = List.of("pending_review", "confirmed", "rejected");

    private static final LocalDate FAR_PAST = LocalDate.of(2000, 1, 1);
    private static final LocalDate FAR_FUTURE = LocalDate.of(2100, 1, 1);

    private final CommissionPaymentReportRepository reportRepository;

    /**
     * @param status all hoac mot trang thai
     * @param from   loc theo ngay thanh toan, tinh ca ngay nay
     */
    @Transactional(readOnly = true)
    public CommissionPaymentHistoryResponse list(UUID ownerId, String status, LocalDate from, LocalDate to, int page, int size) {
        List<String> statuses = parseStatuses(status);
        if (from != null && to != null && to.isBefore(from)) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "Khoang ngay khong hop le.",
                    Map.of("to", "Ngay ket thuc phai sau hoac bang ngay bat dau"));
        }
        LocalDate start = from == null ? FAR_PAST : from;
        LocalDate end = to == null ? FAR_FUTURE : to;

        Page<CommissionPaymentReport> reports = reportRepository.findHistory(ownerId, statuses, start, end, PageRequest.of(page, size));
        Map<String, Long> counts = new LinkedHashMap<>();
        ALL_STATUSES.forEach(value -> counts.put(value, 0L));
        reportRepository.totalsByStatus(ownerId).forEach(row -> counts.put(row.getStatus(), row.getTotal()));

        return new CommissionPaymentHistoryResponse(
                reports.getContent().stream().map(OwnerCommissionPaymentService::toResponse).toList(),
                reports.getNumber(), reports.getSize(), reports.getTotalElements(), reports.getTotalPages(),
                reportRepository.sumHistory(ownerId, statuses, start, end), counts);
    }

    private static List<String> parseStatuses(String status) {
        if (status == null || status.isBlank() || "all".equals(status)) {
            return ALL_STATUSES;
        }
        if (!ALL_STATUSES.contains(status)) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "Trang thai thanh toan khong hop le.",
                    Map.of("status", "Chi nhan all hoac " + String.join(", ", ALL_STATUSES)));
        }
        return List.of(status);
    }
}
