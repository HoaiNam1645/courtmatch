package com.courtly.common.converter;

import com.courtly.common.enums.BlockStatus;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class BlockStatusConverter extends AbstractEnumConverter<BlockStatus> {

    public BlockStatusConverter() {
        super(BlockStatus.class);
    }
}
