package com.courtly.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Loai thuat toan duoc chay. */
public enum AlgorithmType implements PersistableEnum {

    KMEANS("kmeans"),
    DBSCAN("dbscan"),
    PARTNER_MATCHING("partner_matching"),
    DOUBLE_TEAM_MATCHING("double_team_matching");

    private final String value;

    AlgorithmType(String value) {
        this.value = value;
    }

    @Override
    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static AlgorithmType from(String value) {
        for (AlgorithmType item : values()) {
            if (item.value.equals(value)) {
                return item;
            }
        }
        throw new IllegalArgumentException("Gia tri '" + value + "' khong hop le cho AlgorithmType");
    }
}
