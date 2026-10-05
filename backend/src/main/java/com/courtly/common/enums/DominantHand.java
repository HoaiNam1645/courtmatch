package com.courtly.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Tay thuan. */
public enum DominantHand implements PersistableEnum {

    LEFT("left"),
    RIGHT("right"),
    BOTH("both");

    private final String value;

    DominantHand(String value) {
        this.value = value;
    }

    @Override
    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static DominantHand from(String value) {
        for (DominantHand item : values()) {
            if (item.value.equals(value)) {
                return item;
            }
        }
        throw new IllegalArgumentException("Gia tri '" + value + "' khong hop le cho DominantHand");
    }
}
