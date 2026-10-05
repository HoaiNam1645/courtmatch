package com.courtly.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Trang thai ket noi doi tac. */
public enum ConnectionStatus implements PersistableEnum {

    ACTIVE("active"),
    BLOCKED("blocked"),
    REMOVED("removed");

    private final String value;

    ConnectionStatus(String value) {
        this.value = value;
    }

    @Override
    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static ConnectionStatus from(String value) {
        for (ConnectionStatus item : values()) {
            if (item.value.equals(value)) {
                return item;
            }
        }
        throw new IllegalArgumentException("Gia tri '" + value + "' khong hop le cho ConnectionStatus");
    }
}
