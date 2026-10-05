package com.courtly.common.converter;

import com.courtly.common.enums.DominantHand;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class DominantHandConverter extends AbstractEnumConverter<DominantHand> {

    public DominantHandConverter() {
        super(DominantHand.class);
    }
}
