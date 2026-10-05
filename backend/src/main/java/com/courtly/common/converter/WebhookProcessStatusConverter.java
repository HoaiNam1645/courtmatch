package com.courtly.common.converter;

import com.courtly.common.enums.WebhookProcessStatus;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class WebhookProcessStatusConverter extends AbstractEnumConverter<WebhookProcessStatus> {

    public WebhookProcessStatusConverter() {
        super(WebhookProcessStatus.class);
    }
}
