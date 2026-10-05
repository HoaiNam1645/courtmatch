package com.courtly.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Trang thai hoat dong cua dia diem san. */
public enum VenueStatus implements PersistableEnum {

    PENDING("pending"),
    ACTIVE("active"),
    INACTIVE("inactive"),
    REJECTED("rejected");

    private final String value;

    VenueStatus(String value) {
        this.value = value;
    }

    @Override
    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static VenueStatus from(String value) {
        for (VenueStatus item : values()) {
            if (item.value.equals(value)) {
                return item;
            }
        }
        throw new IllegalArgumentException("Gia tri '" + value + "' khong hop le cho VenueStatus");
    }
}
