package com.courtly.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Trang thai cua tung san con. */
public enum CourtStatus implements PersistableEnum {

    ACTIVE("active"),
    INACTIVE("inactive"),
    MAINTENANCE("maintenance");

    private final String value;

    CourtStatus(String value) {
        this.value = value;
    }

    @Override
    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static CourtStatus from(String value) {
        for (CourtStatus item : values()) {
            if (item.value.equals(value)) {
                return item;
            }
        }
        throw new IllegalArgumentException("Gia tri '" + value + "' khong hop le cho CourtStatus");
    }
}
