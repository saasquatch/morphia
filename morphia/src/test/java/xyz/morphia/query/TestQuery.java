/*
 * Copyright (C) 2010 Olafur Gauti Gudmundsson
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may
 * obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */


package xyz.morphia.query;


import com.jayway.awaitility.Awaitility;
import com.mongodb.CursorType;
import com.mongodb.MongoException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.CollationStrength;
import com.mongodb.client.model.CountOptions;
import com.mongodb.client.model.FindOptions;
import org.bson.Document;
import org.bson.types.CodeWithScope;
import org.bson.types.ObjectId;
import org.junit.After;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import xyz.morphia.Key;
import xyz.morphia.TestBase;
import xyz.morphia.TestDatastore.FacebookUser;
import xyz.morphia.TestDatastore.KeysKeysKeys;
import xyz.morphia.TestMapper.CustomId;
import xyz.morphia.TestMapper.UsesCustomIdObject;
import xyz.morphia.annotations.CappedAt;
import xyz.morphia.annotations.Embedded;
import xyz.morphia.annotations.Entity;
import xyz.morphia.annotations.Id;
import xyz.morphia.annotations.IndexOptions;
import xyz.morphia.annotations.Indexed;
import xyz.morphia.annotations.PrePersist;
import xyz.morphia.annotations.Property;
import xyz.morphia.annotations.Reference;
import xyz.morphia.mapping.Mapper;
import xyz.morphia.testmodel.Hotel;
import xyz.morphia.testmodel.Rectangle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static com.mongodb.client.model.Collation.builder;
import static java.util.Arrays.asList;
import static java.util.Arrays.copyOfRange;
import static java.util.Collections.singletonList;
import static org.bson.Document.parse;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static xyz.morphia.query.Sort.ascending;
import static xyz.morphia.query.Sort.descending;
import static xyz.morphia.query.Sort.naturalAscending;
import static xyz.morphia.query.Sort.naturalDescending;


@SuppressWarnings({"unchecked", "unused"})
public class TestQuery extends TestBase {

    @Test
    public void multiKeyValueQueries() {
        getMapper().map(KeyValue.class);
        getDatastore().ensureIndexes(KeyValue.class);
        final KeyValue value = new KeyValue();
        final List<Object> keys = Arrays.asList("key1", "key2");
        value.key = keys;
        getDatastore().save(value);

        final Query<KeyValue> query = getDatastore().find(KeyValue.class).field("key").hasAnyOf(keys);
        Assert.assertEquals(query.getQueryDocument(), parse("{\"key\": {\"$in\": [\"key1\", \"key2\"]}}"));
        assertEquals(query.get().id, value.id);
    }

    @Override
    @After
    public void tearDown() {
        turnOffProfilingAndDropProfileCollection();
        super.tearDown();
    }

    private void turnOffProfilingAndDropProfileCollection() {
        getDatabase().runCommand(new Document("profile", 0));
        getDatabase().getCollection("system.profile").drop();
    }

    @Test
    public void testAliasedFieldSort() {
        getDatastore().saveMany(asList(new Rectangle(1, 10),
            new Rectangle(3, 8),
            new Rectangle(6, 10),
            new Rectangle(10, 10),
            new Rectangle(10, 1)));

        Rectangle r1 = getDatastore().find(Rectangle.class)
                                     .order(ascending("w"))
                                     .get(new FindOptions()
                                              .limit(1));
        assertNotNull(r1);
        assertEquals(1, r1.getWidth(), 0);

        r1 = getDatastore().find(Rectangle.class)
                           .order(descending("w"))
                           .get(new FindOptions()
                                    .limit(1));
        assertNotNull(r1);
        assertEquals(10, r1.getWidth(), 0);
    }

    @Test
    public void testCaseVariants() {
        getDatastore().saveMany(asList(new Pic("pic1"), new Pic("pic2"), new Pic("pic3"), new Pic("pic4")));

        assertEquals(0, getDatastore().find(Pic.class)
                                      .field("name").contains("PIC")
                                      .asList()
                                      .size());
        assertEquals(4, getDatastore().find(Pic.class)
                                      .field("name").containsIgnoreCase("PIC")
                                      .asList()
                                      .size());

        assertEquals(0, getDatastore().find(Pic.class)
                                      .field("name").equal("PIC1")
                                      .asList()
                                      .size());
        assertEquals(1, getDatastore().find(Pic.class)
                                      .field("name").equalIgnoreCase("PIC1")
                                      .asList()
                                      .size());

        assertEquals(0, getDatastore().find(Pic.class)
                                      .field("name").endsWith("C1")
                                      .asList()
                                      .size());
        assertEquals(1, getDatastore().find(Pic.class)
                                      .field("name").endsWithIgnoreCase("C1")
                                      .asList()
                                      .size());

        assertEquals(0, getDatastore().find(Pic.class)
                                      .field("name").startsWith("PIC")
                                      .asList()
                                      .size());
        assertEquals(4, getDatastore().find(Pic.class)
                                      .field("name").startsWithIgnoreCase("PIC")
                                      .asList()
                                      .size());
    }

    @Test
    public void testCollations() {
        checkMinServerVersion(3.4);

        getMapper().map(ContainsRenamedFields.class);
        getDatastore().saveMany(asList(new ContainsRenamedFields("first", "last"),
            new ContainsRenamedFields("First", "Last")));

        Query query = getDatastore().find(ContainsRenamedFields.class)
                                    .field("last_name").equal("last");
        assertEquals(1, query.asList().size());
        assertEquals(2, query.asList(new FindOptions()
                                         .collation(builder()
                                                        .locale("en")
                                                        .collationStrength(CollationStrength.SECONDARY)
                                                        .build()))
                             .size());
        assertEquals(1, query.count());
        assertEquals(2, query.count(new CountOptions()
                                        .collation(builder()
                                                       .locale("en")
                                                       .collationStrength(CollationStrength.SECONDARY)
                                                       .build())));
    }

    @Test
    public void testCombinationQuery() {
        getDatastore().saveMany(asList(new Rectangle(1, 10),
            new Rectangle(4, 2),
            new Rectangle(6, 10),
            new Rectangle(8, 5),
            new Rectangle(10, 4)));

        Query<Rectangle> q = getDatastore().find(Rectangle.class);
        q.and(q.criteria("width").equal(10), q.criteria("height").equal(1));
        assertEquals(1, getDatastore().getCount(q));

        q = getDatastore().find(Rectangle.class);
        q.or(q.criteria("width").equal(10), q.criteria("height").equal(10));
        assertEquals(3, getDatastore().getCount(q));

        q = getDatastore().find(Rectangle.class);
        q.or(q.criteria("width").equal(10), q.and(q.criteria("width").equal(5), q.criteria("height").equal(8)));
        assertEquals(3, getDatastore().getCount(q));
    }

    @Test
    public void testCommentsShowUpInLogs() {
        getDatastore().saveMany(asList(new Pic("pic1"), new Pic("pic2"), new Pic("pic3"), new Pic("pic4")));

        getDatabase().runCommand(new Document("profile", 2));
        String expectedComment = "test comment";

        getDatastore().find(Pic.class)
                      .asList(new FindOptions()
                                  .comment(expectedComment));

        MongoCollection<Document> profileCollection = getDatabase().getCollection("system.profile");
        assertNotEquals(0, profileCollection.countDocuments());
        final Document query = new Document("op", "query")
                                   .append("ns", getDatastore().getCollection(Pic.class).getNamespace().getFullName());
        List<Document> profileRecord = profileCollection.find(query)
                                                        .into(new ArrayList<>());
        assertEquals(profileRecord.toString(), expectedComment, getCommentFromProfileRecord(profileRecord.get(0)));

        turnOffProfilingAndDropProfileCollection();
    }

    private String getCommentFromProfileRecord(final Document profileRecord) {
        if (profileRecord.containsKey("command")) {
            Document commandDocument = ((Document) profileRecord.get("command"));
            if (commandDocument.containsKey("comment")) {
                return (String) commandDocument.get("comment");
            }
        }
        if (profileRecord.containsKey("query")) {
            Document queryDocument = ((Document) profileRecord.get("query"));
            if (queryDocument.containsKey("comment")) {
                return (String) queryDocument.get("comment");
            } else if (queryDocument.containsKey("$comment")) {
                return (String) queryDocument.get("$comment");
            }
        }
        return null;
    }

    @Test
    public void testComplexElemMatchQuery() {
        Keyword oscar = new Keyword("Oscar", 42);
        getDatastore().save(new PhotoWithKeywords(oscar, new Keyword("Jim", 12)));
        assertNull(getDatastore().find(PhotoWithKeywords.class)
                                 .field("keywords")
                                 .elemMatch(getDatastore()
                                                .find(Keyword.class)
                                                .filter("keyword = ", "Oscar")
                                                .filter("score = ", 12))
                                 .get());

        List<PhotoWithKeywords> keywords = getDatastore().find(PhotoWithKeywords.class)
                                                         .field("keywords")
                                                         .elemMatch(getDatastore()
                                                                        .find(Keyword.class)
                                                                        .filter("score > ", 20)
                                                                        .filter("score < ", 100))
                                                         .asList();
        assertEquals(1, keywords.size());
        assertEquals(oscar, keywords.get(0).keywords.get(0));
    }

    @Test
    public void testComplexIdQuery() {
        final CustomId cId = new CustomId();
        cId.setId(new ObjectId());
        cId.setType("banker");

        final UsesCustomIdObject object = new UsesCustomIdObject();
        object.setId(cId);
        object.setText("hllo");
        getDatastore().save(object);

        assertNotNull(getDatastore().find(UsesCustomIdObject.class).filter("_id.type", "banker").get());

        assertNotNull(getDatastore().find(UsesCustomIdObject.class).field("_id").hasAnyOf(singletonList(cId)).get());
    }

    @Test
    public void testComplexIdQueryWithRenamedField() {
        final CustomId cId = new CustomId();
        cId.setId(new ObjectId());
        cId.setType("banker");

        final UsesCustomIdObject object = new UsesCustomIdObject();
        object.setId(cId);
        object.setText("hllo");
        getDatastore().save(object);

        assertNotNull(getDatastore().find(UsesCustomIdObject.class).filter("_id.t", "banker").get());
    }

    @Test
    public void testComplexRangeQuery() {
        getDatastore().saveMany(asList(new Rectangle(1, 10),
            new Rectangle(4, 2),
            new Rectangle(6, 10),
            new Rectangle(8, 5),
            new Rectangle(10, 4)));

        assertEquals(2, getDatastore().getCount(getDatastore().find(Rectangle.class)
                                                              .filter("height >", 3)
                                                              .filter("height <", 8)));
        assertEquals(1, getDatastore().getCount(getDatastore().find(Rectangle.class)
                                                              .filter("height >", 3)
                                                              .filter("height <", 8)
                                                              .filter("width", 10)));
    }

    @Test
    public void testCompoundSort() {
        getDatastore().saveMany(asList(new Rectangle(1, 10),
            new Rectangle(3, 8),
            new Rectangle(6, 10),
            new Rectangle(10, 10),
            new Rectangle(10, 1)));

        Rectangle r1 = getDatastore().find(Rectangle.class).order(ascending("width"), descending("height")).get();
        assertNotNull(r1);
        assertEquals(1, r1.getWidth(), 0);
        assertEquals(10, r1.getHeight(), 0);

        r1 = getDatastore().find(Rectangle.class).order(descending("height"), descending("width")).get();
        assertNotNull(r1);
        assertEquals(10, r1.getWidth(), 0);
        assertEquals(10, r1.getHeight(), 0);
    }

    @Test
    public void testCompoundSortWithSortBeans() {
        List<Rectangle> list =
            asList(new Rectangle(1, 10),
                new Rectangle(3, 8),
                new Rectangle(6, 10),
                new Rectangle(10, 10),
                new Rectangle(10, 1));
        Collections.shuffle(list);
        getDatastore().saveMany(list);

        compareLists(list,
            getDatastore().find(Rectangle.class).order(ascending("width"), descending("height")),
            new RectangleComparator());
        compareLists(list,
            getDatastore().find(Rectangle.class).order(descending("height"), descending("width")),
            new RectangleComparator1());
        compareLists(list,
            getDatastore().find(Rectangle.class).order(ascending("width"), ascending("height")),
            new RectangleComparator2());
        compareLists(list,
            getDatastore().find(Rectangle.class).order(ascending("width"), ascending("height")),
            new RectangleComparator3());
    }

    private void compareLists(final List<Rectangle> list, final Query<Rectangle> query, final Comparator<Rectangle> comparator) {
        list.sort(comparator);
        assertEquals(query.asList(), list);
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testCorrectQueryForNotWithSizeEqIssue514() {
        Query<PhotoWithKeywords> query = getAds()
                                             .find(PhotoWithKeywords.class)
                                             .field("keywords").not().sizeEq(3);

        assertEquals(new Document("keywords", new Document("$not", new Document("$size", 3))), query.getQueryDocument());
    }

    @Test
    public void testDocumentOrQuery() {
        getDatastore().save(new PhotoWithKeywords("scott", "hernandez"));

        final List<Document> orList = new ArrayList<>();
        orList.add(new Document("keywords.keyword", "scott"));
        orList.add(new Document("keywords.keyword", "ralph"));
        final Document orQuery = new Document("$or", orList);

        Query<PhotoWithKeywords> q = getAds().createQuery(PhotoWithKeywords.class, orQuery);
        assertEquals(1, q.count());

        q = getAds().find(PhotoWithKeywords.class).disableValidation().filter("$or", orList);
        assertEquals(1, q.count());
    }

    @Test
    public void testDeepQuery() {
        getDatastore().save(new PhotoWithKeywords(new Keyword("california"), new Keyword("nevada"), new Keyword("arizona")));
        assertNotNull(getDatastore().find(PhotoWithKeywords.class).filter("keywords.keyword", "california").get());
        assertNull(getDatastore().find(PhotoWithKeywords.class).filter("keywords.keyword", "not").get());
    }

    @Test
    public void testDeepQueryWithBadArgs() {
        getDatastore().save(new PhotoWithKeywords(new Keyword("california"), new Keyword("nevada"), new Keyword("arizona")));
        assertNull(getDatastore().find(PhotoWithKeywords.class).filter("keywords.keyword", 1).get());
        assertNull(getDatastore().find(PhotoWithKeywords.class).filter("keywords.keyword", "california".getBytes()).get());
        assertNull(getDatastore().find(PhotoWithKeywords.class).filter("keywords.keyword", null).get());
    }

    @Test
    public void testDeepQueryWithRenamedFields() {
        getDatastore().save(new PhotoWithKeywords(new Keyword("california"), new Keyword("nevada"), new Keyword("arizona")));
        assertNotNull(getDatastore().find(PhotoWithKeywords.class).filter("keywords.keyword", "california").get());
        assertNull(getDatastore().find(PhotoWithKeywords.class).filter("keywords.keyword", "not").get());
    }

    @Test
    public void testDeleteQuery() {
        getDatastore().saveMany(asList(new Rectangle(1, 10),
            new Rectangle(1, 10),
            new Rectangle(1, 10),
            new Rectangle(10, 10),
            new Rectangle(10, 10)));

        assertEquals(5, getDatastore().getCount(Rectangle.class));
        getDatastore().deleteMany(getDatastore().find(Rectangle.class).filter("height", 1D));
        assertEquals(2, getDatastore().getCount(Rectangle.class));
    }

    @Test
    public void testElemMatchQuery() {
        getDatastore().saveMany(asList(new PhotoWithKeywords(), new PhotoWithKeywords("Scott", "Joe", "Sarah")));
        assertNotNull(getDatastore().find(PhotoWithKeywords.class)
                                    .field("keywords").elemMatch(getDatastore().find(Keyword.class).filter("keyword", "Scott"))
                                    .get());
        assertNull(getDatastore().find(PhotoWithKeywords.class)
                                 .field("keywords").elemMatch(getDatastore().find(Keyword.class).filter("keyword", "Randy"))
                                 .get());
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testElemMatchVariants() {
        final PhotoWithKeywords pwk1 = new PhotoWithKeywords();
        final PhotoWithKeywords pwk2 = new PhotoWithKeywords("Kevin");
        final PhotoWithKeywords pwk3 = new PhotoWithKeywords("Scott", "Joe", "Sarah");
        final PhotoWithKeywords pwk4 = new PhotoWithKeywords(new Keyword("Scott", 14));

        Iterator<Key<PhotoWithKeywords>> iterator = getDatastore().saveMany(asList(pwk1, pwk2, pwk3, pwk4)).iterator();
        Key<PhotoWithKeywords> key1 = iterator.next();
        Key<PhotoWithKeywords> key2 = iterator.next();
        Key<PhotoWithKeywords> key3 = iterator.next();
        Key<PhotoWithKeywords> key4 = iterator.next();

        assertEquals(asList(key3, key4), getDatastore().find(PhotoWithKeywords.class)
                                                       .field("keywords")
                                                       .elemMatch(getDatastore().find(Keyword.class)
                                                                                .filter("keyword = ", "Scott"))
                                                       .asKeyList());

        assertEquals(asList(key3, key4), getDatastore().find(PhotoWithKeywords.class)
                                                       .field("keywords")
                                                       .elemMatch(getDatastore()
                                                                      .find(Keyword.class)
                                                                      .field("keyword").equal("Scott"))
                                                       .asKeyList());

        assertEquals(singletonList(key4), getDatastore().find(PhotoWithKeywords.class)
                                                        .field("keywords")
                                                        .elemMatch(getDatastore().find(Keyword.class)
                                                                                 .filter("score = ", 14))
                                                        .asKeyList());

        assertEquals(singletonList(key4), getDatastore().find(PhotoWithKeywords.class)
                                                        .field("keywords")
                                                        .elemMatch(getDatastore()
                                                                       .find(Keyword.class)
                                                                       .field("score").equal(14))
                                                        .asKeyList());

        assertEquals(asList(key1, key2), getDatastore().find(PhotoWithKeywords.class)
                                                       .field("keywords")
                                                       .not()
                                                       .elemMatch(getDatastore().find(Keyword.class)
                                                                                .filter("keyword = ", "Scott"))
                                                       .asKeyList());

        assertEquals(asList(key1, key2), getDatastore().find(PhotoWithKeywords.class)
                                                       .field("keywords").not()
                                                       .elemMatch(getDatastore()
                                                                      .find(Keyword.class)
                                                                      .field("keyword").equal("Scott"))
                                                       .asKeyList());
    }

    @Test
    public void testExplainPlan() {
        getDatastore().saveMany(asList(new Pic("pic1"), new Pic("pic2"), new Pic("pic3"), new Pic("pic4")));
        Map<String, Object> explainResult = getDatastore().find(Pic.class).explain();
        assertEquals(explainResult.toString(), 4, ((Map) explainResult.get("executionStats")).get("nReturned"));
    }

    @Test
    public void testFetchEmptyEntities() {
        PhotoWithKeywords pwk1 = new PhotoWithKeywords("california", "nevada", "arizona");
        PhotoWithKeywords pwk2 = new PhotoWithKeywords("Joe", "Sarah");
        PhotoWithKeywords pwk3 = new PhotoWithKeywords("MongoDB", "World");
        getDatastore().saveMany(asList(pwk1, pwk2, pwk3));

        MongoCursor<PhotoWithKeywords> keys = getDatastore().find(PhotoWithKeywords.class).fetchEmptyEntities();
        assertTrue(keys.hasNext());
        Set<ObjectId> set = new HashSet<>();
        while (keys.hasNext()) {
            set.add(keys.next().id);
        }
        assertEquals(set.toString(), new HashSet<>(asList(pwk1.id, pwk2.id, pwk3.id)), set);
    }

    @Test
    public void testFetchKeys() {
        PhotoWithKeywords pwk1 = new PhotoWithKeywords("california", "nevada", "arizona");
        PhotoWithKeywords pwk2 = new PhotoWithKeywords("Joe", "Sarah");
        PhotoWithKeywords pwk3 = new PhotoWithKeywords("MongoDB", "World");
        getDatastore().saveMany(asList(pwk1, pwk2, pwk3));

        MorphiaKeyIterator<PhotoWithKeywords> keys = getDatastore().find(PhotoWithKeywords.class).fetchKeys();
        assertTrue(keys.hasNext());
        Set<ObjectId> set = new HashSet<>();
        while (keys.hasNext()) {
            set.add((ObjectId) ((MorphiaKeyIterator<?>) keys).next().getId());
        }
        assertEquals(set.toString(), new HashSet<>(asList(pwk1.id, pwk2.id, pwk3.id)), set);
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testFluentAndOrQuery() {
        getDatastore().save(new PhotoWithKeywords("scott", "hernandez"));

        final Query<PhotoWithKeywords> q = getAds().find(PhotoWithKeywords.class);
        q.and(
            q.or(q.criteria("keywords.keyword").equal("scott")),
            q.or(q.criteria("keywords.keyword").equal("hernandez")));

        assertEquals(1, q.count());
        assertTrue(q.getQueryDocument().containsKey("$and"));
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testFluentAndQuery1() {
        getDatastore().save(new PhotoWithKeywords("scott", "hernandez"));

        final Query<PhotoWithKeywords> q = getAds().find(PhotoWithKeywords.class);
        q.and(q.criteria("keywords.keyword").hasThisOne("scott"),
            q.criteria("keywords.keyword").hasAnyOf(asList("scott", "hernandez")));

        assertEquals(1, q.count());
        assertTrue(q.getQueryDocument().containsKey("$and"));

    }

    @Test
    public void testFluentNotQuery() {
        final PhotoWithKeywords pwk = new PhotoWithKeywords("scott", "hernandez");
        getDatastore().save(pwk);

        final Query<PhotoWithKeywords> query = getAds().find(PhotoWithKeywords.class);
        query.criteria("keywords.keyword").not().startsWith("ralph");

        assertEquals(1, query.count());
    }

    @Test
    public void testFluentOrQuery() {
        final PhotoWithKeywords pwk = new PhotoWithKeywords("scott", "hernandez");
        getDatastore().save(pwk);

        final Query<PhotoWithKeywords> q = getAds().find(PhotoWithKeywords.class);
        q.or(
            q.criteria("keywords.keyword").equal("scott"),
            q.criteria("keywords.keyword").equal("ralph"));

        assertEquals(1, q.count());
    }

    @Test
    public void testGetByKeysHetero() {
        final List<Key<Object>> keys = getDatastore().saveMany(
            asList(new FacebookUser(1, "scott"), new Rectangle(1, 1)));
        final List<Object> entities = getDatastore().getByKeys(keys);
        assertNotNull(entities);
        assertEquals(2, entities.size());
        int userCount = 0;
        int rectCount = 0;
        for (final Object o : entities) {
            if (o instanceof Rectangle) {
                rectCount++;
            } else if (o instanceof FacebookUser) {
                userCount++;
            }
        }
        assertEquals(1, rectCount);
        assertEquals(1, userCount);
    }

    @Test
    public void testIdFieldNameQuery() {
        getDatastore().save(new PhotoWithKeywords("scott", "hernandez"));

        assertNotNull(getDatastore().find(PhotoWithKeywords.class).filter("id !=", "scott").get());
    }

    @Test
    public void testIdRangeQuery() {
        getDatastore().saveMany(asList(new HasIntId(1), new HasIntId(11), new HasIntId(12)));
        assertEquals(2, getDatastore().find(HasIntId.class).filter("_id >", 5).filter("_id <", 20).count());
        assertEquals(1, getDatastore().find(HasIntId.class).field("_id").greaterThan(0).field("_id").lessThan(11).count());
    }

    @Test
    public void testInQuery() {
        getDatastore().save(new Photo(asList("red", "green", "blue")));

        assertNotNull(getDatastore()
                          .find(Photo.class)
                          .field("keywords").in(asList("red", "yellow"))
                          .get());
    }

    @Test
    public void testInQueryWithObjects() {
        getDatastore().saveMany(asList(new PhotoWithKeywords(), new PhotoWithKeywords("Scott", "Joe", "Sarah")));

        final Query<PhotoWithKeywords> query = getDatastore()
                                                   .find(PhotoWithKeywords.class)
                                                   .field("keywords").in(asList(new Keyword("Scott"), new Keyword("Randy")));
        assertNotNull(query.get());
    }

    @Test
    public void testKeyList() {
        final Rectangle rect = new Rectangle(1000, 1);
        final Key<Rectangle> rectKey = getDatastore().save(rect);

        assertEquals(rectKey.getId(), rect.getId());

        final FacebookUser fbUser1 = new FacebookUser(1, "scott");
        final FacebookUser fbUser2 = new FacebookUser(2, "tom");
        final FacebookUser fbUser3 = new FacebookUser(3, "oli");
        final FacebookUser fbUser4 = new FacebookUser(4, "frank");
        final Iterable<Key<FacebookUser>> fbKeys = getDatastore().saveMany(asList(fbUser1, fbUser2, fbUser3, fbUser4));
        assertEquals(1, fbUser1.getId());

        final List<Key<FacebookUser>> fbUserKeys = new ArrayList<>();
        for (final Key<FacebookUser> key : fbKeys) {
            fbUserKeys.add(key);
        }

        assertEquals(fbUser1.getId(), fbUserKeys.get(0).getId());
        assertEquals(fbUser2.getId(), fbUserKeys.get(1).getId());
        assertEquals(fbUser3.getId(), fbUserKeys.get(2).getId());
        assertEquals(fbUser4.getId(), fbUserKeys.get(3).getId());

        final KeysKeysKeys k1 = new KeysKeysKeys(rectKey, fbUserKeys);
        final Key<KeysKeysKeys> k1Key = getDatastore().save(k1);
        assertEquals(k1.getId(), k1Key.getId());

        final KeysKeysKeys k1Loaded = getDatastore().get(k1);
        for (final Key<FacebookUser> key : k1Loaded.getUsers()) {
            assertNotNull(key.getId());
        }

        assertNotNull(k1Loaded.getRect().getId());
    }

    @Test
    public void testKeyListLookups() {
        final FacebookUser fbUser1 = new FacebookUser(1, "scott");
        final FacebookUser fbUser2 = new FacebookUser(2, "tom");
        final FacebookUser fbUser3 = new FacebookUser(3, "oli");
        final FacebookUser fbUser4 = new FacebookUser(4, "frank");
        final Iterable<Key<FacebookUser>> fbKeys = getDatastore().saveMany(asList(fbUser1, fbUser2, fbUser3, fbUser4));
        assertEquals(1, fbUser1.getId());

        final List<Key<FacebookUser>> fbUserKeys = new ArrayList<>();
        for (final Key<FacebookUser> key : fbKeys) {
            fbUserKeys.add(key);
        }

        assertEquals(fbUser1.getId(), fbUserKeys.get(0).getId());
        assertEquals(fbUser2.getId(), fbUserKeys.get(1).getId());
        assertEquals(fbUser3.getId(), fbUserKeys.get(2).getId());
        assertEquals(fbUser4.getId(), fbUserKeys.get(3).getId());

        final KeysKeysKeys k1 = new KeysKeysKeys(null, fbUserKeys);
        final Key<KeysKeysKeys> k1Key = getDatastore().save(k1);
        assertEquals(k1.getId(), k1Key.getId());

        final KeysKeysKeys k1Reloaded = getDatastore().get(k1);
        final KeysKeysKeys k1Loaded = getDatastore().getByKey(KeysKeysKeys.class, k1Key);
        assertNotNull(k1Reloaded);
        assertNotNull(k1Loaded);
        for (final Key<FacebookUser> key : k1Loaded.getUsers()) {
            assertNotNull(key.getId());
        }

        assertEquals(4, k1Loaded.getUsers().size());

        final List<FacebookUser> fbUsers = getDatastore().getByKeys(FacebookUser.class, k1Loaded.getUsers());
        assertEquals(4, fbUsers.size());
        for (final FacebookUser fbUser : fbUsers) {
            assertNotNull(fbUser);
            assertNotNull(fbUser.getUsername());
        }
    }

    @Test
    public void testMixedProjection() {
        getDatastore().save(new ContainsRenamedFields("Frank", "Zappa"));

        try {
            getDatastore().find(ContainsRenamedFields.class)
                          .project("first_name", true)
                          .project("last_name", false);
            fail("An exception should have been thrown indication a mixed projection");
        } catch (ValidationException e) {
            // all good
        }

        try {
            getDatastore().find(ContainsRenamedFields.class)
                          .project("first_name", true)
                          .project("last_name", true)
                          .project("_id", false);
        } catch (ValidationException e) {
            fail("An exception should not have been thrown indication a mixed projection because _id suppression is a special case");
        }

        try {
            getDatastore().find(ContainsRenamedFields.class)
                          .project("first_name", false)
                          .project("last_name", false)
                          .project("_id", true);
            fail("An exception should have been thrown indication a mixed projection");
        } catch (ValidationException e) {
            // all good
        }

        try {
            getDatastore().find(IntVector.class)
                          .project("name", false)
                          .project("scalars", new ArraySlice(5));
            fail("An exception should have been thrown indication a mixed projection");
        } catch (ValidationException e) {
            // all good
        }
    }

    @Test
    public void testMultipleConstraintsOnOneField() {
        checkMinServerVersion(3.0);
        getMapper().map(ContainsPic.class);
        getDatastore().ensureIndexes();
        Query<ContainsPic> query = getDatastore().find(ContainsPic.class);
        query.field("size").greaterThanOrEq(10);
        query.field("size").lessThan(100);

        Map<String, Object> explain = query.explain();
        Map<String, Object> queryPlanner = (Map<String, Object>) explain.get("queryPlanner");
        Map<String, Object> winningPlan = (Map<String, Object>) queryPlanner.get("winningPlan");
        Map<String, Object> inputStage = (Map<String, Object>) winningPlan.get("inputStage");
        assertEquals("IXSCAN", inputStage.get("stage"));
    }

    @Test
    public void testNaturalSortAscending() {
        getDatastore().saveMany(asList(new Rectangle(6, 10), new Rectangle(3, 8), new Rectangle(10, 10), new Rectangle(10, 1)));

        List<Rectangle> results = getDatastore()
                                      .find(Rectangle.class)
                                      .order(naturalAscending())
                                      .asList();

        assertEquals(4, results.size());

        Rectangle r;

        r = results.get(0);
        assertNotNull(r);
        assertEquals(6, r.getHeight(), 0);
        assertEquals(10, r.getWidth(), 0);

        r = results.get(1);
        assertNotNull(r);
        assertEquals(3, r.getHeight(), 0);
        assertEquals(8, r.getWidth(), 0);

        r = results.get(2);
        assertNotNull(r);
        assertEquals(10, r.getHeight(), 0);
        assertEquals(10, r.getWidth(), 0);
    }

    @Test
    public void testNaturalSortDescending() {
        getDatastore().saveMany(asList(new Rectangle(6, 10), new Rectangle(3, 8), new Rectangle(10, 10), new Rectangle(10, 1)));

        List<Rectangle> results = getDatastore().find(Rectangle.class).order(naturalDescending()).asList();

        assertEquals(4, results.size());

        Rectangle r;

        r = results.get(0);
        assertNotNull(r);
        assertEquals(10, r.getHeight(), 0);
        assertEquals(1, r.getWidth(), 0);

        r = results.get(1);
        assertNotNull(r);
        assertEquals(10, r.getHeight(), 0);
        assertEquals(10, r.getWidth(), 0);

        r = results.get(2);
        assertNotNull(r);
        assertEquals(3, r.getHeight(), 0);
        assertEquals(8, r.getWidth(), 0);
    }

    @Test
    public void testNegativeBatchSize() {
        getDatastore().deleteMany(getDatastore().find(PhotoWithKeywords.class));
        getDatastore().saveMany(asList(new PhotoWithKeywords("scott", "hernandez"),
            new PhotoWithKeywords("scott", "hernandez"),
            new PhotoWithKeywords("scott", "hernandez"),
            new PhotoWithKeywords("1", "2"),
            new PhotoWithKeywords("3", "4"),
            new PhotoWithKeywords("5", "6")));
        assertEquals(2, getDatastore().find(PhotoWithKeywords.class)
                                      .asList(new FindOptions()
                                                  .batchSize(-2))
                                      .size());
    }

    @Test
    public void testNonexistentFindGet() {
        assertNull(getDatastore().find(Hotel.class).filter("_id", -1).get());
    }

    @Test
    public void testNonexistentGet() {
        assertNull(getDatastore().get(Hotel.class, -1));
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testNotGeneratesCorrectQueryForGreaterThan() {
        final Query<Keyword> query = getDatastore().find(Keyword.class);
        query.criteria("score").not().greaterThan(7);
        assertEquals(new Document("score", new Document("$not", new Document("$gt", 7))), query.getQueryDocument());
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testProject() {
        getDatastore().save(new ContainsRenamedFields("Frank", "Zappa"));

        ContainsRenamedFields found = getDatastore().find(ContainsRenamedFields.class)
                                                    .project("first_name", true)
                                                    .get();
        assertNotNull(found.firstName);
        assertNull(found.lastName);

        found = getDatastore().find(ContainsRenamedFields.class)
                              .project("firstName", true)
                              .get();
        assertNotNull(found.firstName);
        assertNull(found.lastName);

        try {
            getDatastore()
                .find(ContainsRenamedFields.class)
                .project("bad field name", true)
                .get();
            fail("Validation should have caught the bad field");
        } catch (ValidationException e) {
            // success!
        }

        Document fields = getDatastore()
                              .find(ContainsRenamedFields.class)
                              .project("_id", true)
                              .project("first_name", true)
                              .getFields();
        assertNull(fields.get(Mapper.CLASS_NAME_FIELDNAME));
    }

    @Test
    public void testProjectArrayField() {
        int[] ints = {0, 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26, 28, 30};
        IntVector vector = new IntVector(ints);
        getDatastore().save(vector);

        assertArrayEquals(copy(ints, 0, 4), getDatastore().find(IntVector.class)
                                                          .project("scalars", new ArraySlice(4))
                                                          .get().scalars);
        assertArrayEquals(copy(ints, 5, 4), getDatastore().find(IntVector.class)
                                                          .project("scalars", new ArraySlice(5, 4))
                                                          .get().scalars);
        assertArrayEquals(copy(ints, ints.length - 10, 6), getDatastore().find(IntVector.class)
                                                                         .project("scalars", new ArraySlice(-10, 6))
                                                                         .get().scalars);
        assertArrayEquals(copy(ints, ints.length - 12, 12), getDatastore().find(IntVector.class)
                                                                          .project("scalars", new ArraySlice(-12))
                                                                          .get().scalars);
    }

    private int[] copy(final int[] array, final int start, final int count) {
        return copyOfRange(array, start, start + count);
    }

    @Test
    public void testQBE() {
        final CustomId cId = new CustomId();
        cId.setId(new ObjectId());
        cId.setType("banker");

        final UsesCustomIdObject object = new UsesCustomIdObject();
        object.setId(cId);
        object.setText("hllo");
        getDatastore().save(object);
        final UsesCustomIdObject loaded;

        // Add back if/when query by example for embedded fields is supported (require dotting each field).
        // CustomId exId = new CustomId();
        // exId.type = cId.type;
        // loaded = getDs().find(UsesCustomIdObject.class, "_id", exId).get();
        // assertNotNull(loaded);

        final UsesCustomIdObject ex = new UsesCustomIdObject();
        ex.setText(object.getText());
        loaded = getDatastore().queryByExample(ex).get();
        assertNotNull(loaded);
    }

    @Test
    public void testQueryCount() {
        getDatastore().saveMany(asList(new Rectangle(1, 10),
            new Rectangle(1, 10),
            new Rectangle(1, 10),
            new Rectangle(10, 10),
            new Rectangle(10, 10)));

        assertEquals(3, getDatastore().getCount(getDatastore().find(Rectangle.class).filter("height", 1D)));
        assertEquals(2, getDatastore().getCount(getDatastore().find(Rectangle.class).filter("height", 10D)));
        assertEquals(5, getDatastore().getCount(getDatastore().find(Rectangle.class).filter("width", 10D)));

    }

    @Test
    public void testQueryOverLazyReference() {
        final ContainsPic cpk = new ContainsPic();
        final Pic p = new Pic();
        getDatastore().save(p);
        cpk.lazyPic = p;

        getDatastore().save(cpk);

        assertEquals(1, getDatastore().find(ContainsPic.class)
                                      .field("lazyPic").equal(p)
                                      .asList()
                                      .size());
    }

    @Test
    public void testQueryOverReference() {

        final ContainsPic cpk = new ContainsPic();
        final Pic p = new Pic();
        getDatastore().save(p);
        cpk.pic = p;

        getDatastore().save(cpk);

        final Query<ContainsPic> query = getDatastore().find(ContainsPic.class);

        assertEquals(1, query.field("pic").equal(p).asList().size());

        try {
            getDatastore().find(ContainsPic.class).filter("pic.name", "foo").get();
            fail("query validation should have thrown an exception");
        } catch (ValidationException e) {
            assertTrue(e.getMessage().contains("Cannot use dot-"));
        }
    }

    @Test
    public void testRangeQuery() {
        getDatastore().saveMany(asList(new Rectangle(1, 10),
            new Rectangle(4, 2),
            new Rectangle(6, 10),
            new Rectangle(8, 5),
            new Rectangle(10, 4)));

        assertEquals(4, getDatastore().getCount(getDatastore().find(Rectangle.class).filter("height >", 3)));
        assertEquals(3, getDatastore().getCount(getDatastore().find(Rectangle.class).filter("height >", 3)
                                                              .filter("height <", 10)));
        assertEquals(1, getDatastore().getCount(getDatastore().find(Rectangle.class).filter("height >", 9)
                                                              .filter("width <", 5)));
        assertEquals(3, getDatastore().getCount(getDatastore().find(Rectangle.class).filter("height <", 7)));
    }

    @Test(expected = ValidationException.class)
    public void testReferenceQuery() {
        final Photo p = new Photo();
        final ContainsPhotoKey cpk = new ContainsPhotoKey();
        cpk.photo = getDatastore().save(p);
        getDatastore().save(cpk);

        assertNotNull(getDatastore().find(ContainsPhotoKey.class).filter("photo", getMapper().getKey(p)).get());
        assertNotNull(getDatastore().find(ContainsPhotoKey.class).filter("photo", cpk.photo).get());
        assertNull(getDatastore().find(ContainsPhotoKey.class).filter("photo", 1).get());

        getDatastore().find(ContainsPhotoKey.class).filter("photo.keywords", "foo").get();
    }

    @Test
    public void testRegexInsensitiveQuery() {
        getDatastore().save(new PhotoWithKeywords(new Keyword("california"), new Keyword("nevada"), new Keyword("arizona")));
        final Pattern p = Pattern.compile("(?i)caLifornia");
        assertNotNull(getDatastore().find(PhotoWithKeywords.class).disableValidation().filter("keywords.keyword", p).get());
        assertNull(getDatastore().find(PhotoWithKeywords.class).filter("keywords.keyword", Pattern.compile("blah")).get());
    }

    @Test
    public void testRegexQuery() {
        getDatastore().save(new PhotoWithKeywords(new Keyword("california"), new Keyword("nevada"), new Keyword("arizona")));
        assertNotNull(getDatastore().find(PhotoWithKeywords.class)
                                    .disableValidation()
                                    .filter("keywords.keyword", Pattern.compile("california"))
                                    .get());
        assertNull(getDatastore().find(PhotoWithKeywords.class).filter("keywords.keyword", Pattern.compile("blah")).get());
    }

    @Test
    public void testRenamedFieldQuery() {
        getDatastore().save(new ContainsRenamedFields("Scott", "Bakula"));

        assertNotNull(getDatastore().find(ContainsRenamedFields.class).field("firstName").equal("Scott").get());
        assertNotNull(getDatastore().find(ContainsRenamedFields.class).field("first_name").equal("Scott").get());
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testReturnOnlyIndexedFields() {
        getDatastore().saveMany(asList(new Pic("pic1"), new Pic("pic2"), new Pic("pic3"), new Pic("pic4")));
        getDatastore().ensureIndexes();

        Pic foundItem = getDatastore().find(Pic.class)
                                      .field("name").equal("pic2")
                                      .get(new FindOptions()
                                               .returnKey(true)
                                               .limit(1));
        assertNotNull(foundItem);
        assertThat("Name should be populated", foundItem.getName(), is("pic2"));
        assertNull("ID should not be populated", foundItem.getId());
    }

    @Test
    public void testSimpleSort() {
        getDatastore().saveMany(asList(new Rectangle(1, 10),
            new Rectangle(3, 8),
            new Rectangle(6, 10),
            new Rectangle(10, 10),
            new Rectangle(10, 1)));

        Rectangle r1 = getDatastore().find(Rectangle.class)
                                     .order(ascending("width"))
                                     .get();
        assertNotNull(r1);
        assertEquals(1, r1.getWidth(), 0);

        r1 = getDatastore().find(Rectangle.class)
                           .order(descending("width"))
                           .get();
        assertNotNull(r1);
        assertEquals(10, r1.getWidth(), 0);
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testSizeEqQuery() {
        assertEquals(new Document("keywords", new Document("$size", 3)), getDatastore().find(PhotoWithKeywords.class)
                                                                                       .field("keywords")
                                                                                       .sizeEq(3).getQueryDocument());
    }

    @Test
    public void testStartsWithQuery() {
        getDatastore().save(new Photo());
        Photo p = getDatastore().find(Photo.class).field("keywords").startsWith("amaz").get();
        assertNotNull(p);
        p = getDatastore().find(Photo.class).field("keywords").startsWith("notareal").get();
        assertNull(p);

    }

    @Test
    @Ignore("not sure why this is failing but deferring it for more important things")
    public void testTailableCursors() {
        getMapper().map(CappedPic.class);
        getDatastore().ensureCaps();
        final Query<CappedPic> query = getDatastore().find(CappedPic.class);
        final List<CappedPic> found = new ArrayList<>();
        final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);

        assertEquals(0, query.count());

        executorService.scheduleAtFixedRate(
            () -> getDatastore().save(new CappedPic(System.currentTimeMillis() + "")), 0, 500,
            TimeUnit.MILLISECONDS);

        final Iterator<CappedPic> tail = query
                                             .fetch(new FindOptions()
                                                        .cursorType(CursorType.Tailable));
        Awaitility
            .await()
            .pollDelay(500, TimeUnit.MILLISECONDS)
            .atMost(10, TimeUnit.SECONDS)
            .until(() -> {
                if (tail.hasNext()) {
                    found.add(tail.next());
                }
                return found.size() >= 10;
            });
        executorService.shutdownNow();
        Assert.assertTrue(query.count() >= 10);
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testThatElemMatchQueriesOnlyChecksRequiredFields() {
        final PhotoWithKeywords pwk1 = new PhotoWithKeywords(new Keyword("california"),
            new Keyword("nevada"),
            new Keyword("arizona"));
        final PhotoWithKeywords pwk2 = new PhotoWithKeywords("Joe", "Sarah");
        pwk2.keywords.add(new Keyword("Scott", 14));

        getDatastore().saveMany(asList(pwk1, pwk2));

        // In this case, we only want to match on the keyword field, not the
        // score field, which shouldn't be included in the elemMatch query.

        // As a result, the query in MongoDB should look like:
        // find({ keywords: { $elemMatch: { keyword: "Scott" } } })

        // NOT:
        // find({ keywords: { $elemMatch: { keyword: "Scott", score: 12 } } })
        assertNotNull(getDatastore().find(PhotoWithKeywords.class)
                                    .field("keywords")
                                    .elemMatch(getDatastore().find(Keyword.class)
                                                             .filter("keyword = ", "Scott"))
                                    .get());
        assertNotNull(getDatastore().find(PhotoWithKeywords.class)
                                    .field("keywords").elemMatch(getDatastore().find(Keyword.class)
                                                                               .filter("keyword", "Scott"))
                                    .get());

        assertNull(getDatastore().find(PhotoWithKeywords.class)
                                 .field("keywords")
                                 .elemMatch(getDatastore().find(Keyword.class)
                                                          .filter("keyword = ", "Randy"))
                                 .get());
        assertNull(getDatastore().find(PhotoWithKeywords.class)
                                 .field("keywords").elemMatch(getDatastore().find(Keyword.class)
                                                                            .filter("keyword", "Randy"))
                                 .get());
    }

    @Test
    public void testWhereCodeWScopeQuery() {
        getDatastore().save(new PhotoWithKeywords(new Keyword("california"), new Keyword("nevada"), new Keyword("arizona")));
        //        CodeWScope hasKeyword = new CodeWScope("for (kw in this.keywords) { if(kw.keyword == kwd) return true; } return false;
        // ", new Document("kwd","california"));
        final CodeWithScope hasKeyword = new CodeWithScope("this.keywords != null", new Document());
        assertNotNull(getDatastore().find(PhotoWithKeywords.class).where(hasKeyword).get());
    }

    @Test
    public void testWhereStringQuery() {
        getDatastore().save(new PhotoWithKeywords(new Keyword("california"), new Keyword("nevada"), new Keyword("arizona")));
        assertNotNull(getDatastore().find(PhotoWithKeywords.class).where("this.keywords != null").get());
    }

    @Test
    public void testWhereWithInvalidStringQuery() {
        getDatastore().save(new PhotoWithKeywords());
        final CodeWithScope hasKeyword = new CodeWithScope("keywords != null", new Document());
        try {
            // must fail
            assertNotNull(getDatastore().find(PhotoWithKeywords.class).where(hasKeyword.getCode()).get());
            fail("Invalid javascript magically isn't invalid anymore?");
        } catch (MongoException e) {
            // fine
        }

    }

    @Test
    public void testQueryUnmappedData() {
        getMapper().map(Class1.class);
        getDatastore().ensureIndexes(true);

        getDatastore().getDatabase().getCollection("user")
                      .insertOne(new Document()
                                     .append("@class", Class1.class.getName())
                                     .append("value1", "foo")
                                     .append("someMap", new Document("someKey", "value")));

        Query<Class1> query = getDatastore().createQuery(Class1.class);
        query.disableValidation().criteria("someMap.someKey").equal("value");
        Class1 retrievedValue = query.get();
        Assert.assertNotNull(retrievedValue);
        Assert.assertEquals("foo", retrievedValue.value1);
    }

    @Entity(value = "user", useDiscriminator = false)
    private static class Class1 {
        @Id
        private ObjectId id;

        private String value1;

    }

    @Entity
    static class Photo {
        @Id
        private ObjectId id;
        private List<String> keywords = singletonList("amazing");

        Photo() {
        }

        Photo(final List<String> keywords) {
            this.keywords = keywords;
        }
    }

    public static class PhotoWithKeywords {
        @Id
        private ObjectId id;
        private List<Keyword> keywords = new ArrayList<>();

        PhotoWithKeywords() {
        }

        PhotoWithKeywords(final String... words) {
            keywords = new ArrayList<>(words.length);
            for (final String word : words) {
                keywords.add(new Keyword(word));
            }
        }

        PhotoWithKeywords(final Keyword... keyword) {
            keywords.addAll(asList(keyword));
        }
    }

    @Embedded
    public static class Keyword {
        private String keyword;
        private Integer score;

        protected Keyword() {
        }

        Keyword(final String k) {
            this.keyword = k;
        }

        Keyword(final String k, final Integer score) {
            this.keyword = k;
            this.score = score;
        }

        Keyword(final Integer score) {
            this.score = score;
        }

        @Override
        public int hashCode() {
            int result = keyword != null ? keyword.hashCode() : 0;
            result = 31 * result + (score != null ? score.hashCode() : 0);
            return result;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Keyword)) {
                return false;
            }

            final Keyword keyword1 = (Keyword) o;

            if (keyword != null ? !keyword.equals(keyword1.keyword) : keyword1.keyword != null) {
                return false;
            }
            return score != null ? score.equals(keyword1.score) : keyword1.score == null;

        }

    }

    private static class ContainsPhotoKey {
        @Id
        private ObjectId id;
        private Key<Photo> photo;
    }

    @Entity
    public static class HasIntId {
        @Id
        private int id;

        protected HasIntId() {
        }

        HasIntId(final int id) {
            this.id = id;
        }
    }

    @Entity
    public static class ContainsPic {
        @Id
        private ObjectId id;
        private String name = "test";
        @Reference
        private Pic pic;
        @Reference
        private Pic lazyPic;
        @Reference
        private PicWithObjectId lazyObjectIdPic;
        @Indexed
        private int size;

        public ObjectId getId() {
            return id;
        }

        public void setId(final ObjectId id) {
            this.id = id;
        }

        public PicWithObjectId getLazyObjectIdPic() {
            return lazyObjectIdPic;
        }

        public void setLazyObjectIdPic(final PicWithObjectId lazyObjectIdPic) {
            this.lazyObjectIdPic = lazyObjectIdPic;
        }

        public Pic getLazyPic() {
            return lazyPic;
        }

        public void setLazyPic(final Pic lazyPic) {
            this.lazyPic = lazyPic;
        }

        public String getName() {
            return name;
        }

        public void setName(final String name) {
            this.name = name;
        }

        public Pic getPic() {
            return pic;
        }

        public void setPic(final Pic pic) {
            this.pic = pic;
        }

        public int getSize() {
            return size;
        }

        public void setSize(final int size) {
            this.size = size;
        }
    }

    @Entity
    private static class PicWithObjectId {
        @Id
        private ObjectId id;
        private String name;
    }

    @Entity
    public static class Pic {
        @Id
        private ObjectId id;
        @Indexed
        private String name;
        private boolean prePersist;

        public Pic() {
        }

        Pic(final String name) {
            this.name = name;
        }

        public ObjectId getId() {
            return id;
        }

        public void setId(final ObjectId id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(final String name) {
            this.name = name;
        }

        boolean isPrePersist() {
            return prePersist;
        }

        public void setPrePersist(final boolean prePersist) {
            this.prePersist = prePersist;
        }

        @PrePersist
        public void tweak() {
            prePersist = true;
        }
    }

    @Entity(value = "capped_pic", cap = @CappedAt(count = 1000))
    public static class CappedPic extends Pic {
        public CappedPic() {
        }

        CappedPic(final String name) {
            super(name);
        }
    }

    @Entity(useDiscriminator = false)
    public static class ContainsRenamedFields {
        @Id
        private ObjectId id;
        @Property("first_name")
        private String firstName;
        @Property("last_name")
        private String lastName;

        public ContainsRenamedFields() {
        }

        ContainsRenamedFields(final String firstName, final String lastName) {
            this.firstName = firstName;
            this.lastName = lastName;
        }
    }

    @Entity
    private static class KeyValue {
        @Id
        private ObjectId id;
        /**
         * The list of keys for this value.
         */
        @Indexed(options = @IndexOptions(unique = true))
        private List<Object> key;
        /**
         * The id of the value document
         */
        @Indexed
        private ObjectId value;
    }

    @Entity
    private static class GenericKeyValue<T> {

        @Id
        private ObjectId id;

        @Indexed(options = @IndexOptions(unique = true))
        private List<Object> key;

        private T value;
    }

    static class IntVector {
        @Id
        private ObjectId id;
        private String name;
        private int[] scalars;

        IntVector() {
        }

        IntVector(final int... scalars) {
            this.scalars = scalars;
        }
    }

    private static class RectangleComparator implements Comparator<Rectangle> {
        @Override
        public int compare(final Rectangle o1, final Rectangle o2) {
            int compare = Double.compare(o1.getWidth(), o2.getWidth());
            return compare != 0 ? compare : Double.compare(o2.getHeight(), o1.getHeight());
        }
    }

    private static class RectangleComparator1 implements Comparator<Rectangle> {
        @Override
        public int compare(final Rectangle o1, final Rectangle o2) {
            int compare = Double.compare(o2.getHeight(), o1.getHeight());
            return compare != 0 ? compare : Double.compare(o2.getWidth(), o1.getWidth());
        }
    }

    private static class RectangleComparator2 implements Comparator<Rectangle> {
        @Override
        public int compare(final Rectangle o1, final Rectangle o2) {
            int compare = Double.compare(o1.getWidth(), o2.getWidth());
            return compare != 0 ? compare : Double.compare(o1.getHeight(), o2.getHeight());
        }
    }

    private static class RectangleComparator3 implements Comparator<Rectangle> {
        @Override
        public int compare(final Rectangle o1, final Rectangle o2) {
            int compare = Double.compare(o1.getWidth(), o2.getWidth());
            return compare != 0 ? compare : Double.compare(o1.getHeight(), o2.getHeight());
        }
    }
}
