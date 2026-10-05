package com.courtly.common.converter;

import com.courtly.common.enums.BalanceTransactionType;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class BalanceTransactionTypeConverter extends AbstractEnumConverter<BalanceTransactionType> {

    public BalanceTransactionTypeConverter() {
        super(BalanceTransactionType.class);
    }
}
