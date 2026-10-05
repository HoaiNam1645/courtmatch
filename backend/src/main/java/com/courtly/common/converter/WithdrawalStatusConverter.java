package com.courtly.common.converter;

import com.courtly.common.enums.WithdrawalStatus;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class WithdrawalStatusConverter extends AbstractEnumConverter<WithdrawalStatus> {

    public WithdrawalStatusConverter() {
        super(WithdrawalStatus.class);
    }
}
