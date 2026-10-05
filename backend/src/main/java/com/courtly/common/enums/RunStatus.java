package com.courtly.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Trang thai mot lan chay thuat toan. */
public enum RunStatus implements PersistableEnum {

    RUNNING("running"),
    SUCCESS("success"),
    FAILED("failed");

    private final String value;

    RunStatus(String value) {
        this.value = value;
    }

    @Override
    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static RunStatus from(String value) {
        for (RunStatus item : values()) {
            if (item.value.equals(value)) {
                return item;
            }
        }
        throw new IllegalArgumentException("Gia tri '" + value + "' khong hop le cho RunStatus");
    }
}
