package com.courtly.common;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

/** Tao doi tuong Point dung SRID 4326 de ghi vao cot geography. */
public final class GeoSupport {

    public static final int SRID_WGS84 = 4326;

    private static final GeometryFactory FACTORY =
            new GeometryFactory(new PrecisionModel(), SRID_WGS84);

    private GeoSupport() {
    }

    /** Luu y thu tu: PostGIS dung (longitude, latitude), khong phai (latitude, longitude). */
    public static Point point(double latitude, double longitude) {
        return FACTORY.createPoint(new Coordinate(longitude, latitude));
    }
}
