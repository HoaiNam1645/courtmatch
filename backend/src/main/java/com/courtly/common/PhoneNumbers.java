package com.courtly.common;

import java.util.regex.Pattern;

/** Chuan hoa va kiem tra so dien thoai Viet Nam. */
public final class PhoneNumbers {

    /** So di dong Viet Nam sau khi chuan hoa ve dang 0xxxxxxxxx. */
    private static final Pattern VN_MOBILE = Pattern.compile("^0[35789]\\d{8}$");

    /** So co dinh (dau 02, 11 so - vd 0236 3xxx xxx) hoac tong dai 1800/1900. */
    private static final Pattern VN_LANDLINE = Pattern.compile("^(02\\d{9}|1[89]00\\d{4,6})$");

    private PhoneNumbers() {
    }

    /** Doi "+84 901 234 567", "84901234567", "090 123 4567" ve cung dang "0901234567". */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String digitsOnly = raw.replaceAll("[^0-9+]", "");
        if (digitsOnly.startsWith("+84")) {
            return "0" + digitsOnly.substring(3);
        }
        if (digitsOnly.startsWith("84") && (digitsOnly.length() == 11 || digitsOnly.length() == 12)) {
            return "0" + digitsOnly.substring(2);
        }
        return digitsOnly;
    }

    public static boolean isMobile(String normalized) {
        return normalized != null && VN_MOBILE.matcher(normalized).matches();
    }

    /** So lien he cua san (2.3.3): chap nhan ca di dong lan so co dinh. */
    public static boolean isContactNumber(String normalized) {
        return isMobile(normalized) || (normalized != null && VN_LANDLINE.matcher(normalized).matches());
    }
}
