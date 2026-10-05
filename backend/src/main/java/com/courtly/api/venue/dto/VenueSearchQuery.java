package com.courtly.api.venue.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Tham so tim kiem san.
 *
 * @param q          tu khoa tim theo ten, quan hoac dia chi (2.1.13)
 * @param district   loc theo khu vuc (2.1.19)
 * @param lat        vi do cua nguoi dung; co toa do thi ket qua sap theo khoang cach (2.1.14)
 * @param radiusKm   ban kinh tim kiem, chi co tac dung khi co lat/lng (2.1.20)
 * @param maxPricePerHour gia moi gio toi da (2.1.21)
 * @param availableOnly   chi hien san hom nay con khung gio trong (2.1.22)
 * @param size       gioi han 100 theo quy tac chung cua API
 */
public record VenueSearchQuery(

        @Size(max = 150, message = "Tu khoa toi da 150 ky tu")
        String q,

        @Size(max = 100, message = "Ten khu vuc toi da 100 ky tu")
        String district,

        @DecimalMin(value = "-90.0", message = "Vi do khong hop le")
        @DecimalMax(value = "90.0", message = "Vi do khong hop le")
        Double lat,

        @DecimalMin(value = "-180.0", message = "Kinh do khong hop le")
        @DecimalMax(value = "180.0", message = "Kinh do khong hop le")
        Double lng,

        @DecimalMin(value = "0.1", message = "Ban kinh tu 0.1 den 50 km")
        @DecimalMax(value = "50", message = "Ban kinh tu 0.1 den 50 km")
        Double radiusKm,

        @DecimalMin(value = "0", message = "Gia khong duoc am")
        BigDecimal maxPricePerHour,

        @DecimalMin(value = "0", message = "Diem danh gia tu 0 den 5")
        @DecimalMax(value = "5", message = "Diem danh gia tu 0 den 5")
        BigDecimal minRating,

        /** 2.1.22 - chi hien san hom nay con khung gio trong. */
        Boolean availableOnly,

        @Min(value = 0, message = "So trang bat dau tu 0")
        Integer page,

        @Min(value = 1, message = "Kich thuoc trang tu 1 den 100")
        @Max(value = 100, message = "Kich thuoc trang tu 1 den 100")
        Integer size) {

    private static final int DEFAULT_SIZE = 20;
    private static final double DEFAULT_RADIUS_KM = 5;

    public VenueSearchQuery {
        q = trimToNull(q);
        district = trimToNull(district);
    }

    @JsonIgnore
    @AssertTrue(message = "Phai gui ca lat va lng, hoac khong gui truong nao")
    public boolean isCoordinatePairComplete() {
        return (lat == null) == (lng == null);
    }

    public int pageNumber() {
        return page == null ? 0 : page;
    }

    public int pageSize() {
        return size == null ? DEFAULT_SIZE : size;
    }

    /** Chi co y nghia khi da co toa do; mac dinh 5 km giong chip "Gan toi" o giao dien. */
    public double radiusMetres() {
        return (radiusKm == null ? DEFAULT_RADIUS_KM : radiusKm) * 1000;
    }

    /** Boc tu khoa thanh dang ILIKE. Tra null khi khong tim theo tu khoa. */
    public String keywordPattern() {
        return q == null ? null : "%" + q + "%";
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
