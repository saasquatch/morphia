package xyz.morphia.geo;

import com.mongodb.MongoException;
import org.junit.Ignore;
import org.junit.Test;
import xyz.morphia.TestBase;
import xyz.morphia.query.Query;

import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;
import static xyz.morphia.testutil.IndexMatcher.doesNotHaveIndexNamed;
import static xyz.morphia.testutil.IndexMatcher.hasIndexNamed;
import static xyz.morphia.testutil.JSONMatcher.jsonEqual;

/**
 * This test shows how to define an entity that uses the legacy co-ordinate pairs standard, which works with MongoDB server versions 2.2
 * and
 * earlier.  If you are using a server version higher than 2.2 (i.e. 2.4 and onwards) you should store location information as <a
 * href="http://docs.mongodb.org/manual/reference/glossary/#term-geojson">GeoJSON</a> and consult the documentation for indexes and queries
 * that work on this format.  Storing the location as GeoJSON gives you access to a wider range of queries.
 * <p/>
 * This set of tests should run on all server versions.
 */
@Ignore("Defer fixing the geo tests until after the core is fixed")
public class LegacyCoordsTest extends TestBase {
    @Test
    public void shouldCreateA2dIndexOnAnEntityWithArrayOfCoordinates() {
        PlaceWithLegacyCoords pointA = new PlaceWithLegacyCoords(new double[]{3.1, 5.2}, "Point A");
        getDatastore().save(pointA);
        getDatastore().ensureIndexes();
        assertThat(getDatastore().getCollection(PlaceWithLegacyCoords.class).listIndexes(), hasIndexNamed("location_2d"));
    }

    @Test
    public void shouldFindPointWithExactMatch() {
        // given
        final PlaceWithLegacyCoords nearbyPlace = new PlaceWithLegacyCoords(new double[]{1.1, 2.3}, "Nearby Place");
        getDatastore().save(nearbyPlace);
        getDatastore().ensureIndexes();

        // when
        List<PlaceWithLegacyCoords> found = getDatastore().find(PlaceWithLegacyCoords.class)
                                                          .field("location")
                                                          .equal(new double[]{1.1, 2.3})
                                                          .asList();

        // then
        assertThat(found, is(notNullValue()));
        assertThat(found.size(), is(1));
        assertThat(found.get(0), is(nearbyPlace));
    }

    @Test
    @SuppressWarnings("deprecation")
    public void shouldGenerateCorrectQueryForNearSphereWithRadius() {
        // when
        Query<PlaceWithLegacyCoords> query = getDatastore().find(PlaceWithLegacyCoords.class)
                                                           .field("location")
                                                           .near(42.08563, -87.99822, 2, true);

        // then
        assertThat(query.getQueryDocument().toString(),
                   jsonEqual("{ \"location\" : "
                             + "{ \"$nearSphere\" : [ 42.08563 , -87.99822] , "
                             + "\"$maxDistance\" : 2.0}}"));
    }

    @Test
    @SuppressWarnings("deprecation")
    public void shouldGenerateCorrectQueryForNearWithMaxDistance() {
        // when
        Query<PlaceWithLegacyCoords> query = getDatastore().find(PlaceWithLegacyCoords.class)
                                                           .field("location")
                                                           .near(42.08563, -87.99822, 2);

        // then
        assertThat(query.getQueryDocument().toString(),
                   jsonEqual("{ \"location\" : "
                             + "{ \"$near\" : [ 42.08563 , -87.99822] , "
                             + "\"$maxDistance\" : 2.0}}"));

    }

    @Test
    public void shouldNotReturnAnyResultsIfNoLocationsWithinGivenRadius() {
        // given
        final PlaceWithLegacyCoords nearbyPlace = new PlaceWithLegacyCoords(new double[]{1.1, 2.3}, "Nearby Place");
        getDatastore().save(nearbyPlace);
        getDatastore().ensureIndexes();

        // when
        Query<PlaceWithLegacyCoords> locationQuery = getDatastore().find(PlaceWithLegacyCoords.class)
                                                                   .field("location")
                                                                   .near(1.0, 2.0, 0.1);
        // then
        assertThat(locationQuery.asList().size(), is(0));
        assertThat(locationQuery.first(), is(nullValue()));
    }

    @Test
    public void shouldReturnAllLocationsOrderedByDistanceFromQueryLocationWhenPerformingNearQuery() {
        // given
        final PlaceWithLegacyCoords nearbyPlace = new PlaceWithLegacyCoords(new double[]{1.1, 2.3}, "Nearby Place");
        getDatastore().save(nearbyPlace);
        final PlaceWithLegacyCoords furtherAwayPlace = new PlaceWithLegacyCoords(new double[]{10.1, 12.3}, "Further Away Place");
        getDatastore().save(furtherAwayPlace);
        getDatastore().ensureIndexes();

        // when
        final List<PlaceWithLegacyCoords> found = getDatastore().find(PlaceWithLegacyCoords.class)
                                                                .field("location")
                                                                .near(1.0, 2.0)
                                                                .asList();

        // then
        assertThat(found, is(notNullValue()));
        assertThat(found.size(), is(2));
        assertThat(found.get(0), is(nearbyPlace));
        assertThat(found.get(1), is(furtherAwayPlace));
    }

    @Test
    public void shouldReturnOnlyThosePlacesWithinTheGivenRadius() {
        // given
        final PlaceWithLegacyCoords nearbyPlace = new PlaceWithLegacyCoords(new double[]{1.1, 2.3}, "Nearby Place");
        getDatastore().save(nearbyPlace);
        final PlaceWithLegacyCoords furtherAwayPlace = new PlaceWithLegacyCoords(new double[]{10.1, 12.3}, "Further Away Place");
        getDatastore().save(furtherAwayPlace);
        getDatastore().ensureIndexes();

        // when
        final List<PlaceWithLegacyCoords> found = getDatastore().find(PlaceWithLegacyCoords.class)
                                                                .field("location")
                                                                .near(1.0, 2.0, 1.5)
                                                                .asList();
        // then
        assertThat(found, is(notNullValue()));
        assertThat(found.size(), is(1));
        assertThat(found.get(0), is(nearbyPlace));
    }

    @Test(expected = MongoException.class)
    public void shouldThrowAnExceptionIfQueryingWithoutA2dIndex() {
        final PlaceWithLegacyCoords nearbyPlace = new PlaceWithLegacyCoords(new double[]{1.1, 2.3}, "Nearby Place");
        getDatastore().save(nearbyPlace);
        assertThat(getDatastore().getCollection(PlaceWithLegacyCoords.class).listIndexes(), doesNotHaveIndexNamed("location_2d"));

        getDatastore().find(PlaceWithLegacyCoords.class)
                      .field("location")
                      .near(0, 0).first();
    }
}
