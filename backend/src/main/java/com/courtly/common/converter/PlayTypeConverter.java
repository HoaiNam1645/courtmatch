package com.courtly.common.converter;

import com.courtly.common.enums.PlayType;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class PlayTypeConverter extends AbstractEnumConverter<PlayType> {

    public PlayTypeConverter() {
        super(PlayType.class);
    }
}
