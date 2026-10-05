package com.courtly.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Doi tuong bi bao cao. */
public enum ReportTargetType implements PersistableEnum {

    USER("user"),
    VENUE("venue"),
    VENUE_REVIEW("venue_review"),
    PARTNER_REVIEW("partner_review");

    private final String value;

    ReportTargetType(String value) {
        this.value = value;
    }

    @Override
    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static ReportTargetType from(String value) {
        for (ReportTargetType item : values()) {
            if (item.value.equals(value)) {
                return item;
            }
        }
        throw new IllegalArgumentException("Gia tri '" + value + "' khong hop le cho ReportTargetType");
    }
}
