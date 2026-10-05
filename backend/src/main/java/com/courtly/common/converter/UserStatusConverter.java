package com.courtly.common.converter;

import com.courtly.common.enums.UserStatus;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class UserStatusConverter extends AbstractEnumConverter<UserStatus> {

    public UserStatusConverter() {
        super(UserStatus.class);
    }
}
