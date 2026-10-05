package com.courtly.common.converter;

import com.courtly.common.enums.MatchStatus;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class MatchStatusConverter extends AbstractEnumConverter<MatchStatus> {

    public MatchStatusConverter() {
        super(MatchStatus.class);
    }
}
