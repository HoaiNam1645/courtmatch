package com.courtly.common.converter;

import com.courtly.common.enums.SuggestionSource;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class SuggestionSourceConverter extends AbstractEnumConverter<SuggestionSource> {

    public SuggestionSourceConverter() {
        super(SuggestionSource.class);
    }
}
