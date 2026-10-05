package com.courtly.common.converter;

import com.courtly.common.enums.ReportStatus;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ReportStatusConverter extends AbstractEnumConverter<ReportStatus> {

    public ReportStatusConverter() {
        super(ReportStatus.class);
    }
}
