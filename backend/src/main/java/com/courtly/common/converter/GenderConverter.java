package com.courtly.common.converter;

import com.courtly.common.enums.Gender;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class GenderConverter extends AbstractEnumConverter<Gender> {

    public GenderConverter() {
        super(Gender.class);
    }
}
