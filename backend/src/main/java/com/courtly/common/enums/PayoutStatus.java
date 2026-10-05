package com.courtly.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Trang thai giao dich chi tra. */
public enum PayoutStatus implements PersistableEnum {

    PENDING("pending"),
    SUCCESS("success"),
    FAILED("failed");

    private final String value;

    PayoutStatus(String value) {
        this.value = value;
    }

    @Override
    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static PayoutStatus from(String value) {
        for (PayoutStatus item : values()) {
            if (item.value.equals(value)) {
                return item;
            }
        }
        throw new IllegalArgumentException("Gia tri '" + value + "' khong hop le cho PayoutStatus");
    }
}
