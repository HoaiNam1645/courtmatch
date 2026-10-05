package com.courtly.common.converter;

import com.courtly.common.enums.ResetChannel;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ResetChannelConverter extends AbstractEnumConverter<ResetChannel> {

    public ResetChannelConverter() {
        super(ResetChannel.class);
    }
}
