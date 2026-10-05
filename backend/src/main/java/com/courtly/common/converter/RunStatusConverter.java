package com.courtly.common.converter;

import com.courtly.common.enums.RunStatus;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class RunStatusConverter extends AbstractEnumConverter<RunStatus> {

    public RunStatusConverter() {
        super(RunStatus.class);
    }
}
