package com.courtly.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Trang thai hien thi danh gia. */
public enum ReviewStatus implements PersistableEnum {

    VISIBLE("visible"),
    HIDDEN("hidden"),
    REPORTED("reported");

    private final String value;

    ReviewStatus(String value) {
        this.value = value;
    }

    @Override
    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static ReviewStatus from(String value) {
        for (ReviewStatus item : values()) {
            if (item.value.equals(value)) {
                return item;
            }
        }
        throw new IllegalArgumentException("Gia tri '" + value + "' khong hop le cho ReviewStatus");
    }
}
