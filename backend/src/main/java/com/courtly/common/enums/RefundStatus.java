package com.courtly.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Trang thai hoan tien. */
public enum RefundStatus implements PersistableEnum {

    REQUESTED("requested"),
    APPROVED("approved"),
    REJECTED("rejected"),
    PROCESSED("processed"),
    FAILED("failed");

    private final String value;

    RefundStatus(String value) {
        this.value = value;
    }

    @Override
    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static RefundStatus from(String value) {
        for (RefundStatus item : values()) {
            if (item.value.equals(value)) {
                return item;
            }
        }
        throw new IllegalArgumentException("Gia tri '" + value + "' khong hop le cho RefundStatus");
    }
}
