package com.courtly.api.owner.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 2.3.21 - cac booking cau thanh cong no hoa hong.
 *
 * <p>Du lieu tong hop truc tiep tu hoa hong tung booking (pending, due, adjusted), khong co so cai
 * cong no rieng. Sap xep theo han thanh toan, han som nhat truoc.
 *
 * @param totalAmount tong hoa hong cua tap da loc (khong phu thuoc trang)
 */
public record DebtBookingListResponse(List<CommissionListResponse.Item> content, int page, int size,
                                      long totalElements, int totalPages, BigDecimal totalAmount) {
}
