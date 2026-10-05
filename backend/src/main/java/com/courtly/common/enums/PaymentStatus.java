package com.courtly.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Trang thai giao dich thanh toan. */
public enum PaymentStatus implements PersistableEnum {

    PENDING("pending"),
    PAID("paid"),
    FAILED("failed"),
    EXPIRED("expired"),
    REFUNDED("refunded");

    private final String value;

    PaymentStatus(String value) {
        this.value = value;
    }

    @Override
    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static PaymentStatus from(String value) {
        for (PaymentStatus item : values()) {
            if (item.value.equals(value)) {
                return item;
            }
        }
        throw new IllegalArgumentException("Gia tri '" + value + "' khong hop le cho PaymentStatus");
    }
}
