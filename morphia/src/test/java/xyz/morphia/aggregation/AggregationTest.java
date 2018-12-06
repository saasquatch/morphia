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

package xyz.morphia.aggregation;

import com.mongodb.MongoCommandException;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Collation;
import com.mongodb.client.model.CollationStrength;
import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.client.model.ValidationOptions;
import com.mongodb.client.model.geojson.Point;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.Assert;
import org.junit.Test;
import xyz.morphia.TestBase;
import xyz.morphia.annotations.Entity;
import xyz.morphia.annotations.Id;
import xyz.morphia.annotations.Validation;
import xyz.morphia.geo.City;
import xyz.morphia.query.Query;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import static com.mongodb.AggregationOptions.builder;
import static java.lang.String.format;
import static java.util.Arrays.asList;
import static org.bson.Document.parse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static xyz.morphia.aggregation.Accumulator.accumulator;
import static xyz.morphia.aggregation.Group.addToSet;
import static xyz.morphia.aggregation.Group.grouping;
import static xyz.morphia.aggregation.Group.id;
import static xyz.morphia.aggregation.Group.push;
import static xyz.morphia.aggregation.Group.sum;
import static xyz.morphia.aggregation.Projection.divide;
import static xyz.morphia.aggregation.Projection.expression;
import static xyz.morphia.aggregation.Projection.projection;
import static xyz.morphia.geo.GeoJson.point;
import static xyz.morphia.query.Sort.ascending;

@SuppressWarnings("unused")
public class AggregationTest extends TestBase {

    @Test
    public void testCollation() {
        checkMinServerVersion(3.4);
        getDatastore().save(asList(new User("john doe", new Date()), new User("John Doe", new Date())));

        Query query = getDatastore().find(User.class).field("name").equal("john doe");
        AggregationPipeline pipeline = getDatastore()
            .createAggregation(User.class)
            .match(query);
        assertEquals(1, count(pipeline.aggregate(User.class)));

        assertEquals(2, count(pipeline.aggregate(User.class,
                                                        builder()
                                                            .collation(Collation.builder()
                                                                                .locale("en")
                                                                                .collationStrength(
                                                                                    CollationStrength.SECONDARY)
                                                                                .build()).build())));
    }

    @Test
    public void testBypassDocumentValidation() {
        checkMinServerVersion(3.2);
        getDatastore().save(asList(new User("john doe", new Date()), new User("John Doe", new Date())));

        MongoDatabase database = getMongoClient().getDatabase(TEST_DB_NAME);
        database.getCollection("out_users").drop();
        database.createCollection("out_users", new CreateCollectionOptions()
            .validationOptions(new ValidationOptions()
                                   .validator(parse("{ age : { gte : 13 } }"))));

        try {
            getDatastore().createAggregation(User.class)
                .match(getDatastore().find(User.class).field("name").equal("john doe"))
                .out("out_users", User.class);
            fail("Document validation should have complained.");
        } catch (MongoCommandException e) {
            // expected
        }

        getDatastore()
            .createAggregation(User.class)
            .match(getDatastore().find(User.class).field("name").equal("john doe"))
            .out("out_users", User.class, builder()
                .bypassDocumentValidation(true)
                .build());

        assertEquals(1, getAds().find("out_users", User.class).count());
    }

    @Test
    public void testDateAggregation() {
        AggregationPipeline pipeline = getDatastore()
            .createAggregation(User.class)
            .group(id(grouping("month", accumulator("$month", "date")),
                         grouping("year", accumulator("$year", "date"))),
                grouping("count", accumulator("$sum", 1)));
        final Document group = ((AggregationPipelineImpl) pipeline).getStages().get(0);
        final Document id = getDocument(group, "$group", "_id");
        assertEquals(new Document("$month", "$date"), id.get("month"));
        assertEquals(new Document("$year", "$date"), id.get("year"));

        pipeline.aggregate(User.class);
    }

    @Test
    public void testNullGroupId() {
        AggregationPipeline pipeline = getDatastore()
            .createAggregation(User.class)
            .group(grouping("count", accumulator("$sum", 1)));

        final Document group = ((AggregationPipelineImpl) pipeline).getStages().get(0);
        Assert.assertNull(group.get("_id"));

        pipeline.aggregate(User.class);
    }

    @Test
    public void testDateToString() throws ParseException {
        checkMinServerVersion(3.0);
        Date joined = new SimpleDateFormat("yyyy-MM-dd z").parse("2016-05-01 UTC");
        getDatastore().save(new User("John Doe", joined));
        AggregationPipeline pipeline = getDatastore()
            .createAggregation(User.class)
            .project(projection("string", expression("$dateToString",
                                                     new Document("format", "%Y-%m-%d")
                                                         .append("date", "$joined"))));

        for (StringDates next : pipeline.aggregate(StringDates.class)) {
            assertEquals("2016-05-01", next.string);
        }
    }

    @Test
    public void testGenericAccumulatorUsage() {
        getDatastore().save(asList(new Book("The Banquet", "Dante", 2),
                            new Book("Divine Comedy", "Dante", 1),
                            new Book("Eclogues", "Dante", 2),
                            new Book("The Odyssey", "Homer", 10),
                            new Book("Iliad", "Homer", 10)));

        Iterator<CountResult> aggregation = getDatastore().createAggregation(Book.class)
                                                          .group("author", grouping("count", accumulator("$sum", 1)))
                                                          .sort(ascending("_id"))
                                                          .aggregate(CountResult.class)
                                                          .iterator();

        CountResult result1 = aggregation.next();
        CountResult result2 = aggregation.next();
        Assert.assertFalse("Expecting two results", aggregation.hasNext());
        assertEquals("Dante", result1.getAuthor());
        assertEquals(3, result1.getCount());
        assertEquals("Homer", result2.getAuthor());
        assertEquals(2, result2.getCount());
    }

    @Test
    public void testGeoNearWithGeoJson() {
        // given
        Point londonPoint = point(51.5286416, -0.1015987);
        City london = new City("London", londonPoint);
        getDatastore().save(london);
        City manchester = new City("Manchester", point(53.4722454, -2.2235922));
        getDatastore().save(manchester);
        City sevilla = new City("Sevilla", point(37.3753708, -5.9550582));
        getDatastore().save(sevilla);

        getDatastore().ensureIndexes();

        Iterator<City> citiesOrderedByDistanceFromLondon = getDatastore().createAggregation(City.class)
                                                                         .geoNear(GeoNear.builder("distance")
                                                                                         .setNear(londonPoint)
                                                                                         .setSpherical(true)
                                                                                         .build())
                                                                         .aggregate(City.class)
                                                                         .iterator();

        Assert.assertTrue(citiesOrderedByDistanceFromLondon.hasNext());
        assertEquals(london, citiesOrderedByDistanceFromLondon.next());
        assertEquals(manchester, citiesOrderedByDistanceFromLondon.next());
        assertEquals(sevilla, citiesOrderedByDistanceFromLondon.next());
        Assert.assertFalse(citiesOrderedByDistanceFromLondon.hasNext());
    }

    @Test
    public void testGeoNearWithSphericalGeometry() {
        // given
        double latitude = 51.5286416;
        double longitude = -0.1015987;
        City london = new City("London", point(latitude, longitude));
        getDatastore().save(london);
        City manchester = new City("Manchester", point(53.4722454, -2.2235922));
        getDatastore().save(manchester);
        City sevilla = new City("Sevilla", point(37.3753708, -5.9550582));
        getDatastore().save(sevilla);

        getDatastore().ensureIndexes();

        // when
        Iterator<City> citiesOrderedByDistanceFromLondon = getDatastore().createAggregation(City.class)
                                                                         .geoNear(GeoNear.builder("distance")
                                                                                         .setNear(latitude, longitude)
                                                                                         .setSpherical(true)
                                                                                         .build())
                                                                         .aggregate(City.class)
                                                                         .iterator();

        // then
        Assert.assertTrue(citiesOrderedByDistanceFromLondon.hasNext());
        assertEquals(london, citiesOrderedByDistanceFromLondon.next());
        assertEquals(manchester, citiesOrderedByDistanceFromLondon.next());
        assertEquals(sevilla, citiesOrderedByDistanceFromLondon.next());
        Assert.assertFalse(citiesOrderedByDistanceFromLondon.hasNext());
    }

    @Test
    public void testLimit() {
        getDatastore().save(asList(new Book("The Banquet", "Dante", 2),
                            new Book("Divine Comedy", "Dante", 1),
                            new Book("Eclogues", "Dante", 2),
                            new Book("The Odyssey", "Homer", 10),
                            new Book("Iliad", "Homer", 10)));

        AggregateIterable<Book> aggregate = getDatastore().createAggregation(Book.class)
                                                          .limit(2)
                                                          .aggregate(Book.class);
        assertEquals(2, count(aggregate));
    }

    /**
     * Test data pulled from https://docs.mongodb.com/v3.2/reference/operator/aggregation/lookup/
     */
    @Test
    public void testLookup() {
        checkMinServerVersion(3.2);
        getDatastore().save(asList(new Order(1, "abc", 12, 2),
                            new Order(2, "jkl", 20, 1),
                            new Order(3)));
        List<Inventory> inventories = asList(new Inventory(1, "abc", "product 1", 120),
                                             new Inventory(2, "def", "product 2", 80),
                                             new Inventory(3, "ijk", "product 3", 60),
                                             new Inventory(4, "jkl", "product 4", 70),
                                             new Inventory(5, null, "Incomplete"),
                                             new Inventory(6));
        getDatastore().save(inventories);

        getDatastore().createAggregation(Order.class)
                      .lookup("inventory", "item", "sku", "inventoryDocs")
                      .out("lookups", Order.class);
        List<Order> lookups = getAds().createQuery("lookups", Order.class)
                                      .order(ascending("_id"))
                                      .asList();
        assertEquals(inventories.get(0), lookups.get(0).inventoryDocs.get(0));
        assertEquals(inventories.get(3), lookups.get(1).inventoryDocs.get(0));
        assertEquals(inventories.get(4), lookups.get(2).inventoryDocs.get(0));
        assertEquals(inventories.get(5), lookups.get(2).inventoryDocs.get(1));
    }

    @Test
    public void testOut() {
        getDatastore().save(asList(new Book("The Banquet", "Dante", 2),
                            new Book("Divine Comedy", "Dante", 1),
                            new Book("Eclogues", "Dante", 2),
                            new Book("The Odyssey", "Homer", 10),
                            new Book("Iliad", "Homer", 10)));

        getDatastore().createAggregation(Book.class)
                                                   .group("author", grouping("books", push("title")))
                                                   .out(Author.class);
        assertEquals(2, getDatastore().getCollection(Author.class).countDocuments());
        Author author = getDatastore().find(Author.class).first();
        assertEquals("Homer", author.name);
        assertEquals(asList("The Odyssey", "Iliad"), author.books);

        getDatastore().createAggregation(Book.class)
                      .group("author", grouping("books", push("title")))
                      .out("different", Author.class);

        assertEquals(2, getDatabase().getCollection("different").countDocuments());
    }

    @Test
    public void testOutNamedCollection() {
        getDatastore().save(asList(new Book("The Banquet", "Dante", 2, "Italian", "Sophomore Slump"),
                            new Book("Divine Comedy", "Dante", 1, "Not Very Funny", "I mean for a 'comedy'", "Ironic"),
                            new Book("Eclogues", "Dante", 2, "Italian", ""),
                            new Book("The Odyssey", "Homer", 10, "Classic", "Mythology", "Sequel"),
                            new Book("Iliad", "Homer", 10, "Mythology", "Trojan War", "No Sequel")));

        getDatastore().createAggregation(Book.class)
                      .match(getDatastore().getQueryFactory().createQuery(getDatastore())
                                           .field("author").equal("Homer"))
                      .group("author", grouping("copies", sum("copies")))
                      .out("testAverage", Author.class);
        MongoCursor<Document> testAverage = getDatabase().getCollection("testAverage").find().iterator();
        Assert.assertNotNull(testAverage);
        try {
            assertEquals(20, testAverage.next().get("copies"));
        } finally {
            testAverage.close();
        }
    }

    @Test
    public void testProjection() {
        getDatastore().save(asList(new Book("The Banquet", "Dante", 2),
                            new Book("Divine Comedy", "Dante", 1),
                            new Book("Eclogues", "Dante", 2),
                            new Book("The Odyssey", "Homer", 10),
                            new Book("Iliad", "Homer", 10)));

        final AggregationPipeline pipeline = getDatastore().createAggregation(Book.class)
                                                           .group("author", grouping("copies", sum("copies")))
                                                           .project(projection("_id").suppress(),
                                                             projection("author", "_id"),
                                                             projection("copies", divide(projection("copies"), 5)))
                                                           .sort(ascending("author"));
        Iterator<Book> aggregate = pipeline.aggregate(Book.class).iterator();
        Book book = aggregate.next();
        assertEquals("Dante", book.author);
        assertEquals(1, book.copies.intValue());

        final List<Document> stages = ((AggregationPipelineImpl) pipeline).getStages();
        assertEquals(stages.get(0), parse("{ \"$group\": {_id: \"$author\", copies: { \"$sum\": \"$copies\"}}}"));
        assertEquals(stages.get(1), parse(("{ \"$project\": {_id: 0, author: \"$_id\", copies: { \"$divide\" : [ \"$copies\", 5 ] }}}")));

    }

    @Test
    public void testSkip() {
        getDatastore().save(asList(new Book("The Banquet", "Dante", 2),
                            new Book("Divine Comedy", "Dante", 1),
                            new Book("Eclogues", "Dante", 2),
                            new Book("The Odyssey", "Homer", 10),
                            new Book("Iliad", "Homer", 10)));

        Book book = getDatastore().createAggregation(Book.class)
                                  .skip(2)
                                  .aggregate(Book.class)
                                  .iterator()
                                  .next();
        assertEquals("Eclogues", book.title);
        assertEquals("Dante", book.author);
        assertEquals(2, book.copies.intValue());
    }

    @Test
    public void testUnwind() throws ParseException {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        getDatastore().save(asList(new User("jane", format.parse("2011-03-02"), "golf", "racquetball"),
                            new User("joe", format.parse("2012-07-02"), "tennis", "golf", "swimming")));

        AggregateIterable<User> aggregate = getDatastore().createAggregation(User.class)
                                                          .project(projection("_id").suppress(),
                                                              projection("name"),
                                                              projection("joined"),
                                                              projection("likes"))
                                                          .unwind("likes")
                                                          .aggregate(User.class);
        int count = 0;
        for (User user : aggregate) {
            switch (count) {
                case 0:
                    assertEquals("jane", user.name);
                    assertEquals("golf", user.likes.get(0));
                    break;
                case 1:
                    assertEquals("jane", user.name);
                    assertEquals("racquetball", user.likes.get(0));
                    break;
                case 2:
                    assertEquals("joe", user.name);
                    assertEquals("tennis", user.likes.get(0));
                    break;
                case 3:
                    assertEquals("joe", user.name);
                    assertEquals("golf", user.likes.get(0));
                    break;
                case 4:
                    assertEquals("joe", user.name);
                    assertEquals("swimming", user.likes.get(0));
                    break;
                default:
                    fail("Should only find 5 elements");
            }
            count++;
        }
    }

    @Test
    public void testUserPreferencesPipeline() {
        final AggregationPipeline pipeline = getDatastore().createAggregation(Book.class)  /* the class is irrelevant for this test */
                                                           .group("state", Group.grouping("total_pop", sum("pop")))
                                                           .match(getDatastore().find(Book.class)
                                                                                .disableValidation()
                                                                                .field("total_pop").greaterThanOrEq(10000000));


        Document group = parse("{ \"$group\": {_id: \"$state\", total_pop: {\"$sum\": \"$pop\"}}}");

        Document match = parse("{ \"$match\": {total_pop: {\"$gte\": 10000000}}}");

        final List<Document> stages = ((AggregationPipelineImpl) pipeline).getStages();
        assertEquals(stages.get(0), group);
        assertEquals(stages.get(1), match);
    }

    @Test
    public void testGroupWithProjection() {
        AggregationPipeline pipeline =
            getDatastore().createAggregation(Author.class)
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
        assertEquals(addToSet.get("sentDate"), "$sentDate");
        assertEquals(addToSet.get("messageId"), "$_id");
    }

    @Test
    public void testAdd() {
        AggregationPipeline pipeline = getDatastore()
            .createAggregation(Book.class)
            .group(grouping("summation",
                            accumulator("$sum", accumulator("$add", asList("$amountFromTBInDouble", "$amountFromParentPNLInDouble"))
                            )));

        Document group = (Document) ((AggregationPipelineImpl) pipeline).getStages().get(0).get("$group");
        Document summation = (Document) group.get("summation");
        Document sum = (Document) summation.get("$sum");
        List<?> add = (List<?>) sum.get("$add");
        Assert.assertTrue(add.get(0) instanceof String);
        assertEquals("$amountFromTBInDouble", add.get(0));
        pipeline.aggregate(User.class);
    }

    private Document getDocument(final Document document, final String... path) {
        Document current = document;
        for (String step : path) {
            Object next = current.get(step);
            Assert.assertNotNull(format("Could not find %s in \n%s", step, current), next);
            current = (Document) next;
        }
        return current;
    }

    private static class StringDates {
        @Id
        private ObjectId id;
        private String string;
    }

    @Entity(value = "books", useDiscriminator = false)
    public static final class Book {
        @Id
        private ObjectId id;
        private String title;
        private String author;
        private Integer copies;
        private List<String> tags;

        private Book() {
        }

        public Book(final String title, final String author, final Integer copies, final String... tags) {
            this.title = title;
            this.author = author;
            this.copies = copies;
            this.tags = asList(tags);
        }

        @Override
        public String toString() {
            return format("Book{title='%s', author='%s', copies=%d, tags=%s}", title, author, copies, tags);
        }
    }

    @Entity("authors")
    public static class Author {
        @Id
        private String name;
        private List<String> books;
    }

    @Entity("users")
    @Validation("{ age : { $gte : 13 } }")
    private static final class User {
        @Id
        private ObjectId id;
        private String name;
        private Date joined;
        private List<String> likes;
        private int age;

        private User() {
        }

        private User(final String name, final Date joined, final String... likes) {
            this.name = name;
            this.joined = joined;
            this.likes = asList(likes);
        }

        @Override
        public String toString() {
            return format("User{name='%s', joined=%s, likes=%s}", name, joined, likes);
        }
    }

    @Entity("counts")
    public static class CountResult {

        @Id
        private String author;
        private int count;

        public String getAuthor() {
            return author;
        }

        public int getCount() {
            return count;
        }
    }

    @Entity("orders")
    private static class Order {
        @Id
        private int id;
        private String item;
        private int price;
        private int quantity;
        private List<Inventory> inventoryDocs;

        private Order() {
        }

        Order(final int id) {
            this.id = id;
        }

        Order(final int id, final String item, final int price, final int quantity) {
            this.id = id;
            this.item = item;
            this.price = price;
            this.quantity = quantity;
        }

        public List<Inventory> getInventoryDocs() {
            return inventoryDocs;
        }

        public void setInventoryDocs(final List<Inventory> inventoryDocs) {
            this.inventoryDocs = inventoryDocs;
        }

        public String getItem() {
            return item;
        }

        public void setItem(final String item) {
            this.item = item;
        }

        public int getPrice() {
            return price;
        }

        public void setPrice(final int price) {
            this.price = price;
        }

        public int getQuantity() {
            return quantity;
        }

        public void setQuantity(final int quantity) {
            this.quantity = quantity;
        }

        public int getId() {
            return id;
        }

        public void setId(final int id) {
            this.id = id;
        }

        @Override
        public int hashCode() {
            int result = id;
            result = 31 * result + (item != null ? item.hashCode() : 0);
            result = 31 * result + price;
            result = 31 * result + quantity;
            return result;
        }        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Order)) {
                return false;
            }

            final Order order = (Order) o;

            if (id != order.id) {
                return false;
            }
            if (price != order.price) {
                return false;
            }
            if (quantity != order.quantity) {
                return false;
            }
            return item != null ? item.equals(order.item) : order.item == null;

        }


    }

    @Entity(value = "inventory", useDiscriminator = false)
    public static class Inventory {
        @Id
        private int id;
        private String sku;
        private String description;
        private int instock;

        public Inventory() {
        }

        Inventory(final int id) {
            this.id = id;
        }

        Inventory(final int id, final String sku, final String description) {
            this.id = id;
            this.sku = sku;
            this.description = description;
        }

        Inventory(final int id, final String sku, final String description, final int instock) {
            this.id = id;
            this.sku = sku;
            this.description = description;
            this.instock = instock;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(final String description) {
            this.description = description;
        }

        public int getInstock() {
            return instock;
        }

        public void setInstock(final int instock) {
            this.instock = instock;
        }

        public String getSku() {
            return sku;
        }

        public void setSku(final String sku) {
            this.sku = sku;
        }

        public int getId() {
            return id;
        }

        public void setId(final int id) {
            this.id = id;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Inventory)) {
                return false;
            }

            final Inventory inventory = (Inventory) o;

            if (id != inventory.id) {
                return false;
            }
            if (instock != inventory.instock) {
                return false;
            }
            if (sku != null ? !sku.equals(inventory.sku) : inventory.sku != null) {
                return false;
            }
            return description != null ? description.equals(inventory.description) : inventory.description == null;

        }

        @Override
        public int hashCode() {
            int result = id;
            result = 31 * result + (sku != null ? sku.hashCode() : 0);
            result = 31 * result + (description != null ? description.hashCode() : 0);
            result = 31 * result + instock;
            return result;
        }
    }
}
