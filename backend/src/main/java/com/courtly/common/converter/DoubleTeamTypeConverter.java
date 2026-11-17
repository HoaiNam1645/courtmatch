package com.courtly.common.converter;

import com.courtly.common.enums.DoubleTeamType;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class DoubleTeamTypeConverter extends AbstractEnumConverter<DoubleTeamType> {

    public DoubleTeamTypeConverter() {
        super(DoubleTeamType.class);
    }
}
