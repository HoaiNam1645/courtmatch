package com.courtly.common.converter;

import com.courtly.common.enums.ModelType;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ModelTypeConverter extends AbstractEnumConverter<ModelType> {

    public ModelTypeConverter() {
        super(ModelType.class);
    }
}
