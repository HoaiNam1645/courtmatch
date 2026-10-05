package com.courtly.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Trang thai tran dau. */
public enum MatchStatus implements PersistableEnum {

    SCHEDULED("scheduled"),
    COMPLETED("completed"),
    CANCELLED("cancelled");

    private final String value;

    MatchStatus(String value) {
        this.value = value;
    }

    @Override
    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static MatchStatus from(String value) {
        for (MatchStatus item : values()) {
            if (item.value.equals(value)) {
                return item;
            }
        }
        throw new IllegalArgumentException("Gia tri '" + value + "' khong hop le cho MatchStatus");
    }
}
