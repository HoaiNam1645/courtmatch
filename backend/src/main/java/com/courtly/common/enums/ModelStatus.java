package com.courtly.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Trang thai vong doi cua mot phien ban model. */
public enum ModelStatus implements PersistableEnum {

    TRAINING("training"),
    READY("ready"),
    ACTIVE("active"),
    FAILED("failed"),
    ARCHIVED("archived");

    private final String value;

    ModelStatus(String value) {
        this.value = value;
    }

    @Override
    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static ModelStatus from(String value) {
        for (ModelStatus item : values()) {
            if (item.value.equals(value)) {
                return item;
            }
        }
        throw new IllegalArgumentException("Gia tri '" + value + "' khong hop le cho ModelStatus");
    }
}
