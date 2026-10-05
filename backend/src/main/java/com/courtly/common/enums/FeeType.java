package com.courtly.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Kieu tinh phi nen tang. */
public enum FeeType implements PersistableEnum {

    PERCENTAGE("percentage"),
    FIXED("fixed");

    private final String value;

    FeeType(String value) {
        this.value = value;
    }

    @Override
    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static FeeType from(String value) {
        for (FeeType item : values()) {
            if (item.value.equals(value)) {
                return item;
            }
        }
        throw new IllegalArgumentException("Gia tri '" + value + "' khong hop le cho FeeType");
    }
}
