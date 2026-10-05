package com.courtly.common.converter;

import com.courtly.common.enums.AlgorithmType;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class AlgorithmTypeConverter extends AbstractEnumConverter<AlgorithmType> {

    public AlgorithmTypeConverter() {
        super(AlgorithmType.class);
    }
}
