package com.courtly.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Hinh thuc tran dau. */
public enum MatchType implements PersistableEnum {

    SINGLE("single"),
    DOUBLE("double"),
    MIXED_DOUBLE("mixed_double");

    private final String value;

    MatchType(String value) {
        this.value = value;
    }

    @Override
    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static MatchType from(String value) {
        for (MatchType item : values()) {
            if (item.value.equals(value)) {
                return item;
            }
        }
        throw new IllegalArgumentException("Gia tri '" + value + "' khong hop le cho MatchType");
    }
}
