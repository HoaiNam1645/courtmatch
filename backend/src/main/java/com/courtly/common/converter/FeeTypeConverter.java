package com.courtly.common.converter;

import com.courtly.common.enums.FeeType;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class FeeTypeConverter extends AbstractEnumConverter<FeeType> {

    public FeeTypeConverter() {
        super(FeeType.class);
    }
}
