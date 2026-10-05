package com.courtly.common.converter;

import com.courtly.common.enums.JoinedStatus;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class JoinedStatusConverter extends AbstractEnumConverter<JoinedStatus> {

    public JoinedStatusConverter() {
        super(JoinedStatus.class);
    }
}
