package com.courtly.seed;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Sinh UUID on dinh tu mot khoa chuoi.
 *
 * <p>Nho vay chay lai seeder nhieu lan van ra cung bo id, va frontend co the
 * doi chieu du lieu mau voi cac id cu trong {@code public/api/home.json}
 * (venue-01 ... venue-06) trong giai doan chuyen tu mock sang API that.
 */
public final class SeedIds {

    private static final String NAMESPACE = "courtly:";

    private SeedIds() {
    }

    public static UUID of(String key) {
        return UUID.nameUUIDFromBytes((NAMESPACE + key).getBytes(StandardCharsets.UTF_8));
    }
}
