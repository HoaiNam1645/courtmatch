package com.courtly.api.booking.dto;

import com.courtly.common.enums.PersistableEnum;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Trang thai mot khung gio (2.1.26).
 *
 * <p>Ba gia tri dau khop voi giao dien dang co. {@code past} duoc them vi khung gio
 * da troi qua trong ngay hom nay thi khong dat duoc, khac voi bi nguoi khac dat.
 */
public enum SlotStatus implements PersistableEnum {

    AVAILABLE("available"),
    BOOKED("booked"),
    BLOCKED("blocked"),
    PAST("past");

    private final String value;

    SlotStatus(String value) {
        this.value = value;
    }

    @Override
    @JsonValue
    public String getValue() {
        return value;
    }
}
