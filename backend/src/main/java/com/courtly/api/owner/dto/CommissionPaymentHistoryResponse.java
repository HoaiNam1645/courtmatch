package com.courtly.api.owner.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 2.3.26 - lich su thanh toan hoa hong cua chu san.
 *
 * @param totalAmount  tong so tien cua tap da loc (khong phu thuoc trang)
 * @param statusCounts so lan gui theo trang thai, tinh tren toan bo lich su (khong phu thuoc bo loc)
 */
public record CommissionPaymentHistoryResponse(List<CommissionPaymentResponse> content, int page, int size,
                                               long totalElements, int totalPages, BigDecimal totalAmount,
                                               Map<String, Long> statusCounts) {
}
