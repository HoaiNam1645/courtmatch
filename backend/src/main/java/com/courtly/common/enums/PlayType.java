package com.courtly.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Hinh thuc choi uu tien. */
public enum PlayType implements PersistableEnum {

    SINGLE("single"),
    DOUBLE("double"),
    MIXED("mixed");

    private final String value;

    PlayType(String value) {
        this.value = value;
    }

    @Override
    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static PlayType from(String value) {
        for (PlayType item : values()) {
            if (item.value.equals(value)) {
                return item;
            }
        }
        throw new IllegalArgumentException("Gia tri '" + value + "' khong hop le cho PlayType");
    }
}
