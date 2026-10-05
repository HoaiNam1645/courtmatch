package com.courtly.common;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * Hinh dang phan trang dung chung cho moi API tra danh sach.
 *
 * @param page so trang, bat dau tu 0
 */
public record PageResponse<T>(List<T> content,
                              int page,
                              int size,
                              long totalElements,
                              int totalPages) {

    public static <S, T> PageResponse<T> from(Page<S> source, List<T> content) {
        return new PageResponse<>(
                content,
                source.getNumber(),
                source.getSize(),
                source.getTotalElements(),
                source.getTotalPages());
    }
}
