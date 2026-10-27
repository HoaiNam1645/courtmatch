package com.courtly.common.converter;

import com.courtly.common.enums.ModelStatus;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ModelStatusConverter extends AbstractEnumConverter<ModelStatus> {

    public ModelStatusConverter() {
        super(ModelStatus.class);
    }
}
