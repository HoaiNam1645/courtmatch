package com.courtly.common.converter;

import com.courtly.common.enums.ActiveStatus;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ActiveStatusConverter extends AbstractEnumConverter<ActiveStatus> {

    public ActiveStatusConverter() {
        super(ActiveStatus.class);
    }
}
