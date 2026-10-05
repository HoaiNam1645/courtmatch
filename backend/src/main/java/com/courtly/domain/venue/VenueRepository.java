package com.courtly.domain.venue;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface VenueRepository extends JpaRepository<Venue, UUID> {

    Optional<Venue> findBySlug(String slug);

    /**
     * Dieu kien loc dung chung cho cau lay du lieu va cau dem.
     *
     * <p>Moi tham so deu cho phep NULL nghia la "khong loc theo tieu chi nay".
     * Phai CAST tham so vi PostgreSQL khong suy duoc kieu cua tham so NULL.
     *
     * <p>{@code availableOnly} (2.1.22) tinh tu lich that: lay cua so mo cua con lai cua
     * hom nay, tru di hop cac khoang da bi giu (booking_items pending/confirmed va
     * court_blocks). Con khe nao chua bi phu thi san duoc coi la con trong. Dung
     * {@code range_agg} de gop cac khoang chong lan nhau, thay vi cong don thoi luong -
     * cong don se dem trung cac khoang giao nhau.
     */
    String SEARCH_FILTER = """
            WHERE v.status = 'active'
              AND v.approval_status = 'approved'
              AND (CAST(:keyword AS text) IS NULL
                   OR courtly_unaccent(v.name) ILIKE courtly_unaccent(CAST(:keyword AS text))
                   OR courtly_unaccent(v.district) ILIKE courtly_unaccent(CAST(:keyword AS text))
                   OR courtly_unaccent(v.address) ILIKE courtly_unaccent(CAST(:keyword AS text)))
              AND (CAST(:district AS text) IS NULL OR v.district = CAST(:district AS text))
              AND (CAST(:minRating AS numeric) IS NULL OR v.average_rating >= CAST(:minRating AS numeric))
              AND (CAST(:lat AS double precision) IS NULL
                   OR ST_DWithin(v.location,
                                 ST_SetSRID(ST_MakePoint(CAST(:lng AS double precision),
                                                         CAST(:lat AS double precision)), 4326)::geography,
                                 CAST(:radiusMeters AS double precision)))
              AND (CAST(:maxPricePerHour AS numeric) IS NULL
                   OR EXISTS (SELECT 1 FROM courts c2
                              JOIN court_price_rules r2 ON r2.court_id = c2.id AND r2.status = 'active'
                              WHERE c2.venue_id = v.id AND c2.status = 'active'
                                AND r2.price_per_hour <= CAST(:maxPricePerHour AS numeric)))
              AND (CAST(:availableOnly AS boolean) IS NOT TRUE OR EXISTS (
                    SELECT 1
                    FROM courts c3
                    JOIN venue_operating_hours h ON h.venue_id = v.id
                         AND h.day_of_week = EXTRACT(isodow FROM (now() AT TIME ZONE 'Asia/Ho_Chi_Minh'))::smallint
                         AND NOT h.is_closed
                    CROSS JOIN LATERAL (
                        SELECT tstzrange(
                            GREATEST(((now() AT TIME ZONE 'Asia/Ho_Chi_Minh')::date + h.open_time)
                                     AT TIME ZONE 'Asia/Ho_Chi_Minh', now()),
                            ((now() AT TIME ZONE 'Asia/Ho_Chi_Minh')::date + h.close_time)
                                     AT TIME ZONE 'Asia/Ho_Chi_Minh') AS win
                    ) w
                    WHERE c3.venue_id = v.id AND c3.status = 'active' AND NOT isempty(w.win)
                      AND NOT (w.win <@ COALESCE((
                            SELECT range_agg(tstzrange(o.start_time, o.end_time))
                            FROM (
                                SELECT bi.start_time, bi.end_time FROM booking_items bi
                                WHERE bi.court_id = c3.id AND bi.status IN ('pending', 'confirmed')
                                UNION ALL
                                SELECT cb.start_time, cb.end_time FROM court_blocks cb
                                WHERE cb.court_id = c3.id AND cb.status = 'active'
                            ) o
                            WHERE tstzrange(o.start_time, o.end_time) && w.win
                      ), '{}'::tstzmultirange))
              ))
            """;

    /**
     * Tim kiem san (2.1.13) kem loc theo quan (2.1.19), ban kinh (2.1.14, 2.1.20),
     * gia (2.1.21) va diem danh gia.
     *
     * <p>Anh bia, so san con va khoang gia lay bang LATERAL JOIN trong cung mot cau,
     * thay vi goi them mot truy van cho moi san.
     *
     * <p>Co toa do thi sap theo khoang cach tang dan, khong thi sap theo diem danh gia.
     * Bieu thuc ST_Distance trong ORDER BY chi duoc tinh khi :lat khac NULL.
     *
     * @param keyword chuoi da boc san dang {@code %tu khoa%}, hoac NULL
     */
    @Query(value = """
            SELECT v.id                              AS "id",
                   v.slug                            AS "slug",
                   v.name                            AS "name",
                   v.district                        AS "district",
                   v.address                         AS "address",
                   v.phone                           AS "phone",
                   v.latitude                        AS "latitude",
                   v.longitude                       AS "longitude",
                   v.average_rating                  AS "averageRating",
                   v.review_count                    AS "reviewCount",
                   cover.image_url                   AS "coverImageUrl",
                   COALESCE(stats.court_count, 0)    AS "courtCount",
                   stats.min_price                   AS "minPricePerHour",
                   stats.max_price                   AS "maxPricePerHour",
                   hours.open_time                   AS "todayOpenTime",
                   hours.close_time                  AS "todayCloseTime",
                   COALESCE(hours.is_closed, false)  AS "closedToday",
                   CASE WHEN CAST(:lat AS double precision) IS NOT NULL
                        THEN ST_Distance(v.location,
                                         ST_SetSRID(ST_MakePoint(CAST(:lng AS double precision),
                                                                 CAST(:lat AS double precision)), 4326)::geography)
                   END                               AS "distanceMeters"
            FROM venues v
            LEFT JOIN LATERAL (
                SELECT i.image_url FROM venue_images i
                WHERE i.venue_id = v.id
                ORDER BY i.is_cover DESC, i.display_order
                LIMIT 1
            ) cover ON true
            LEFT JOIN LATERAL (
                SELECT count(DISTINCT c.id) AS court_count,
                       min(r.price_per_hour) AS min_price,
                       max(r.price_per_hour) AS max_price
                FROM courts c
                LEFT JOIN court_price_rules r ON r.court_id = c.id AND r.status = 'active'
                WHERE c.venue_id = v.id AND c.status = 'active'
            ) stats ON true
            LEFT JOIN venue_operating_hours hours
                   ON hours.venue_id = v.id AND hours.day_of_week = CAST(:dayOfWeek AS smallint)
            """ + SEARCH_FILTER + """
            ORDER BY CASE WHEN CAST(:lat AS double precision) IS NOT NULL
                          THEN ST_Distance(v.location,
                                           ST_SetSRID(ST_MakePoint(CAST(:lng AS double precision),
                                                                   CAST(:lat AS double precision)), 4326)::geography)
                     END ASC NULLS LAST,
                     v.average_rating DESC,
                     v.name ASC
            """,
            countQuery = "SELECT count(*) FROM venues v " + SEARCH_FILTER,
            nativeQuery = true)
    Page<VenueSummaryView> search(@Param("keyword") String keyword,
                                  @Param("district") String district,
                                  @Param("minRating") BigDecimal minRating,
                                  @Param("maxPricePerHour") BigDecimal maxPricePerHour,
                                  @Param("availableOnly") Boolean availableOnly,
                                  @Param("lat") Double latitude,
                                  @Param("lng") Double longitude,
                                  @Param("radiusMeters") Double radiusMeters,
                                  @Param("dayOfWeek") short dayOfWeek,
                                  Pageable pageable);

    /** Danh sach quan/huyen co san, dung dung danh cho modal bo loc (2.1.19). */
    @Query(value = """
            SELECT v.district AS "district", count(*) AS "venueCount"
            FROM venues v
            WHERE v.status = 'active' AND v.approval_status = 'approved' AND v.district IS NOT NULL
            GROUP BY v.district
            ORDER BY count(*) DESC, v.district
            """, nativeQuery = true)
    List<DistrictCountView> findDistrictsWithVenueCount();
}
