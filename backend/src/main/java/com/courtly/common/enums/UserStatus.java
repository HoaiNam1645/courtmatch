package com.courtly.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Trang thai tai khoan nguoi dung. */
public enum UserStatus implements PersistableEnum {

    ACTIVE("active"),
    INACTIVE("inactive"),
    BANNED("banned"),
    PENDING("pending");

    private final String value;

    UserStatus(String value) {
        this.value = value;
    }

    @Override
    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static UserStatus from(String value) {
        for (UserStatus item : values()) {
            if (item.value.equals(value)) {
                return item;
            }
        }
        throw new IllegalArgumentException("Gia tri '" + value + "' khong hop le cho UserStatus");
    }
}
