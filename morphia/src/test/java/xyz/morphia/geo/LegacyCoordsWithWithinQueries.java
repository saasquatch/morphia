package xyz.morphia.geo;

import org.junit.Ignore;
import org.junit.Test;
import xyz.morphia.TestBase;
import xyz.morphia.query.Shape;

import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;
import static xyz.morphia.geo.GeoJson.point;

/**
 * Although this tests the old legacy coordinate system of storing location, this set of tests shows the functionality that's available
 * with
 * these coordinates in later versions of the server that also support GeoJSON.  In order to get full geo querying functionality, you
 * should
 * use GeoJSON for storing your location not legacy co-ordinates.
 * <p/>
 * This test requires server version 2.4 or above as it uses $geoWithin.
 */
@Ignore("Defer fixing the geo tests until after the core is fixed")
public class LegacyCoordsWithWithinQueries extends TestBase {
    @Test
    public void shouldNotReturnAnyPointsIfNothingInsideCircle() {
        final PlaceWithLegacyCoords point = new PlaceWithLegacyCoords(new double[]{1, 1}, "place");
        getDatastore().save(point);
        getDatastore().ensureIndexes();

        final PlaceWithLegacyCoords found = getDatastore().find(PlaceWithLegacyCoords.class)
                                                          .field("location")
                                                          .within(Shape.center(point(2, 2), 0.5)).first();
        assertThat(found, is(nullValue()));
    }

    @Test
    public void shouldNotReturnAnyValuesWhenTheQueryBoxDoesNotContainAnyPoints() {
        final PlaceWithLegacyCoords point = new PlaceWithLegacyCoords(new double[]{1, 1}, "place");
        getDatastore().save(point);
        getDatastore().ensureIndexes();

        final PlaceWithLegacyCoords found = getDatastore().find(PlaceWithLegacyCoords.class)
                                                          .field("location")
                                                          .within(Shape.box(point(0, 0), point(0.5, 0.5))).first();
        assertThat(found, is(nullValue()));
    }

    @Test
    public void shouldNotReturnAnyValuesWhenTheQueryPolygonDoesNotContainAnyPoints() {
        final PlaceWithLegacyCoords point = new PlaceWithLegacyCoords(new double[]{7.3, 9.2}, "place");
        getDatastore().save(point);
        getDatastore().ensureIndexes();

        final PlaceWithLegacyCoords found = getDatastore().find(PlaceWithLegacyCoords.class)
                                                          .field("location")
                                                          .within(Shape.polygon(point(0, 0),
                                                              point(0, 5),
                                                              point(2, 3),
                                                              point(1, 0))).first();
        assertThat(found, is(nullValue()));
    }

    @Test
    public void shouldReturnAPointThatIsFullyWithinQueryPolygon() {
        final PlaceWithLegacyCoords expectedPoint = new PlaceWithLegacyCoords(new double[]{1, 1}, "place");
        getDatastore().save(expectedPoint);
        getDatastore().ensureIndexes();

        final PlaceWithLegacyCoords found = getDatastore().find(PlaceWithLegacyCoords.class)
                                                          .field("location")
                                                          .within(Shape.polygon(point(0, 0),
                                                              point(0, 5),
                                                              point(2, 3),
                                                              point(1, 0))).first();
        assertThat(found, is(expectedPoint));
    }

    @Test
    public void shouldReturnOnlyThePointsWithinTheGivenCircle() {
        final PlaceWithLegacyCoords expectedPoint = new PlaceWithLegacyCoords(new double[]{1.1, 2.3}, "Near point");
        getDatastore().save(expectedPoint);
        final PlaceWithLegacyCoords otherPoint = new PlaceWithLegacyCoords(new double[]{3.1, 5.2}, "Further point");
        getDatastore().save(otherPoint);
        getDatastore().ensureIndexes();

        final List<PlaceWithLegacyCoords> found = getDatastore().find(PlaceWithLegacyCoords.class)
                                                                .field("location")
                                                                .within(Shape.center(point(1, 2), 1.1))
                                                                .asList();

        assertThat(found.size(), is(1));
        assertThat(found.get(0), is(expectedPoint));
    }

    @Test
    public void shouldReturnPointOnBoundaryOfQueryCircle() {
        final PlaceWithLegacyCoords expectedPoint = new PlaceWithLegacyCoords(new double[]{1, 1}, "place");
        getDatastore().save(expectedPoint);
        getDatastore().ensureIndexes();

        final PlaceWithLegacyCoords found = getDatastore().find(PlaceWithLegacyCoords.class)
                                                          .field("location")
                                                          .within(Shape.center(point(0, 1), 1)).first();
        assertThat(found, is(expectedPoint));
    }

    @Test
    public void shouldReturnPointOnBoundaryOfQueryCircleWithSphericalGeometry() {
        final PlaceWithLegacyCoords expectedPoint = new PlaceWithLegacyCoords(new double[]{1, 1}, "place");
        getDatastore().save(expectedPoint);
        getDatastore().ensureIndexes();

        final PlaceWithLegacyCoords found = getDatastore().find(PlaceWithLegacyCoords.class)
                                                          .field("location")
                                                          .within(Shape.centerSphere(point(0, 1), 1)).first();
        assertThat(found, is(expectedPoint));
    }

    @Test
    public void shouldReturnPointThatIsFullyInsideTheQueryBox() {
        final PlaceWithLegacyCoords expectedPoint = new PlaceWithLegacyCoords(new double[]{1, 1}, "place");
        getDatastore().save(expectedPoint);
        getDatastore().ensureIndexes();

        final PlaceWithLegacyCoords found = getDatastore().find(PlaceWithLegacyCoords.class)
                                                          .field("location")
                                                          .within(Shape.box(point(0, 0), point(2, 2))).first();
        assertThat(found, is(expectedPoint));
    }
}
