package com.courtly.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Trang thai xu ly bao cao. */
public enum ReportStatus implements PersistableEnum {

    PENDING("pending"),
    REVIEWING("reviewing"),
    RESOLVED("resolved"),
    REJECTED("rejected");

    private final String value;

    ReportStatus(String value) {
        this.value = value;
    }

    @Override
    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static ReportStatus from(String value) {
        for (ReportStatus item : values()) {
            if (item.value.equals(value)) {
                return item;
            }
        }
        throw new IllegalArgumentException("Gia tri '" + value + "' khong hop le cho ReportStatus");
    }
}
