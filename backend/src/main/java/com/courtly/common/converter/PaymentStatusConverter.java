package com.courtly.common.converter;

import com.courtly.common.enums.PaymentStatus;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class PaymentStatusConverter extends AbstractEnumConverter<PaymentStatus> {

    public PaymentStatusConverter() {
        super(PaymentStatus.class);
    }
}
