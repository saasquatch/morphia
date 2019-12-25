/*
 * Copyright (c) 2008 - 2013 MongoDB, Inc. <http://mongodb.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.morphia.aggregation.experimental;

import com.mongodb.client.model.Collation;
import dev.morphia.TestBase;
import dev.morphia.aggregation.experimental.model.Book;
import dev.morphia.aggregation.experimental.model.CountResult;
import dev.morphia.aggregation.experimental.model.Inventory;
import dev.morphia.aggregation.experimental.model.Order;
import dev.morphia.aggregation.experimental.model.StringDates;
import dev.morphia.aggregation.experimental.stages.Group;
import dev.morphia.aggregation.experimental.stages.Sample;
import dev.morphia.query.Query;
import dev.morphia.query.internal.MorphiaCursor;
import dev.morphia.testmodel.User;
import org.junit.Assert;
import org.junit.Test;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import static com.mongodb.client.model.CollationStrength.SECONDARY;
import static dev.morphia.aggregation.experimental.Lookup.from;
import static dev.morphia.aggregation.experimental.stages.Accumulator.sum;
import static dev.morphia.aggregation.experimental.stages.DateExpression.dateToString;
import static dev.morphia.aggregation.experimental.stages.DateExpression.month;
import static dev.morphia.aggregation.experimental.stages.DateExpression.year;
import static dev.morphia.aggregation.experimental.stages.Expression.field;
import static dev.morphia.aggregation.experimental.stages.Expression.literal;
import static dev.morphia.aggregation.experimental.stages.Group.id;
import static dev.morphia.aggregation.experimental.stages.Projection.of;
import static dev.morphia.aggregation.experimental.stages.Sort.on;
import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;

public class AggregationTest extends TestBase {

    @Test
    public void testCollation() {
        getDs().save(asList(new User("john doe", new Date()), new User("John Doe", new Date())));

        Query query = getDs().find(User.class).field("name").equal("john doe");
        Aggregation<User> pipeline = getDs()
                                         .aggregate(User.class)
                                         .match(query);
        assertEquals(1, count(pipeline.execute(User.class)));

        assertEquals(2, count(pipeline.execute(User.class,
            new dev.morphia.aggregation.experimental.AggregationOptions()
                .collation(Collation.builder()
                                    .locale("en")
                                    .collationStrength(SECONDARY)
                                    .build()))));
    }

    @Test
    public void testDateAggregation() {
        Aggregation<User> pipeline = getDs()
                                         .aggregate(User.class)
                                         .group(id(
                                             month("month", "$date"),
                                             year("year", "$date"))
                                                    .fields(sum("count", 1)));
        pipeline.execute(User.class);
    }

    @Test
    public void testNullGroupId() {
        Aggregation<User> pipeline = getDs()
                                         .aggregate(User.class)
                                         .group(Group.nullId()
                                                     .fields(sum("count", 1)));

        Group group = pipeline.getStage("$group");
        Assert.assertNull(group.getId());
        assertEquals(1, group.getFields().size());

        pipeline.execute(User.class);
    }

    @Test
    public void testSampleStage() {
        Aggregation<User> pipeline = getDs()
                                         .aggregate(User.class)
                                         .sample(Sample.of(1));
        Sample sample = pipeline.getStage("$sample");
        assertEquals(1, sample.getSize());

        pipeline.execute(User.class);
    }

    @Test
    public void testDateToString() throws ParseException {
        Date joined = new SimpleDateFormat("yyyy-MM-dd z").parse("2016-05-01 UTC");
        getDs().save(new User("John Doe", joined));
        Aggregation<User> pipeline = getDs()
                                         .aggregate(User.class)
                                           .project(of()
                                                        .include("string",
                                                            dateToString("%Y-%m-%d", field("joined"))));

        MorphiaCursor<StringDates> it = pipeline.execute(StringDates.class);
        while (it.hasNext()) {
            final StringDates next = it.next();
            assertEquals("2016-05-01", next.getString());
        }
    }

   @Test
    public void testGenericAccumulatorUsage() {
       getDs().save(asList(new Book("The Banquet", "Dante", 2),
           new Book("Divine Comedy", "Dante", 1),
           new Book("Eclogues", "Dante", 2),
           new Book("The Odyssey", "Homer", 10),
           new Book("Iliad", "Homer", 10)));

       MorphiaCursor<CountResult> aggregation = getDs().aggregate(Book.class)
                                                       .group(id("author")
                                                                  .fields(sum("count", literal(1))))
                                                       .sort(on().ascending("_id"))
                                                       .execute(CountResult.class);

       CountResult result1 = aggregation.next();
       CountResult result2 = aggregation.next();
       Assert.assertFalse("Expecting two results", aggregation.hasNext());
       Assert.assertEquals("Dante", result1.getAuthor());
       Assert.assertEquals(3, result1.getCount());
       Assert.assertEquals("Homer", result2.getAuthor());
       Assert.assertEquals(2, result2.getCount());
   }

    @Test
    public void testLimit() {
        getDs().save(asList(new Book("The Banquet", "Dante", 2),
            new Book("Divine Comedy", "Dante", 1),
            new Book("Eclogues", "Dante", 2),
            new Book("The Odyssey", "Homer", 10),
            new Book("Iliad", "Homer", 10)));

        MorphiaCursor<Book> aggregate = getDs().aggregate(Book.class)
                                               .limit(2)
                                               .execute(Book.class);
        int count = 0;
        while (aggregate.hasNext()) {
            aggregate.next();
            count++;
        }
        Assert.assertEquals(2, count);
    }

    // Test data pulled from https://docs.mongodb.com/v3.2/reference/operator/aggregation/lookup/
    @Test
    public void testLookup() {
        getDs().save(asList(new Order(1, "abc", 12, 2),
            new Order(2, "jkl", 20, 1),
            new Order(3)));
        List<Inventory> inventories = asList(new Inventory(1, "abc", "product 1", 120),
            new Inventory(2, "def", "product 2", 80),
            new Inventory(3, "ijk", "product 3", 60),
            new Inventory(4, "jkl", "product 4", 70),
            new Inventory(5, null, "Incomplete"),
            new Inventory(6));
        getDs().save(inventories);

        List<Order> lookups = getDs().aggregate(Order.class)
                                     .lookup(from(Inventory.class)
                                                 .localField("item")
                                                 .foreignField("sku")
                                                 .as("inventoryDocs"))
                                     .sort(on().ascending("_id"))
                                     .execute(Order.class)
                                     .toList();
        Assert.assertEquals(inventories.get(0), lookups.get(0).getInventoryDocs().get(0));
        Assert.assertEquals(inventories.get(3), lookups.get(1).getInventoryDocs().get(0));
        Assert.assertEquals(inventories.get(4), lookups.get(2).getInventoryDocs().get(0));
        Assert.assertEquals(inventories.get(5), lookups.get(2).getInventoryDocs().get(1));
    }
/*
    @Test
    public void testGeoNearWithGeoJson() {
        // given
        Point londonPoint = new Point(new Position(51.5286416, -0.1015987));
        City london = new City("London", londonPoint);
        getDs().save(london);
        City manchester = new City("Manchester", new Point(new Position(53.4722454, -2.2235922)));
        getDs().save(manchester);
        City sevilla = new City("Sevilla", new Point(new Position(37.3753708, -5.9550582)));
        getDs().save(sevilla);

        getDs().ensureIndexes();

        // when
        Iterator<City> citiesOrderedByDistanceFromLondon = getDs().createAggregation(City.class)
                                                                  .geoNear(GeoNear.builder("distance")
                                                                                  .setNear(londonPoint)
                                                                                  .setSpherical(true)
                                                                                  .build())
                                                                  .aggregate(City.class).iterator();

        // then
        Assert.assertTrue(citiesOrderedByDistanceFromLondon.hasNext());
        Assert.assertEquals(london, citiesOrderedByDistanceFromLondon.next());
        Assert.assertEquals(manchester, citiesOrderedByDistanceFromLondon.next());
        Assert.assertEquals(sevilla, citiesOrderedByDistanceFromLondon.next());
        Assert.assertFalse(citiesOrderedByDistanceFromLondon.hasNext());
    }

    @Test
    public void testGeoNearWithLegacyCoords() {
        // given
        double latitude = 51.5286416;
        double longitude = -0.1015987;
        PlaceWithLegacyCoords london = new PlaceWithLegacyCoords(new double[]{longitude, latitude}, "London");
        getDs().save(london);
        PlaceWithLegacyCoords manchester = new PlaceWithLegacyCoords(new double[]{-2.2235922, 53.4722454}, "Manchester");
        getDs().save(manchester);
        PlaceWithLegacyCoords sevilla = new PlaceWithLegacyCoords(new double[]{-5.9550582, 37.3753708}, "Sevilla");
        getDs().save(sevilla);

        getDs().ensureIndexes();

        // when
        Iterator<PlaceWithLegacyCoords> citiesOrderedByDistanceFromLondon = getDs()
                                                                                .createAggregation(PlaceWithLegacyCoords.class)
                                                                                .geoNear(GeoNear.builder("distance")
                                                                                                .setNear(latitude, longitude)
                                                                                                .setSpherical(false)
                                                                                                .build())
                                                                                .aggregate(PlaceWithLegacyCoords.class)
                                                                                .iterator();

        // then
        Assert.assertTrue(citiesOrderedByDistanceFromLondon.hasNext());
        Assert.assertEquals(london, citiesOrderedByDistanceFromLondon.next());
        Assert.assertEquals(manchester, citiesOrderedByDistanceFromLondon.next());
        Assert.assertEquals(sevilla, citiesOrderedByDistanceFromLondon.next());
        Assert.assertFalse(citiesOrderedByDistanceFromLondon.hasNext());
    }

    @Test
    public void testGeoNearWithSphericalGeometry() {
        // given
        double latitude = 51.5286416;
        double longitude = -0.1015987;
        City london = new City("London", new Point(new Position(latitude, longitude)));
        getDs().save(london);
        City manchester = new City("Manchester", new Point(new Position(53.4722454, -2.2235922)));
        getDs().save(manchester);
        City sevilla = new City("Sevilla", new Point(new Position(37.3753708, -5.9550582)));
        getDs().save(sevilla);

        getDs().ensureIndexes();

        // when
        Iterator<City> citiesOrderedByDistanceFromLondon = getDs().createAggregation(City.class)
                                                                  .geoNear(GeoNear.builder("distance")
                                                                                  .setNear(latitude, longitude)
                                                                                  .setSpherical(true)
                                                                                  .build())
                                                                  .aggregate(City.class)
                                                                  .iterator();

        // then
        Assert.assertTrue(citiesOrderedByDistanceFromLondon.hasNext());
        Assert.assertEquals(london, citiesOrderedByDistanceFromLondon.next());
        Assert.assertEquals(manchester, citiesOrderedByDistanceFromLondon.next());
        Assert.assertEquals(sevilla, citiesOrderedByDistanceFromLondon.next());
        Assert.assertFalse(citiesOrderedByDistanceFromLondon.hasNext());
    }



    @Test
    public void testOut() {
        getDs().save(asList(new Book("The Banquet", "Dante", 2),
            new Book("Divine Comedy", "Dante", 1),
            new Book("Eclogues", "Dante", 2),
            new Book("The Odyssey", "Homer", 10),
            new Book("Iliad", "Homer", 10)));

        AggregationOptions options = builder()
                                         .build();
        Iterator<Author> aggregate = getDs().createAggregation(Book.class)
                                            .group("author", grouping("books", push("title")))
                                            .out(Author.class, options)
                                            .iterator();
        Assert.assertEquals(2, getDs().getCollection(Author.class).countDocuments());
        Author author = aggregate.next();
        Assert.assertEquals("Homer", author.name);
        Assert.assertEquals(asList("The Odyssey", "Iliad"), author.books);

        getDs().createAggregation(Book.class)
               .group("author", grouping("books", push("title")))
               .out("different", Author.class);

        Assert.assertEquals(2, getDatabase().getCollection("different").countDocuments());
    }

    @Test
    public void testOutNamedCollection() {
        getDs().save(asList(new Book("The Banquet", "Dante", 2, "Italian", "Sophomore Slump"),
            new Book("Divine Comedy", "Dante", 1, "Not Very Funny", "I mean for a 'comedy'", "Ironic"),
            new Book("Eclogues", "Dante", 2, "Italian", ""),
            new Book("The Odyssey", "Homer", 10, "Classic", "Mythology", "Sequel"),
            new Book("Iliad", "Homer", 10, "Mythology", "Trojan War", "No Sequel")));

        getDs().createAggregation(Book.class)
               .match(getDs().getQueryFactory().createQuery(getDs())
                             .field("author").equal("Homer"))
               .group("author", grouping("copies", sum("copies")))
               .out("testAverage", Author.class);
        try (MongoCursor<Document> testAverage = getDatabase().getCollection("testAverage").find().iterator()) {
            Assert.assertEquals(20, testAverage.next().get("copies"));
        }
    }

    @Test
    public void testProjection() {
        getDs().save(asList(new Book("The Banquet", "Dante", 2),
            new Book("Divine Comedy", "Dante", 1),
            new Book("Eclogues", "Dante", 2),
            new Book("The Odyssey", "Homer", 10),
            new Book("Iliad", "Homer", 10)));

        final AggregationPipeline pipeline = getDs().createAggregation(Book.class)
                                                    .group("author", grouping("copies", sum("copies")))
                                                    .project(projection("_id").suppress(),
                                                        projection("author", "_id"),
                                                        projection("copies", divide(projection("copies"), 5)))
                                                    .sort(ascending("author"));
        Iterator<Book> aggregate = pipeline.aggregate(Book.class).iterator();
        Book book = aggregate.next();
        Assert.assertEquals("Dante", book.author);
        Assert.assertEquals(1, book.copies.intValue());

        final List<Document> stages = ((AggregationPipelineImpl) pipeline).getStages();
        Assert.assertEquals(stages.get(0), obj("$group", obj("_id", "$author").append("copies", obj("$sum", "$copies"))));
        Assert.assertEquals(stages.get(1), obj("$project", obj("_id", 0)
                                                               .append("author", "$_id")
                                                               .append("copies", obj("$divide", Arrays.<Object>asList("$copies", 5)))));

    }

    @Test
    public void testSizeProjection() {
        getDs().save(asList(new Book("The Banquet", "Dante", 2),
            new Book("Divine Comedy", "Dante", 1),
            new Book("Eclogues", "Dante", 2),
            new Book("The Odyssey", "Homer", 10),
            new Book("Iliad", "Homer", 10)));

        final AggregationPipeline pipeline = getDs().createAggregation(Book.class)
                                                    .group("author", grouping("titles", addToSet("title")))
                                                    .project(projection("_id").suppress(),
                                                        projection("author", "_id"),
                                                        projection("copies", size(projection("titles"))))
                                                    .sort(ascending("author"));
        Iterator<Book> aggregate = pipeline.aggregate(Book.class).iterator();
        Book book = aggregate.next();
        Assert.assertEquals("Dante", book.author);
        Assert.assertEquals(3, book.copies.intValue());

        final List<Document> stages = ((AggregationPipelineImpl) pipeline).getStages();
        Assert.assertEquals(stages.get(0), obj("$group", obj("_id", "$author").append("titles", obj("$addToSet", "$title"))));
        Assert.assertEquals(stages.get(1), obj("$project", obj("_id", 0)
                                                               .append("author", "$_id")
                                                               .append("copies", obj("$size", "$titles"))));
    }

    @Test
    public void testSkip() {
        getDs().save(asList(new Book("The Banquet", "Dante", 2),
            new Book("Divine Comedy", "Dante", 1),
            new Book("Eclogues", "Dante", 2),
            new Book("The Odyssey", "Homer", 10),
            new Book("Iliad", "Homer", 10)));

        Book book = getDs().createAggregation(Book.class)
                           .skip(2)
                           .aggregate(Book.class)
                           .first();
        Assert.assertEquals("Eclogues", book.title);
        Assert.assertEquals("Dante", book.author);
        Assert.assertEquals(2, book.copies.intValue());
    }

    @Test
    public void testUnwind() throws ParseException {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        getDs().save(asList(new User("jane", format.parse("2011-03-02"), "golf", "racquetball"),
            new User("joe", format.parse("2012-07-02"), "tennis", "golf", "swimming"),
            new User("john", format.parse("2012-07-02"))));

        Iterator<User> aggregate = getDs().createAggregation(User.class)
                                          .project(projection("_id").suppress(), projection("name"), projection("joined"),
                                              projection("likes"))
                                          .unwind("likes")
                                          .aggregate(User.class)
                                          .iterator();
        int count = 0;
        while (aggregate.hasNext()) {
            User user = aggregate.next();
            switch (count) {
                case 0:
                    Assert.assertEquals("jane", user.name);
                    Assert.assertEquals("golf", user.likes.get(0));
                    break;
                case 1:
                    Assert.assertEquals("jane", user.name);
                    Assert.assertEquals("racquetball", user.likes.get(0));
                    break;
                case 2:
                    Assert.assertEquals("joe", user.name);
                    Assert.assertEquals("tennis", user.likes.get(0));
                    break;
                case 3:
                    Assert.assertEquals("joe", user.name);
                    Assert.assertEquals("golf", user.likes.get(0));
                    break;
                case 4:
                    Assert.assertEquals("joe", user.name);
                    Assert.assertEquals("swimming", user.likes.get(0));
                    break;
                default:
                    fail("Should only find 5 elements");
            }
            count++;
        }

        aggregate = getDs().createAggregation(User.class)
                           .project(projection("_id").suppress(), projection("name"), projection("joined"),
                               projection("likes"))
                           .unwind("likes", new UnwindOptions().preserveNullAndEmptyArrays(true))
                           .aggregate(User.class)
                           .iterator();
        count = 0;
        while (aggregate.hasNext()) {
            User user = aggregate.next();
            switch (count) {
                case 0:
                    Assert.assertEquals("jane", user.name);
                    Assert.assertEquals("golf", user.likes.get(0));
                    break;
                case 1:
                    Assert.assertEquals("jane", user.name);
                    Assert.assertEquals("racquetball", user.likes.get(0));
                    break;
                case 2:
                    Assert.assertEquals("joe", user.name);
                    Assert.assertEquals("tennis", user.likes.get(0));
                    break;
                case 3:
                    Assert.assertEquals("joe", user.name);
                    Assert.assertEquals("golf", user.likes.get(0));
                    break;
                case 4:
                    Assert.assertEquals("joe", user.name);
                    Assert.assertEquals("swimming", user.likes.get(0));
                    break;
                case 5:
                    Assert.assertEquals("john", user.name);
                    Assert.assertNull(user.likes);
                    break;
                default:
                    fail("Should only find 6 elements");
            }
            count++;
        }
    }

    @Test
    public void testSortByCount() {
        getDs().save(asList(new Book("The Banquet", "Dante", 2),
            new Book("Divine Comedy", "Dante", 1),
            new Book("Eclogues", "Dante", 2),
            new Book("The Odyssey", "Homer", 10),
            new Book("Iliad", "Homer", 10)));


        Iterator<SortByCountResult> aggregate = getDs().createAggregation(Book.class)
                                                       .sortByCount("author").aggregate(SortByCountResult.class)
                                                       .iterator();
        SortByCountResult result1 = aggregate.next();
        assertEquals(result1.id, "Dante");
        assertEquals(result1.count, 3);

        SortByCountResult result2 = aggregate.next();
        assertEquals(result2.id, "Homer");
        assertEquals(result2.count, 2);

    }

    @Test
    public void testBucketWithoutOptions() {
        getDs().save(asList(new Book("The Banquet", "Dante", 2),
            new Book("Divine Comedy", "Dante", 1),
            new Book("Eclogues", "Dante", 2),
            new Book("The Odyssey", "Homer", 10),
            new Book("Iliad", "Homer", 10)));

        Iterator<BucketResult> aggregate = getDs().createAggregation(Book.class)
                                                  .bucket("copies", Arrays.asList(1, 5, 12)).aggregate(BucketResult.class)
                                                  .iterator();
        BucketResult result1 = aggregate.next();
        assertEquals(result1.id, "1");
        assertEquals(result1.count, 3);

        BucketResult result2 = aggregate.next();
        assertEquals(result2.id, "5");
        assertEquals(result2.count, 2);

    }

    @Test
    public void testBucketWithOptions() {
        getDs().save(asList(new Book("The Banquet", "Dante", 2),
            new Book("Divine Comedy", "Dante", 1),
            new Book("Eclogues", "Dante", 2),
            new Book("The Odyssey", "Homer", 10),
            new Book("Iliad", "Homer", 10)));

        Iterator<BucketResult> aggregate = getDs().createAggregation(Book.class)
                                                  .bucket("copies", Arrays.asList(1, 5, 10),
                                                      new BucketOptions()
                                                          .defaultField("test")
                                                          .output("count").sum(1))
                                                  .aggregate(BucketResult.class)
                                                  .iterator();
        BucketResult result1 = aggregate.next();
        assertEquals(result1.id, "1");
        assertEquals(result1.count, 3);

        BucketResult result2 = aggregate.next();
        assertEquals(result2.id, "test");
        assertEquals(result2.count, 2);

    }

    @Test(expected = RuntimeException.class)
    public void testBucketWithUnsortedBoundaries() {
        getDs().save(asList(new Book("The Banquet", "Dante", 2),
            new Book("Divine Comedy", "Dante", 1),
            new Book("Eclogues", "Dante", 2),
            new Book("The Odyssey", "Homer", 10),
            new Book("Iliad", "Homer", 10)));

        getDs().createAggregation(Book.class)
               .bucket("copies", Arrays.asList(5, 1, 10),
                   new BucketOptions()
                       .defaultField("test")
                       .output("count")
                       .sum(1))
               .aggregate(BucketResult.class)
               .iterator();

    }

    @Test(expected = RuntimeException.class)
    public void testBucketWithBoundariesWithSizeLessThanTwo() {
        getDs().save(asList(new Book("The Banquet", "Dante", 2),
            new Book("Divine Comedy", "Dante", 1),
            new Book("Eclogues", "Dante", 2),
            new Book("The Odyssey", "Homer", 10),
            new Book("Iliad", "Homer", 10)));

        getDs().createAggregation(Book.class)
               .bucket("copies", singletonList(10),
                   new BucketOptions()
                       .defaultField("test")
                       .output("count")
                       .sum(1))
               .aggregate(BucketResult.class)
               .iterator();

    }


    @Test
    public void testBucketAutoWithoutGranularity() {
        getDs().save(asList(new Book("The Banquet", "Dante", 5),
            new Book("Divine Comedy", "Dante", 10),
            new Book("Eclogues", "Dante", 40),
            new Book("The Odyssey", "Homer", 21)));

        Iterator<BucketAutoResult> aggregate = getDs().createAggregation(Book.class)
                                                      .bucketAuto("copies", 2)
                                                      .aggregate(BucketAutoResult.class)
                                                      .iterator();
        BucketAutoResult result1 = aggregate.next();
        assertEquals(result1.id.min, 5);
        assertEquals(result1.id.max, 21);
        assertEquals(result1.count, 2);
        result1 = aggregate.next();
        assertEquals(result1.id.min, 21);
        assertEquals(result1.id.max, 40);
        assertEquals(result1.count, 2);
        assertFalse(aggregate.hasNext());

    }

    @Test
    public void testBucketAutoWithGranularity() {
        getDs().save(asList(new Book("The Banquet", "Dante", 5),
            new Book("Divine Comedy", "Dante", 7),
            new Book("Eclogues", "Dante", 40),
            new Book("The Odyssey", "Homer", 21)));

        Iterator<BooksBucketResult> aggregate = getDs().createAggregation(Book.class)
                                                       .bucketAuto("copies", 3,
                                                           new BucketAutoOptions()
                                                               .granularity(BucketAutoOptions.Granularity.POWERSOF2)
                                                               .output("authors")
                                                               .addToSet("author")
                                                               .output("count")
                                                               .sum(1))
                                                       .aggregate(BooksBucketResult.class)
                                                       .iterator();
        BooksBucketResult result1 = aggregate.next();
        assertEquals(result1.getId().min, 4);
        assertEquals(result1.getId().max, 8);
        assertEquals(result1.getCount(), 2);
        assertEquals(result1.authors, singleton("Dante"));

        result1 = aggregate.next();
        assertEquals(result1.getId().min, 8);
        assertEquals(result1.getId().max, 32);
        assertEquals(result1.getCount(), 1);
        assertEquals(result1.authors, singleton("Homer"));

        result1 = aggregate.next();
        assertEquals(result1.getId().min, 32);
        assertEquals(result1.getId().max, 64);
        assertEquals(result1.getCount(), 1);
        assertEquals(result1.authors, singleton("Dante"));
        assertFalse(aggregate.hasNext());

    }

    @Test
    public void testUserPreferencesPipeline() {
        final AggregationPipeline pipeline = getDs().createAggregation(Book.class)  // the class is irrelevant for this test
                                                    .group("state", Group.grouping("total_pop", sum("pop")))
                                                    .match(getDs().find(Book.class)
                                                                  .disableValidation()
                                                                  .field("total_pop").greaterThanOrEq(10000000));
        Document group = obj("$group", obj("_id", "$state")
                                           .append("total_pop", obj("$sum", "$pop")));

        Document match = obj("$match", obj("total_pop", obj("$gte", 10000000)));

        final List<Document> stages = ((AggregationPipelineImpl) pipeline).getStages();
        Assert.assertEquals(stages.get(0), group);
        Assert.assertEquals(stages.get(1), match);
    }

    @Test
    public void testGroupWithProjection() {
        AggregationPipeline pipeline =
            getDs().createAggregation(Author.class)
                   .group("subjectHash",
                       grouping("authors", addToSet("fromAddress.address")),
                       grouping("messageDataSet", grouping("$addToSet",
                           projection("sentDate", "sentDate"),
                           projection("messageId", "_id"))),
                       grouping("messageCount", accumulator("$sum", 1)))
                   .limit(10)
                   .skip(0);
        List<Document> stages = ((AggregationPipelineImpl) pipeline).getStages();
        Document group = stages.get(0);
        Document addToSet = getDocument(group, "$group", "messageDataSet", "$addToSet");
        Assert.assertNotNull(addToSet);
        Assert.assertEquals(addToSet.get("sentDate"), "$sentDate");
        Assert.assertEquals(addToSet.get("messageId"), "$_id");
    }

    @Test
    public void testAdd() {
        AggregationPipeline pipeline = getDs()
                                           .createAggregation(Book.class)
                                           .group(grouping("summation",
                                               accumulator("$sum",
                                                   accumulator("$add", asList("$amountFromTBInDouble", "$amountFromParentPNLInDouble"))
                                                          )));

        Document group = (Document) ((AggregationPipelineImpl) pipeline).getStages().get(0).get("$group");
        Document summation = (Document) group.get("summation");
        Document sum = (Document) summation.get("$sum");
        List<?> add = (List<?>) sum.get("$add");
        Assert.assertTrue(add.get(0) instanceof String);
        Assert.assertEquals("$amountFromTBInDouble", add.get(0));
        pipeline.aggregate(User.class);
    }
*/
}
