package com.courtly.service.owner;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Danh muc ly do chu san tu choi yeu cau dat san (2.3.14).
 *
 * <p>Frontend lay danh sach nay qua API, khong khai bao cung. Ly do "other" bat buoc kem ghi chu.
 */
public final class RejectReasons {

    public static final String OTHER = "other";

    /** ma -> nhan hien thi cho nguoi choi va chu san. */
    public static final Map<String, String> LABELS = labels();

    private RejectReasons() {
    }

    private static Map<String, String> labels() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("slot_unavailable", "Khung gio nay san khong the phuc vu");
        map.put("maintenance", "San dang bao tri hoac sua chua");
        map.put("private_event", "San dang phuc vu su kien hoac giai dau");
        map.put("schedule_conflict", "Da co lich khac da thoa thuan truoc");
        map.put(OTHER, "Ly do khac");
        return java.util.Collections.unmodifiableMap(map);
    }
}
