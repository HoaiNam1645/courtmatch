package com.courtly.common.converter;

import com.courtly.common.enums.PlayingStyle;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class PlayingStyleConverter extends AbstractEnumConverter<PlayingStyle> {

    public PlayingStyleConverter() {
        super(PlayingStyle.class);
    }
}
