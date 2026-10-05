package com.courtly.common.converter;

import com.courtly.common.enums.BookingItemStatus;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class BookingItemStatusConverter extends AbstractEnumConverter<BookingItemStatus> {

    public BookingItemStatusConverter() {
        super(BookingItemStatus.class);
    }
}
