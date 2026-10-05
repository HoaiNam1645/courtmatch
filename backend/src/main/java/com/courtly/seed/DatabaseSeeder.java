package com.courtly.seed;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Chay toan bo seeder khi {@code courtly.seed.enabled=true}
 * (mac dinh bat o profile dev, tat o prod).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "courtly.seed", name = "enabled", havingValue = "true")
public class DatabaseSeeder implements ApplicationRunner {

    /** Spring tu sap xep danh sach theo {@code @Order} cua tung seeder. */
    private final List<Seeder> seeders;

    @Override
    public void run(ApplicationArguments args) {
        log.info("=== Bat dau nap du lieu mau ({} buoc) ===", seeders.size());
        long startedAt = System.currentTimeMillis();

        for (Seeder seeder : seeders) {
            long stepStart = System.currentTimeMillis();
            seeder.seed();
            log.info("  [{}] xong trong {} ms", seeder.name(), System.currentTimeMillis() - stepStart);
        }

        log.info("=== Nap du lieu mau hoan tat trong {} ms ===", System.currentTimeMillis() - startedAt);
    }
}
