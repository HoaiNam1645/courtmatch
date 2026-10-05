package com.courtly.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Trang thai yeu cau ghep cap. */
public enum PartnerRequestStatus implements PersistableEnum {

    PENDING("pending"),
    ACCEPTED("accepted"),
    REJECTED("rejected"),
    CANCELLED("cancelled"),
    EXPIRED("expired");

    private final String value;

    PartnerRequestStatus(String value) {
        this.value = value;
    }

    @Override
    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static PartnerRequestStatus from(String value) {
        for (PartnerRequestStatus item : values()) {
            if (item.value.equals(value)) {
                return item;
            }
        }
        throw new IllegalArgumentException("Gia tri '" + value + "' khong hop le cho PartnerRequestStatus");
    }
}
