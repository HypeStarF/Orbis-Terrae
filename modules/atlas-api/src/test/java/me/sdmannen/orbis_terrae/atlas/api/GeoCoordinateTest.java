package me.sdmannen.orbis_terrae.atlas.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

final class GeoCoordinateTest {
    @Test
    void acceptsBoundaryCoordinates() {
        GeoCoordinate coordinate = new GeoCoordinate(90.0, -180.0);
        assertEquals(90.0, coordinate.latitude());
        assertEquals(-180.0, coordinate.longitude());
    }

    @Test
    void rejectsInvalidLatitude() {
        assertThrows(IllegalArgumentException.class, () -> new GeoCoordinate(90.0001, 0.0));
    }

    @Test
    void rejectsInvalidLongitude() {
        assertThrows(IllegalArgumentException.class, () -> new GeoCoordinate(0.0, 180.0001));
    }
}
