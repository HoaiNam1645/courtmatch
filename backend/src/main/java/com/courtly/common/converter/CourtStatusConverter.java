package com.courtly.common.converter;

import com.courtly.common.enums.CourtStatus;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class CourtStatusConverter extends AbstractEnumConverter<CourtStatus> {

    public CourtStatusConverter() {
        super(CourtStatus.class);
    }
}
