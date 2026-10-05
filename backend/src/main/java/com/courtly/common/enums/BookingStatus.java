package com.courtly.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Trang thai don dat san. */
public enum BookingStatus implements PersistableEnum {

    PENDING_PAYMENT("pending_payment"),
    CONFIRMED("confirmed"),
    CANCELLED("cancelled"),
    COMPLETED("completed"),
    EXPIRED("expired"),
    REFUNDED("refunded");

    private final String value;

    BookingStatus(String value) {
        this.value = value;
    }

    @Override
    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static BookingStatus from(String value) {
        for (BookingStatus item : values()) {
            if (item.value.equals(value)) {
                return item;
            }
        }
        throw new IllegalArgumentException("Gia tri '" + value + "' khong hop le cho BookingStatus");
    }
}
