package com.courtly.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Kenh gui ma dat lai mat khau. */
public enum ResetChannel implements PersistableEnum {

    EMAIL("email"),
    PHONE("phone");

    private final String value;

    ResetChannel(String value) {
        this.value = value;
    }

    @Override
    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static ResetChannel from(String value) {
        for (ResetChannel item : values()) {
            if (item.value.equals(value)) {
                return item;
            }
        }
        throw new IllegalArgumentException("Gia tri '" + value + "' khong hop le cho ResetChannel");
    }
}
