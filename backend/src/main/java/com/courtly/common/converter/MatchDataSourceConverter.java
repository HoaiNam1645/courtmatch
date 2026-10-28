package com.courtly.common.converter;

import com.courtly.common.enums.MatchDataSource;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class MatchDataSourceConverter extends AbstractEnumConverter<MatchDataSource> {

    public MatchDataSourceConverter() {
        super(MatchDataSource.class);
    }
}
