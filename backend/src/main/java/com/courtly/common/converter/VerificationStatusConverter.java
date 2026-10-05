package com.courtly.common.converter;

import com.courtly.common.enums.VerificationStatus;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class VerificationStatusConverter extends AbstractEnumConverter<VerificationStatus> {

    public VerificationStatusConverter() {
        super(VerificationStatus.class);
    }
}
