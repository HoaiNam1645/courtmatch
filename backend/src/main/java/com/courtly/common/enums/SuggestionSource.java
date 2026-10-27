package com.courtly.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Nguon cham diem cua de xuat doi tac. */
public enum SuggestionSource implements PersistableEnum {

    AI("ai"),
    BASELINE("baseline");

    private final String value;

    SuggestionSource(String value) {
        this.value = value;
    }

    @Override
    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static SuggestionSource from(String value) {
        for (SuggestionSource item : values()) {
            if (item.value.equals(value)) {
                return item;
            }
        }
        throw new IllegalArgumentException("Gia tri '" + value + "' khong hop le cho SuggestionSource");
    }
}
