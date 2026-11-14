package com.courtly.service.admin;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Danh muc ly do quan tri vien tu choi san (2.3.35).
 *
 * <p>Frontend lay danh sach nay qua API, khong khai bao cung. Ly do "other" bat buoc kem ghi chu.
 */
public final class VenueRejectReasons {

    public static final String OTHER = "other";

    /** ma -> nhan hien thi cho chu san. */
    public static final Map<String, String> LABELS = labels();

    private VenueRejectReasons() {
    }

    private static Map<String, String> labels() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("incomplete_info", "Thong tin san chua day du");
        map.put("invalid_location", "Dia chi hoac toa do khong chinh xac");
        map.put("duplicate_venue", "San bi trung voi san da co tren he thong");
        map.put("inappropriate_content", "Noi dung hoac hinh anh khong phu hop");
        map.put("invalid_pricing", "Bang gia hoac gio hoat dong khong hop le");
        map.put(OTHER, "Ly do khac");
        return Collections.unmodifiableMap(map);
    }
}
