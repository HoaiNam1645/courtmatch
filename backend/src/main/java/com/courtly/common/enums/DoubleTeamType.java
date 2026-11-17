package com.courtly.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Loai doi: doi nam, doi nu hoac doi nam nu. */
public enum DoubleTeamType implements PersistableEnum {

    MENS("mens"),
    WOMENS("womens"),
    MIXED("mixed");

    private final String value;

    DoubleTeamType(String value) {
        this.value = value;
    }

    @Override
    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static DoubleTeamType from(String value) {
        for (DoubleTeamType item : values()) {
            if (item.value.equals(value)) {
                return item;
            }
        }
        throw new IllegalArgumentException("Gia tri '" + value + "' khong hop le cho DoubleTeamType");
    }
}
