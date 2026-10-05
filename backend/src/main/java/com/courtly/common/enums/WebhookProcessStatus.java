package com.courtly.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Trang thai xu ly webhook SePay. */
public enum WebhookProcessStatus implements PersistableEnum {

    RECEIVED("received"),
    PROCESSED("processed"),
    IGNORED("ignored"),
    FAILED("failed");

    private final String value;

    WebhookProcessStatus(String value) {
        this.value = value;
    }

    @Override
    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static WebhookProcessStatus from(String value) {
        for (WebhookProcessStatus item : values()) {
            if (item.value.equals(value)) {
                return item;
            }
        }
        throw new IllegalArgumentException("Gia tri '" + value + "' khong hop le cho WebhookProcessStatus");
    }
}
