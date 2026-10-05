package com.courtly.common.converter;

import com.courtly.common.enums.SuggestionStatus;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class SuggestionStatusConverter extends AbstractEnumConverter<SuggestionStatus> {

    public SuggestionStatusConverter() {
        super(SuggestionStatus.class);
    }
}
