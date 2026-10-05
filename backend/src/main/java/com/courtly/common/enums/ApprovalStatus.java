package com.courtly.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Trang thai duyet dia diem san boi admin. */
public enum ApprovalStatus implements PersistableEnum {

    PENDING("pending"),
    APPROVED("approved"),
    REJECTED("rejected");

    private final String value;

    ApprovalStatus(String value) {
        this.value = value;
    }

    @Override
    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static ApprovalStatus from(String value) {
        for (ApprovalStatus item : values()) {
            if (item.value.equals(value)) {
                return item;
            }
        }
        throw new IllegalArgumentException("Gia tri '" + value + "' khong hop le cho ApprovalStatus");
    }
}
