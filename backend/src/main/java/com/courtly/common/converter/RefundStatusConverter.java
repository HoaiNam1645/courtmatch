package com.courtly.common.converter;

import com.courtly.common.enums.RefundStatus;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class RefundStatusConverter extends AbstractEnumConverter<RefundStatus> {

    public RefundStatusConverter() {
        super(RefundStatus.class);
    }
}
