package com.courtly.api.owner.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Tien ich cua san (venue_services).
 *
 * @param price null = khong niem yet gia, 0 = mien phi
 */
public record VenueServiceInput(

        @NotBlank(message = "Thieu ma tien ich")
        @Size(max = 80, message = "Ma tien ich khong hop le")
        String code,

        @DecimalMin(value = "0", message = "Gia tien ich khong duoc am")
        @DecimalMax(value = "10000000", message = "Gia tien ich toi da 10.000.000d")
        BigDecimal price,

        @Size(max = 255, message = "Ghi chu toi da 255 ky tu")
        String note) {
}
