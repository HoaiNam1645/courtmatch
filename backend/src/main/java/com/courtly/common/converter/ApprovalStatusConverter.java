package com.courtly.common.converter;

import com.courtly.common.enums.ApprovalStatus;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ApprovalStatusConverter extends AbstractEnumConverter<ApprovalStatus> {

    public ApprovalStatusConverter() {
        super(ApprovalStatus.class);
    }
}
