package com.courtly.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Trinh do ky nang nguoi choi. */
public enum SkillLevel implements PersistableEnum {

    BEGINNER("beginner"),
    INTERMEDIATE("intermediate"),
    ADVANCED("advanced"),
    PROFESSIONAL("professional");

    private final String value;

    SkillLevel(String value) {
        this.value = value;
    }

    @Override
    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static SkillLevel from(String value) {
        for (SkillLevel item : values()) {
            if (item.value.equals(value)) {
                return item;
            }
        }
        throw new IllegalArgumentException("Gia tri '" + value + "' khong hop le cho SkillLevel");
    }
}
