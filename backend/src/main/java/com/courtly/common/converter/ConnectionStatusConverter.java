package com.courtly.common.converter;

import com.courtly.common.enums.ConnectionStatus;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ConnectionStatusConverter extends AbstractEnumConverter<ConnectionStatus> {

    public ConnectionStatusConverter() {
        super(ConnectionStatus.class);
    }
}
