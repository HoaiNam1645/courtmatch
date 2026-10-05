package com.courtly.common.converter;

import com.courtly.common.enums.PayoutStatus;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class PayoutStatusConverter extends AbstractEnumConverter<PayoutStatus> {

    public PayoutStatusConverter() {
        super(PayoutStatus.class);
    }
}
