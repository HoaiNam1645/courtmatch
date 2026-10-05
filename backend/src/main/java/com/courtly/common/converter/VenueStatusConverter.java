package com.courtly.common.converter;

import com.courtly.common.enums.VenueStatus;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class VenueStatusConverter extends AbstractEnumConverter<VenueStatus> {

    public VenueStatusConverter() {
        super(VenueStatus.class);
    }
}
