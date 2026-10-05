package com.courtly.common.converter;

import com.courtly.common.enums.ReviewStatus;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ReviewStatusConverter extends AbstractEnumConverter<ReviewStatus> {

    public ReviewStatusConverter() {
        super(ReviewStatus.class);
    }
}
