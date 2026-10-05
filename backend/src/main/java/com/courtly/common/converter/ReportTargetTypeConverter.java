package com.courtly.common.converter;

import com.courtly.common.enums.ReportTargetType;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ReportTargetTypeConverter extends AbstractEnumConverter<ReportTargetType> {

    public ReportTargetTypeConverter() {
        super(ReportTargetType.class);
    }
}
