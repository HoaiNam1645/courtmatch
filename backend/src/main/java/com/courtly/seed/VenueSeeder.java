package com.courtly.seed;

import com.courtly.common.enums.ApprovalStatus;
import com.courtly.common.enums.BlockStatus;
import com.courtly.common.enums.CourtStatus;
import com.courtly.common.enums.VenueStatus;
import com.courtly.domain.account.User;
import com.courtly.domain.account.UserRepository;
import com.courtly.domain.venue.Court;
import com.courtly.domain.venue.CourtBlock;
import com.courtly.domain.venue.CourtBlockRepository;
import com.courtly.domain.venue.CourtPriceRule;
import com.courtly.domain.venue.FavoriteVenue;
import com.courtly.domain.venue.FavoriteVenueRepository;
import com.courtly.domain.venue.Service;
import com.courtly.domain.venue.ServiceRepository;
import com.courtly.domain.venue.Venue;
import com.courtly.domain.venue.VenueImage;
import com.courtly.domain.venue.VenueOperatingHours;
import com.courtly.domain.venue.VenueRepository;
import com.courtly.domain.venue.VenueService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Buoc 3: dich vu, dia diem san, san con, anh, gio mo cua va bang gia. */
@Slf4j
@Component
@Order(3)
@RequiredArgsConstructor
public class VenueSeeder implements Seeder {

    private static final String DATA_FILE = "seed/venues.json";

    private final VenueRepository venueRepository;
    private final ServiceRepository serviceRepository;
    private final UserRepository userRepository;
    private final CourtBlockRepository courtBlockRepository;
    private final FavoriteVenueRepository favoriteVenueRepository;
    private final ObjectMapper objectMapper;

    @Override
    public String name() {
        return "venues + courts + prices";
    }

    @Override
    @Transactional
    public void seed() {
        if (venueRepository.count() > 0) {
            log.info("  [venues] da co du lieu, bo qua");
            return;
        }

        VenueSeedData data = readSeedFile();
        Map<String, Service> services = seedServices(data.services());

        int courtCount = 0;
        for (VenueSeedData.VenueSeed seed : data.venues()) {
            Venue venue = buildVenue(seed);

            for (VenueSeedData.OperatingHoursSeed hoursSeed : seed.operatingHours()) {
                VenueOperatingHours hours = buildOperatingHours(seed, hoursSeed);
                hours.setVenue(venue);
                venue.getOperatingHours().add(hours);
            }

            for (VenueSeedData.ImageSeed imageSeed : seed.images()) {
                VenueImage image = buildImage(imageSeed);
                image.setVenue(venue);
                venue.getImages().add(image);
            }

            seed.services().forEach(item -> {
                Service service = services.get(item.code());
                if (service == null) {
                    throw new IllegalStateException("Khong tim thay dich vu '" + item.code() + "'");
                }
                venue.getVenueServices().add(new VenueService(venue, service, item.price(), item.note()));
            });

            for (VenueSeedData.CourtSeed courtSeed : seed.courts()) {
                Court court = buildCourt(courtSeed);
                court.setVenue(venue);
                venue.getCourts().add(court);
                courtCount++;
            }

            venueRepository.save(venue);
        }

        seedCourtBlock();
        seedFavorites();

        log.info("  [venues] {} dia diem, {} san con, {} dich vu",
                data.venues().size(), courtCount, services.size());
    }

    private VenueSeedData readSeedFile() {
        try (InputStream input = new ClassPathResource(DATA_FILE).getInputStream()) {
            return objectMapper.readValue(input, VenueSeedData.class);
        } catch (IOException e) {
            throw new IllegalStateException("Khong doc duoc file du lieu mau " + DATA_FILE, e);
        }
    }

    private Map<String, Service> seedServices(List<VenueSeedData.ServiceSeed> seeds) {
        Map<String, Service> result = new HashMap<>();
        for (VenueSeedData.ServiceSeed seed : seeds) {
            Service service = new Service(seed.code(), seed.name(), seed.description());
            service.setId(SeedIds.of("service:" + seed.code()));
            result.put(seed.code(), serviceRepository.save(service));
        }
        return result;
    }

    private Venue buildVenue(VenueSeedData.VenueSeed seed) {
        User owner = userRepository.findById(SeedIds.of("user:" + seed.ownerKey()))
                .orElseThrow(() -> new IllegalStateException("Thieu chu san '" + seed.ownerKey()
                        + "'. UserSeeder phai chay truoc."));

        Venue venue = new Venue();
        venue.setId(SeedIds.of("venue:" + seed.key()));
        venue.setOwner(owner);
        venue.setName(seed.name());
        venue.setSlug(seed.slug());
        venue.setDescription(seed.description());
        venue.setAddress(seed.address());
        venue.setDistrict(seed.district());
        venue.setProvince(seed.province());
        venue.setPhone(seed.phone());
        venue.setEmail(seed.email());
        venue.setLatitude(BigDecimal.valueOf(seed.latitude()));
        venue.setLongitude(BigDecimal.valueOf(seed.longitude()));
        venue.setStatus(VenueStatus.ACTIVE);
        venue.setApprovalStatus(ApprovalStatus.APPROVED);
        // Diem trung binh se duoc ReviewSeeder tinh lai tu venue_reviews thuc te.
        venue.setAverageRating(seed.averageRating());
        venue.setReviewCount(seed.reviewCount());
        venue.setCreatedAt(Instant.now().minus(60, ChronoUnit.DAYS));
        return venue;
    }

    private VenueOperatingHours buildOperatingHours(VenueSeedData.VenueSeed venueSeed,
                                                    VenueSeedData.OperatingHoursSeed seed) {
        VenueOperatingHours hours = new VenueOperatingHours();
        hours.setId(SeedIds.of("operating-hours:" + venueSeed.key() + ":" + seed.dayOfWeek()));
        hours.setDayOfWeek(seed.dayOfWeek());
        hours.setOpenTime(LocalTime.parse(seed.openTime()));
        hours.setCloseTime(LocalTime.parse(seed.closeTime()));
        hours.setClosed(seed.closed());
        return hours;
    }

    private VenueImage buildImage(VenueSeedData.ImageSeed seed) {
        VenueImage image = new VenueImage();
        image.setId(SeedIds.of("venue-image:" + seed.key()));
        image.setImageUrl(seed.imageUrl());
        image.setCaption(seed.caption());
        image.setDisplayOrder(seed.displayOrder());
        image.setCover(seed.cover());
        return image;
    }

    private Court buildCourt(VenueSeedData.CourtSeed seed) {
        Court court = new Court();
        court.setId(SeedIds.of("court:" + seed.key()));
        court.setName(seed.name());
        court.setCourtCode(seed.courtCode());
        court.setCourtType(seed.courtType());
        court.setSurfaceType(seed.surfaceType());
        court.setIndoor(seed.indoor());
        court.setStatus(CourtStatus.ACTIVE);

        int index = 0;
        for (VenueSeedData.PriceRuleSeed ruleSeed : seed.priceRules()) {
            CourtPriceRule rule = new CourtPriceRule();
            rule.setId(SeedIds.of("price-rule:" + seed.key() + ":" + index++));
            rule.setDayOfWeek(ruleSeed.dayOfWeek());
            rule.setStartTime(LocalTime.parse(ruleSeed.startTime()));
            rule.setEndTime(LocalTime.parse(ruleSeed.endTime()));
            rule.setPricePerHour(ruleSeed.pricePerHour());
            rule.setCourt(court);
            court.getPriceRules().add(rule);
        }
        return court;
    }

    /** Mot khung gio bi khoa de bao tri, dung kiem thu luong tinh lich trong (2.3.10). */
    private void seedCourtBlock() {
        Venue venue = venueRepository.findById(SeedIds.of("venue:venue-01")).orElseThrow();
        Court court = venue.getCourts().stream()
                .filter(c -> c.getCourtCode().equals("VIP"))
                .findFirst()
                .orElseThrow();

        Instant start = Instant.now().plus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);

        CourtBlock block = new CourtBlock();
        block.setId(SeedIds.of("court-block:venue-01-vip"));
        block.setCourt(court);
        block.setStartTime(start);
        block.setEndTime(start.plus(4, ChronoUnit.HOURS));
        block.setReason("Bao tri mat san dinh ky");
        block.setStatus(BlockStatus.ACTIVE);
        block.setCreatedBy(venue.getOwner());
        courtBlockRepository.save(block);
    }

    private void seedFavorites() {
        List<String> pairs = List.of("player-01:venue-01", "player-01:venue-04",
                "player-02:venue-02", "player-03:venue-03");

        for (String pair : pairs) {
            String[] parts = pair.split(":");
            User user = userRepository.findById(SeedIds.of("user:" + parts[0])).orElseThrow();
            Venue venue = venueRepository.findById(SeedIds.of("venue:" + parts[1])).orElseThrow();
            favoriteVenueRepository.save(new FavoriteVenue(user, venue));
        }
    }
}
