package com.courtly.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Trang thai tung khung gio trong don. */
public enum BookingItemStatus implements PersistableEnum {

    PENDING("pending"),
    CONFIRMED("confirmed"),
    CANCELLED("cancelled"),
    COMPLETED("completed"),
    EXPIRED("expired");

    private final String value;

    BookingItemStatus(String value) {
        this.value = value;
    }

    @Override
    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static BookingItemStatus from(String value) {
        for (BookingItemStatus item : values()) {
            if (item.value.equals(value)) {
                return item;
            }
        }
        throw new IllegalArgumentException("Gia tri '" + value + "' khong hop le cho BookingItemStatus");
    }
}
