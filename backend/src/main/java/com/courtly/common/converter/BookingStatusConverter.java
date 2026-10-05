package com.courtly.common.converter;

import com.courtly.common.enums.BookingStatus;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class BookingStatusConverter extends AbstractEnumConverter<BookingStatus> {

    public BookingStatusConverter() {
        super(BookingStatus.class);
    }
}
