package xyz.morphia.query;


import com.mongodb.MongoException;
import org.bson.types.ObjectId;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import xyz.morphia.TestBase;
import xyz.morphia.annotations.Entity;
import xyz.morphia.annotations.Id;
import xyz.morphia.annotations.Indexed;
import xyz.morphia.utils.IndexType;

import static xyz.morphia.geo.GeoJson.point;


@Ignore("Defer fixing the geo tests until after the core is fixed")
public class TestGeoQueries extends TestBase {
    @Override
    public void setUp() {
        super.setUp();
        getMapper().map(Place.class);
    }

    @Test
    public void testGeoWithinPolygon() {
        getDatastore().ensureIndexes();
        final Place place1 = new Place("place1", new double[]{0, 1});
        getDatastore().save(place1);
        final Place found = getDatastore().find(Place.class)
                                          .field("loc")
                                          .within(Shape.polygon(point(0, 0), point(0, 5), point(2, 3), point(2, 0))).first();
        Assert.assertNotNull(found);
    }

    @Test
    public void testGeoWithinPolygon2() {
        getDatastore().ensureIndexes();
        final Place place1 = new Place("place1", new double[]{10, 1});
        getDatastore().save(place1);
        final Place found = getDatastore().find(Place.class)
                                          .field("loc")
                                          .within(Shape.polygon(point(0, 0), point(0, 5), point(2, 3), point(2, 0))).first();
        Assert.assertNull(found);
    }

    @Test
    public void testGeoWithinRadius() {
        getDatastore().ensureIndexes();
        final Place place1 = new Place("place1", new double[]{1, 1});
        getDatastore().save(place1);
        final Place found = getDatastore().find(Place.class)
                                          .field("loc")
                                          .within(Shape.center(point(0, 1), 1.1)).first();
        Assert.assertNotNull(found);
    }

    @Test
    public void testGeoWithinRadius2() {
        getDatastore().ensureIndexes();
        final Place place1 = new Place("place1", new double[]{1, 1});
        getDatastore().save(place1);
        final Place found = getDatastore().find(Place.class)
                                          .field("loc")
                                          .within(Shape.center(point(0.5, 0.5), 0.77)).first();
        Assert.assertNotNull(found);
    }

    @Test
    public void testGeoWithinRadiusSphere() {
        getDatastore().ensureIndexes();
        final Place place1 = new Place("place1", new double[]{1, 1});
        getDatastore().save(place1);
        final Place found = getDatastore().find(Place.class)
                                          .field("loc")
                                          .within(Shape.centerSphere(point(0, 1), 1)).first();
        Assert.assertNotNull(found);
    }

    @Test
    public void testNear() {
        getDatastore().ensureIndexes();
        final Place place1 = new Place("place1", new double[]{1, 1});
        getDatastore().save(place1);
        final Place found = getDatastore().find(Place.class)
                                          .field("loc")
                                          .near(0, 0).first();
        Assert.assertNotNull(found);
    }

    @Test
    public void testNearMaxDistance() {
        getDatastore().ensureIndexes();
        final Place place1 = new Place("place1", new double[]{1, 1});
        getDatastore().save(place1);
        final Place found = getDatastore().find(Place.class)
                                          .field("loc")
                                          .near(0, 0, 1.5).first();
        Assert.assertNotNull(found);
        final Place notFound = getDatastore().find(Place.class)
                                             .field("loc")
                                             .near(0, 0, 1).first();
        Assert.assertNull(notFound);
    }

    @Test
    public void testNearNoIndex() {
        final Place place1 = new Place("place1", new double[]{1, 1});
        getDatastore().save(place1);
        Place found = null;
        try {
            found = getDatastore().find(Place.class)
                                  .field("loc")
                                  .near(0, 0).first();
            Assert.assertFalse(true);
        } catch (MongoException e) {
            Assert.assertNull(found);
        }
    }

    @Test
    public void testWithinBox() {
        getDatastore().ensureIndexes();
        final Place place1 = new Place("place1", new double[]{1, 1});
        getDatastore().save(place1);
        final Place found = getDatastore().find(Place.class)
                                          .field("loc")
                                          .within(Shape.box(point(0, 0), point(2, 2))).first();
        Assert.assertNotNull(found);
    }

    @Test
    public void testWithinOutsideBox() {
        getDatastore().ensureIndexes();
        final Place place1 = new Place("place1", new double[]{1, 1});
        getDatastore().save(place1);
        final Place found = getDatastore().find(Place.class)
                                          .field("loc")
                                          .within(Shape.box(point(0, 0), point(.4, .5))).first();
        Assert.assertNull(found);
    }

    @Test
    public void testWithinOutsideRadius() {
        getDatastore().ensureIndexes();
        final Place place1 = new Place("place1", new double[]{1, 1});
        getDatastore().save(place1);
        final Place found = getDatastore().find(Place.class)
                                          .field("loc")
                                          .within(Shape.center(point(2, 2), .4)).first();
        Assert.assertNull(found);
    }

    @Test
    public void testWithinRadius() {
        getDatastore().ensureIndexes();
        final Place place1 = new Place("place1", new double[]{1, 1});
        getDatastore().save(place1);
        final Place found = getDatastore().find(Place.class)
                                          .field("loc")
                                          .within(Shape.center(point(0, 1), 1.1)).first();
        Assert.assertNotNull(found);
    }

    @Test
    public void testWithinRadius2() {
        getDatastore().ensureIndexes();
        final Place place1 = new Place("place1", new double[]{1, 1});
        getDatastore().save(place1);
        final Place found = getDatastore().find(Place.class)
                                          .field("loc")
                                          .within(Shape.center(point(0.5, 0.5), 0.77)).first();
        Assert.assertNotNull(found);
    }

    @Test
    public void testWithinRadiusSphere() {
        getDatastore().ensureIndexes();
        final Place place1 = new Place("place1", new double[]{1, 1});
        getDatastore().save(place1);
        final Place found = getDatastore().find(Place.class)
                                          .field("loc")
                                          .within(Shape.centerSphere(point(0, 1), 1)).first();
        Assert.assertNotNull(found);
    }

    @Entity
    private static class Place {
        @Id
        private ObjectId id;
        private String name;
        @Indexed(IndexType.GEO2D)
        private double[] loc;

        private Place() {
        }

        Place(final String name, final double[] loc) {
            this.name = name;
            this.loc = loc;
        }
    }
}
