package com.courtly.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Phong cach choi, khop voi playingStyleOptions o giao dien. */
public enum PlayingStyle implements PersistableEnum {

    ATTACKING("attacking"),
    BALANCED("balanced"),
    DEFENSIVE("defensive");

    private final String value;

    PlayingStyle(String value) {
        this.value = value;
    }

    @Override
    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static PlayingStyle from(String value) {
        for (PlayingStyle item : values()) {
            if (item.value.equals(value)) {
                return item;
            }
        }
        throw new IllegalArgumentException("Gia tri '" + value + "' khong hop le cho PlayingStyle");
    }
}
