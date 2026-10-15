package com.courtly.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Trang thai don dat san. */
public enum BookingStatus implements PersistableEnum {

    PENDING_OWNER_CONFIRMATION("pending_owner_confirmation"),
    AWAITING_PAYMENT("awaiting_payment"),
    CONFIRMED("confirmed"),
    REJECTED("rejected"),
    CANCELLED("cancelled"),
    COMPLETED("completed"),
    EXPIRED("expired"),
    DISPUTED("disputed"),
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
