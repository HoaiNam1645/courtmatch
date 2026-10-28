package com.courtly.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Nguon goc noi bo cua ban ghi tran dau. */
public enum MatchDataSource implements PersistableEnum {

    BOOKING("booking"),
    MANUAL("manual"),
    SEEDED("seeded");

    private final String value;

    MatchDataSource(String value) {
        this.value = value;
    }

    @Override
    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static MatchDataSource from(String value) {
        for (MatchDataSource item : values()) {
            if (item.value.equals(value)) {
                return item;
            }
        }
        throw new IllegalArgumentException("Gia tri '" + value + "' khong hop le cho MatchDataSource");
    }
}
