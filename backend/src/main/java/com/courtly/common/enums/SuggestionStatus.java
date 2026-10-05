package com.courtly.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Trang thai goi y doi tac. */
public enum SuggestionStatus implements PersistableEnum {

    ACTIVE("active"),
    HIDDEN("hidden"),
    EXPIRED("expired");

    private final String value;

    SuggestionStatus(String value) {
        this.value = value;
    }

    @Override
    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static SuggestionStatus from(String value) {
        for (SuggestionStatus item : values()) {
            if (item.value.equals(value)) {
                return item;
            }
        }
        throw new IllegalArgumentException("Gia tri '" + value + "' khong hop le cho SuggestionStatus");
    }
}
