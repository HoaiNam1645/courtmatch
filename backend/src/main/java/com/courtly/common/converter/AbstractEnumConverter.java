package com.courtly.common.converter;

import com.courtly.common.enums.PersistableEnum;
import jakarta.persistence.AttributeConverter;

/**
 * Chuyen doi hai chieu giua enum Java (HANG_UPPERCASE) va gia tri luu trong
 * database (lowercase). Moi enum co mot lop con gan {@code @Converter(autoApply = true)}
 * nen cac entity khong can khai bao {@code @Enumerated} hay {@code @Convert}.
 */
public abstract class AbstractEnumConverter<E extends Enum<E> & PersistableEnum>
        implements AttributeConverter<E, String> {

    private final Class<E> enumType;

    protected AbstractEnumConverter(Class<E> enumType) {
        this.enumType = enumType;
    }

    @Override
    public String convertToDatabaseColumn(E attribute) {
        return attribute == null ? null : attribute.getValue();
    }

    @Override
    public E convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        for (E constant : enumType.getEnumConstants()) {
            if (constant.getValue().equals(dbData)) {
                return constant;
            }
        }
        throw new IllegalArgumentException(
                "Gia tri '" + dbData + "' khong hop le cho " + enumType.getSimpleName());
    }
}
