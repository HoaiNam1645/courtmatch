package com.courtly.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Trang thai xac minh chu san. */
public enum VerificationStatus implements PersistableEnum {

    PENDING("pending"),
    VERIFIED("verified"),
    REJECTED("rejected");

    private final String value;

    VerificationStatus(String value) {
        this.value = value;
    }

    @Override
    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static VerificationStatus from(String value) {
        for (VerificationStatus item : values()) {
            if (item.value.equals(value)) {
                return item;
            }
        }
        throw new IllegalArgumentException("Gia tri '" + value + "' khong hop le cho VerificationStatus");
    }
}
