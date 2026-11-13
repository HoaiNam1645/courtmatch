package com.courtly.service.owner;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Danh muc co dinh cho form san cua chu san (2.3.3, 2.3.4).
 *
 * <p>He thong chi phuc vu thanh pho Da Nang. Frontend lay danh sach nay qua API, khong
 * khai bao cung trong giao dien.
 */
public final class VenueCatalog {

    public static final String PROVINCE = "Da Nang";

    /** Cac quan/huyen cua Da Nang, viet dung nhu cot venues.district dang luu. */
    public static final List<String> DISTRICTS = List.of(
            "Hải Châu", "Thanh Khê", "Sơn Trà", "Ngũ Hành Sơn", "Liên Chiểu", "Cẩm Lệ", "Hòa Vang");

    /**
     * Khung bao toa do thanh pho Da Nang (ke ca huyen Hoa Vang).
     *
     * <p>Day la hinh chu nhat xap xi ranh gioi hanh chinh: chan duoc toa do o tinh khac,
     * nhung khong thay the duoc viec chu san chon dung vi tri tren ban do.
     */
    public static final BigDecimal MIN_LATITUDE = new BigDecimal("15.90");
    public static final BigDecimal MAX_LATITUDE = new BigDecimal("16.25");
    public static final BigDecimal MIN_LONGITUDE = new BigDecimal("107.80");
    public static final BigDecimal MAX_LONGITUDE = new BigDecimal("108.35");

    /** Loai san con -> nhan hien thi. */
    public static final Map<String, String> COURT_TYPES = orderedMap(
            "standard", "Tiêu chuẩn",
            "vip", "VIP");

    /** Mat san -> nhan hien thi. */
    public static final Map<String, String> SURFACE_TYPES = orderedMap(
            "vinyl", "Thảm PVC",
            "wood", "Sàn gỗ",
            "concrete", "Sàn bê tông");

    public static final int MAX_COURTS = 30;

    private VenueCatalog() {
    }

    public static boolean isInDaNang(BigDecimal latitude, BigDecimal longitude) {
        return latitude != null && longitude != null
                && latitude.compareTo(MIN_LATITUDE) >= 0 && latitude.compareTo(MAX_LATITUDE) <= 0
                && longitude.compareTo(MIN_LONGITUDE) >= 0 && longitude.compareTo(MAX_LONGITUDE) <= 0;
    }

    private static Map<String, String> orderedMap(String... pairs) {
        java.util.LinkedHashMap<String, String> map = new java.util.LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(pairs[i], pairs[i + 1]);
        }
        return java.util.Collections.unmodifiableMap(map);
    }
}
