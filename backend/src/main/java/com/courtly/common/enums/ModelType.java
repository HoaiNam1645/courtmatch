package com.courtly.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Loai model dung cho xep hang doi tac hoac can bang doi. */
public enum ModelType implements PersistableEnum {

    CATBOOST_RANKER("catboost_ranker"),
    BASELINE_RANKER("baseline_ranker"),
    DOUBLE_TEAM_RANKER("double_team_ranker");

    private final String value;

    ModelType(String value) {
        this.value = value;
    }

    @Override
    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static ModelType from(String value) {
        for (ModelType item : values()) {
            if (item.value.equals(value)) {
                return item;
            }
        }
        throw new IllegalArgumentException("Gia tri '" + value + "' khong hop le cho ModelType");
    }
}
