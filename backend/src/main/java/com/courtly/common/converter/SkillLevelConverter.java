package com.courtly.common.converter;

import com.courtly.common.enums.SkillLevel;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class SkillLevelConverter extends AbstractEnumConverter<SkillLevel> {

    public SkillLevelConverter() {
        super(SkillLevel.class);
    }
}
