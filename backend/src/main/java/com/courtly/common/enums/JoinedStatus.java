package com.courtly.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Trang thai tham gia tran dau. */
public enum JoinedStatus implements PersistableEnum {

    INVITED("invited"),
    ACCEPTED("accepted"),
    DECLINED("declined"),
    JOINED("joined");

    private final String value;

    JoinedStatus(String value) {
        this.value = value;
    }

    @Override
    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static JoinedStatus from(String value) {
        for (JoinedStatus item : values()) {
            if (item.value.equals(value)) {
                return item;
            }
        }
        throw new IllegalArgumentException("Gia tri '" + value + "' khong hop le cho JoinedStatus");
    }
}
