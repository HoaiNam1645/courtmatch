package com.courtly.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Loai bien dong so du chu san. */
public enum BalanceTransactionType implements PersistableEnum {

    EARNING("earning"),
    PLATFORM_FEE("platform_fee"),
    WITHDRAWAL("withdrawal"),
    REFUND_ADJUSTMENT("refund_adjustment"),
    MANUAL_ADJUSTMENT("manual_adjustment");

    private final String value;

    BalanceTransactionType(String value) {
        this.value = value;
    }

    @Override
    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static BalanceTransactionType from(String value) {
        for (BalanceTransactionType item : values()) {
            if (item.value.equals(value)) {
                return item;
            }
        }
        throw new IllegalArgumentException("Gia tri '" + value + "' khong hop le cho BalanceTransactionType");
    }
}
