package com.courtly.common.converter;

import com.courtly.common.enums.MatchType;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class MatchTypeConverter extends AbstractEnumConverter<MatchType> {

    public MatchTypeConverter() {
        super(MatchType.class);
    }
}
