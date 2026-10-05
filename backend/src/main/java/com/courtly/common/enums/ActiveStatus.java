package com.courtly.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Trang thai bat/tat dung chung cho cau hinh. */
public enum ActiveStatus implements PersistableEnum {

    ACTIVE("active"),
    INACTIVE("inactive");

    private final String value;

    ActiveStatus(String value) {
        this.value = value;
    }

    @Override
    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static ActiveStatus from(String value) {
        for (ActiveStatus item : values()) {
            if (item.value.equals(value)) {
                return item;
            }
        }
        throw new IllegalArgumentException("Gia tri '" + value + "' khong hop le cho ActiveStatus");
    }
}
