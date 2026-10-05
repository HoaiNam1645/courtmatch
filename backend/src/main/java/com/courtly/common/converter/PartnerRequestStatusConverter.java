package com.courtly.common.converter;

import com.courtly.common.enums.PartnerRequestStatus;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class PartnerRequestStatusConverter extends AbstractEnumConverter<PartnerRequestStatus> {

    public PartnerRequestStatusConverter() {
        super(PartnerRequestStatus.class);
    }
}
