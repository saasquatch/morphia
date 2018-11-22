package xyz.morphia.geo;

import com.mongodb.client.model.geojson.Geometry;
import com.mongodb.client.model.geojson.GeometryCollection;
import com.mongodb.client.model.geojson.LineString;
import com.mongodb.client.model.geojson.MultiLineString;
import com.mongodb.client.model.geojson.MultiPoint;
import com.mongodb.client.model.geojson.MultiPolygon;
import com.mongodb.client.model.geojson.Point;
import com.mongodb.client.model.geojson.Polygon;
import com.mongodb.client.model.geojson.PolygonCoordinates;
import com.mongodb.client.model.geojson.Position;

import java.util.ArrayList;
import java.util.List;

import static java.util.Arrays.asList;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toList;

/**
 * Factory class for creating GeoJSON types.  See <a href="http://docs.mongodb
 * .org/manual/applications/geospatial-indexes/#geojson-objects">the
 * documentation</a> for all the types.
 */
@SuppressWarnings("unchecked")
public final class GeoJson {
    private GeoJson() {
    }

    /**
     * Create a new Position representing a GeoJSON position type.
     *
     * @param latitude  the position's latitude coordinate
     * @param longitude the position's longitude coordinate
     * @return a Position instance representing a single location position defined by the given latitude and longitude
     * @mongodb.server.release 2.4
     * @see <a href="http://docs.mongodb.org/manual/apps/geospatial-indexes/#geojson-objects">GeoJSON</a>
     */
    public static Point point(final double latitude, final double longitude) {
        return new Point(position(latitude, longitude));
    }

    /**
     * Create a new Position representing a GeoJSON position type.
     *
     * @param latitude  the position's latitude coordinate
     * @param longitude the position's longitude coordinate
     * @return a Position instance representing a single location position defined by the given latitude and longitude
     * @mongodb.server.release 2.4
     * @see <a href="http://docs.mongodb.org/manual/apps/geospatial-indexes/#geojson-objects">GeoJSON</a>
     */
    public static Position position(final double latitude, final double longitude) {
        return new Position(latitude, longitude);
    }

    /**
     * Create a new Polygon representing a GeoJSON Polygon type. This helper method uses {@link #polygon(LineString, LineString...)} to
     * create the Polygon.  If you need to create Polygons with interior rings (holes), use that method.
     *
     * @param points an ordered series of Points that make up the polygon.  The first and last points should be the same to close the
     *               polygon
     * @return a Polygon as defined by the points.
     * @throws java.lang.IllegalArgumentException if the start and end points are not the same
     * @mongodb.server.release 2.4
     * @see xyz.morphia.geo.GeoJson#polygon(LineString, LineString...)
     * @see <a href="http://docs.mongodb.org/manual/apps/geospatial-indexes/#geojson-objects">GeoJSON</a>
     */
    public static Polygon polygon(final Point... points) {
        return new Polygon(stream(points).map(p -> new Position(p.getCoordinates().getValues()))
                                         .collect(toList()));
    }

    /**
     * Create a new Polygon representing a GeoJSON Polygon type. This helper method uses {@link #polygon(LineString, LineString...)} to
     * create the Polygon.  If you need to create Polygons with interior rings (holes), use that method.
     *
     * @param exterior an ordered series of Position that make up the polygon.  The first and last points should be the same to close the
     *               polygon
     * @param holes the holes in the polygon
     * @return a Polygon as defined by the points.
     * @throws java.lang.IllegalArgumentException if the start and end points are not the same
     * @mongodb.server.release 2.4
     * @see <a href="http://docs.mongodb.org/manual/apps/geospatial-indexes/#geojson-objects">GeoJSON</a>
     */
    public static Polygon polygon(final List<Position> exterior, final List<Position>... holes) {
        return new Polygon(exterior, holes);
    }

    /**
     * Create a new Polygon representing a GeoJSON Polygon type. This helper method uses {@link #polygon(LineString, LineString...)} to
     * create the Polygon.  If you need to create Polygons with interior rings (holes), use that method.
     *
     * @param exterior an ordered series of Position that make up the polygon.  The first and last points should be the same to close the
     *               polygon
     * @return a Polygon as defined by the points.
     * @throws java.lang.IllegalArgumentException if the start and end points are not the same
     * @mongodb.server.release 2.4
     * @see <a href="http://docs.mongodb.org/manual/apps/geospatial-indexes/#geojson-objects">GeoJSON</a>
     */
    public static Polygon polygon(final Position... exterior) {
        return new Polygon(asList(exterior));
    }

    /**
     * Create a new LineString representing a GeoJSON LineString type.
     *
     * @param points an ordered series of Position that make up the line
     * @return a LineString instance representing a series of ordered points that make up a line
     * @mongodb.server.release 2.4
     * @see <a href="http://docs.mongodb.org/manual/apps/geospatial-indexes/#geojson-objects">GeoJSON</a>
     */
    public static LineString lineString(final Position... points) {
        return new LineString(asList(points));
    }

    private static void ensurePolygonIsClosed(final LineString points) {
        int size = points.getCoordinates().size();
        if (size > 0 && !points.getCoordinates().get(0).equals(points.getCoordinates().get(size - 1))) {
            throw new IllegalArgumentException("A polygon requires the starting point to be the same as the end to ensure a closed "
                                               + "area");
        }
    }

    /**
     * Lets you create a Polygon representing a GeoJSON Polygon type. This method is especially useful for defining polygons with inner
     * rings.
     *
     * @param exteriorBoundary   a LineString that contains a series of Points that make up the polygon.  The first and last points should
     *                           be the same to close the polygon
     * @param interiorBoundaries optional varargs that let you define the boundaries for any holes inside the polygon
     * @return a PolygonBuilder to be used to build up the required Polygon
     * @throws java.lang.IllegalArgumentException if the start and end points are not the same
     * @mongodb.server.release 2.4
     * @see <a href="http://docs.mongodb.org/manual/apps/geospatial-indexes/#geojson-objects">GeoJSON</a>
     */
    public static Polygon polygon(final LineString exteriorBoundary, final LineString... interiorBoundaries) {
        ensurePolygonIsClosed(exteriorBoundary);
        for (final LineString boundary : interiorBoundaries) {
            ensurePolygonIsClosed(boundary);
        }

        final List<List<Position>> holes = new ArrayList<>();

        for (final LineString boundary : interiorBoundaries) {
            holes.add(new ArrayList<>(boundary.getCoordinates()));
        }

        return new Polygon(exteriorBoundary.getCoordinates(), holes.toArray(new ArrayList[0]));
    }

    /**
     * Create a new MultiPoint representing a GeoJSON MultiPoint type.
     *
     * @param position a set of points that make up the MultiPoint object
     * @return a MultiPoint object containing all the given points
     * @mongodb.server.release 2.6
     * @see <a href="http://docs.mongodb.org/manual/apps/geospatial-indexes/#geojson-objects">GeoJSON</a>
     */
    public static MultiPoint multiPoint(final Position... position) {
        return new MultiPoint(asList(position));
    }

    /**
     * Create a new MultiLineString representing a GeoJSON MultiLineString type.
     *
     * @param lines a set of lines that make up the MultiLineString object
     * @return a MultiLineString object containing all the given lines
     * @mongodb.server.release 2.6
     * @see <a href="http://docs.mongodb.org/manual/apps/geospatial-indexes/#geojson-objects">GeoJSON</a>
     */
    public static MultiLineString multiLineString(final List<List<Position>> lines) {
        return new MultiLineString(lines);
    }

    /**
     * Create a new MultiPolygon representing a GeoJSON MultiPolygon type.
     *
     * @param polygons a series of polygons (which may contain inner rings)
     * @return a MultiPolygon object containing all the given polygons
     * @mongodb.server.release 2.6
     * @see <a href="http://docs.mongodb.org/manual/apps/geospatial-indexes/#geojson-objects">GeoJSON</a>
     */
    public static MultiPolygon multiPolygon(final Polygon... polygons) {
        return new MultiPolygon(stream(polygons)
                                    .map(p -> new PolygonCoordinates(p.getExterior()))
                                    .collect(toList()));
    }

    /**
     * Create a new MultiPolygon representing a GeoJSON MultiPolygon type.
     *
     * @param polygons a series of polygons (which may contain inner rings)
     * @return a MultiPolygon object containing all the given polygons
     * @mongodb.server.release 2.6
     * @see <a href="http://docs.mongodb.org/manual/apps/geospatial-indexes/#geojson-objects">GeoJSON</a>
     */
    public static MultiPolygon multiPolygon(final PolygonCoordinates... polygons) {
        return new MultiPolygon(asList(polygons));
    }

    /**
     * Return a GeometryCollection that will let you create a GeoJSON GeometryCollection.
     *
     * @param geometries a series of Geometry instances that will make up this GeometryCollection
     * @return a GeometryCollection made up of all the geometries
     * @mongodb.server.release 2.6
     * @see <a href="http://docs.mongodb.org/manual/apps/geospatial-indexes/#geojson-objects">GeoJSON</a>
     */
    public static GeometryCollection geometryCollection(final Geometry... geometries) {
        return new GeometryCollection(asList(geometries));
    }
}
