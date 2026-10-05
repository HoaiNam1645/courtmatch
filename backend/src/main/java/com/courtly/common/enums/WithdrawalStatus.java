package com.courtly.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Trang thai yeu cau rut tien. */
public enum WithdrawalStatus implements PersistableEnum {

    PENDING("pending"),
    APPROVED("approved"),
    REJECTED("rejected"),
    PAID("paid"),
    CANCELLED("cancelled");

    private final String value;

    WithdrawalStatus(String value) {
        this.value = value;
    }

    @Override
    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static WithdrawalStatus from(String value) {
        for (WithdrawalStatus item : values()) {
            if (item.value.equals(value)) {
                return item;
            }
        }
        throw new IllegalArgumentException("Gia tri '" + value + "' khong hop le cho WithdrawalStatus");
    }
}
