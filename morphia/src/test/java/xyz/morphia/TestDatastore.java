package xyz.morphia;

import com.mongodb.client.model.Collation;
import com.mongodb.client.model.DeleteOptions;
import com.mongodb.client.model.FindOneAndDeleteOptions;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.Assert;
import org.junit.Test;
import xyz.morphia.annotations.Entity;
import xyz.morphia.annotations.EntityListeners;
import xyz.morphia.annotations.Id;
import xyz.morphia.annotations.PostLoad;
import xyz.morphia.annotations.PostPersist;
import xyz.morphia.annotations.PreLoad;
import xyz.morphia.annotations.PrePersist;
import xyz.morphia.annotations.Reference;
import xyz.morphia.annotations.Transient;
import xyz.morphia.generics.model.ChildEmbedded;
import xyz.morphia.generics.model.ChildEntity;
import xyz.morphia.internal.DatastoreImpl;
import xyz.morphia.mapping.Mapper;
import xyz.morphia.query.Query;
import xyz.morphia.query.UpdateException;
import xyz.morphia.query.UpdateOperations;
import xyz.morphia.testmodel.Address;
import xyz.morphia.testmodel.Hotel;
import xyz.morphia.testmodel.Rectangle;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static com.mongodb.ReadPreference.secondaryPreferred;
import static com.mongodb.WriteConcern.ACKNOWLEDGED;
import static com.mongodb.WriteConcern.UNACKNOWLEDGED;
import static com.mongodb.client.model.CollationStrength.SECONDARY;
import static com.mongodb.client.model.ReturnDocument.AFTER;
import static com.mongodb.client.model.ReturnDocument.BEFORE;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@SuppressWarnings("unused")
public class TestDatastore extends TestBase {

    @Test(expected = UpdateException.class)
    public void saveNull() {
        getDatastore().save((Hotel) null);
    }

    @Test
    @SuppressWarnings("deprecation")
    public void saveVarargs() {
        Iterable<Key<FacebookUser>> keys = getDatastore().saveMany(asList(new FacebookUser(1, "user 1"),
                                                        new FacebookUser(2, "user 2"),
                                                        new FacebookUser(3, "user 3"),
                                                        new FacebookUser(4, "user 4")));
        long id = 1;
        for (final Key<FacebookUser> key : keys) {
            assertEquals(id++, key.getId());
        }
        assertEquals(5, id);
        assertEquals(4, getDatastore().find(FacebookUser.class).count());

    }

    @Test
    public void shouldSaveGenericTypeVariables() {
        // given
        ChildEntity child = new ChildEntity();
        child.setEmbeddedList(singletonList(new ChildEmbedded()));

        // when
        Key<ChildEntity> saveResult = getDatastore().save(child);

        // then
        assertNotEquals(null, saveResult);
    }

    @Test
    public void testCollectionNames() {
        assertEquals("facebook_users", getMapper().getCollectionName(FacebookUser.class));
    }

    @Test
    public void testEmbedded() {
        getDatastore().deleteMany(getDatastore().find(Hotel.class));
        final Hotel borg = new Hotel();
        borg.setName("Hotel Borg");
        borg.setStars(4);
        borg.setTakesCreditCards(true);
        borg.setStartDate(new Date());
        borg.setType(Hotel.Type.LEISURE);
        final Address address = new Address();
        address.setStreet("Posthusstraeti 11");
        address.setPostCode("101");
        borg.setAddress(address);


        getDatastore().save(borg);
        assertEquals(1, getDatastore().find(Hotel.class).count());
        assertNotNull(borg.getId());

        final Hotel hotelLoaded = getDatastore().find(Hotel.class).filter("_id", borg.getId()).first();
        assertEquals(borg.getName(), hotelLoaded.getName());
        assertEquals(borg.getAddress().getPostCode(), hotelLoaded.getAddress().getPostCode());
    }

    @Test
    public void testGet() {
        getMapper().map(FacebookUser.class);
        List<FacebookUser> fbUsers = new ArrayList<>();
        fbUsers.add(new FacebookUser(1, "user 1"));
        fbUsers.add(new FacebookUser(2, "user 2"));
        fbUsers.add(new FacebookUser(3, "user 3"));
        fbUsers.add(new FacebookUser(4, "user 4"));

        getDatastore().saveMany(fbUsers);
        assertEquals(4, getDatastore().find(FacebookUser.class).count());
        assertNotNull(getDatastore().find(FacebookUser.class).filter("_id", 1).first());
        List<FacebookUser> res = getDatastore().find(FacebookUser.class).filter(Mapper.ID_KEY + " in", asList(1L, 2L)).asList();
        assertEquals(2, res.size());
        assertNotNull(res.get(0));
        assertNotEquals(0, res.get(0).id);
        assertNotNull(res.get(1));
        assertNotNull(res.get(1).username);

        getDatastore().getCollection(FacebookUser.class).deleteMany(new Document());
        getAds().insertMany(fbUsers);
        assertEquals(4, getDatastore().find(FacebookUser.class).count());
        assertNotNull(getDatastore().find(FacebookUser.class).filter("_id", 1).first());
        res = getDatastore().find(FacebookUser.class).filter(Mapper.ID_KEY + " in", asList(1L, 2L)).asList();
        assertEquals(2, res.size());
        assertNotNull(res.get(0));
        assertNotEquals(0, res.get(0).id);
        assertNotNull(res.get(1));
        assertNotNull(res.get(1).username);
    }

    @Test
    public void testIdUpdatedOnSave() {
        final Rectangle rect = new Rectangle(10, 10);
        getDatastore().save(rect);
        assertNotNull(rect.getId());
    }

    @Test
    public void testLifecycle() {
        final LifecycleTestObj life1 = new LifecycleTestObj();
        getMapper().addMappedClass(LifecycleTestObj.class);
        getDatastore().save(life1);
        assertTrue(life1.prePersist);
        assertTrue(life1.prePersistWithParam);
        assertTrue(life1.postPersist);
        assertTrue(life1.postPersistWithParam);

        final Datastore datastore = getDatastore();
        final LifecycleTestObj loaded = datastore.find(life1.getClass())
                                                 .filter("_id", datastore.getMapper().getId(life1))
                                                 .first();
        assertTrue(loaded.preLoad);
        assertTrue(loaded.preLoadWithParam);
        assertTrue(loaded.postLoad);
        assertTrue(loaded.postLoadWithParam);
    }

    @Test
    public void testLifecycleListeners() {
        final LifecycleTestObj life1 = new LifecycleTestObj();
        getMapper().addMappedClass(LifecycleTestObj.class);
        getDatastore().save(life1);
        assertTrue(LifecycleListener.prePersist);
        assertTrue(LifecycleListener.prePersistWithEntity);
    }

    @Test
    public void testMorphiaDS() {
        Morphia.createDatastore(getMongoClient(), "test");
    }

    @Test
    public void testMultipleDatabasesSingleThreaded() {
        getMapper().map(FacebookUser.class);
        getMongoClient().dropDatabase("db1");
        getMongoClient().dropDatabase("db2");

        final Datastore ds1 = Morphia.createDatastore("db1");
        final Datastore ds2 = Morphia.createDatastore("db2");

        final FacebookUser db1Friend = new FacebookUser(3, "DB1 FaceBook Friend");
        ds1.save(db1Friend);
        final FacebookUser db1User = new FacebookUser(1, "DB1 FaceBook User");
        db1User.friends.add(db1Friend);
        ds1.save(db1User);

        final FacebookUser db2Friend = new FacebookUser(4, "DB2 FaceBook Friend");
        ds2.save(db2Friend);
        final FacebookUser db2User = new FacebookUser(2, "DB2 FaceBook User");
        db2User.friends.add(db2Friend);
        ds2.save(db2User);

        testFirstDatastore(ds1);
        testSecondDatastore(ds2);

        testFirstDatastore(ds1);
        testSecondDatastore(ds2);

        testFirstDatastore(ds1);
        testSecondDatastore(ds2);

        testFirstDatastore(ds1);
        testSecondDatastore(ds2);

        testStandardDatastore();
    }

    @Test
    public void testSaveAndDelete() {
        getDatastore().getCollection(Rectangle.class).drop();

        final Rectangle rect = new Rectangle(10, 10);
        ObjectId id = new ObjectId();
        rect.setId(id);

        //test delete(entity)
        getDatastore().save(rect);
        assertEquals(1, getDatastore().find(rect.getClass()).count());
        getDatastore().deleteOne(rect);
        assertEquals(0, getDatastore().find(rect.getClass()).count());

        //test delete(entity, id)
        getDatastore().save(rect);
        assertEquals(1, getDatastore().find(rect.getClass()).count());
        getDatastore().deleteOne(rect.getClass(), 1);
        assertEquals(1, getDatastore().find(rect.getClass()).count());
        getDatastore().deleteOne(rect.getClass(), id);
        assertEquals(0, getDatastore().find(rect.getClass()).count());

        //test delete(entity, {id})
        getDatastore().save(rect);
        assertEquals(1, getDatastore().find(rect.getClass()).count());
        getDatastore().deleteMany(rect.getClass(), singletonList(rect.getId()));
        assertEquals(0, getDatastore().find(rect.getClass()).count());

        //test delete(entity, {id,id})
        ObjectId id1 = (ObjectId) getDatastore().save(new Rectangle(10, 10)).getId();
        ObjectId id2 = (ObjectId) getDatastore().save(new Rectangle(10, 10)).getId();
        assertEquals(2, getDatastore().find(rect.getClass()).count());
        getDatastore().deleteMany(rect.getClass(), asList(id1, id2));
        assertEquals(0, getDatastore().find(rect.getClass()).count());

        //test delete(Class, {id,id})
        id1 = (ObjectId) getDatastore().save(new Rectangle(20, 20)).getId();
        id2 = (ObjectId) getDatastore().save(new Rectangle(20, 20)).getId();
        assertEquals("datastore should have saved two entities with autogenerated ids", 2, getDatastore().find(rect.getClass()).count());
        getDatastore().deleteMany(rect.getClass(), asList(id1, id2));
        assertEquals("datastore should have deleted two entities with autogenerated ids", 0, getDatastore().find(rect.getClass()).count());

        //test delete(entity, {id}) with one left
        id1 = (ObjectId) getDatastore().save(new Rectangle(20, 20)).getId();
        getDatastore().save(new Rectangle(20, 20));
        assertEquals(2, getDatastore().find(rect.getClass()).count());
        getDatastore().deleteMany(rect.getClass(), singletonList(id1));
        assertEquals(1, getDatastore().find(rect.getClass()).count());
        getDatastore().getCollection(Rectangle.class).drop();

        //test delete(Class, {id}) with one left
        id1 = (ObjectId) getDatastore().save(new Rectangle(20, 20)).getId();
        getDatastore().save(new Rectangle(20, 20));
        assertEquals(2, getDatastore().find(rect.getClass()).count());
        getDatastore().deleteMany(Rectangle.class, singletonList(id1));
        assertEquals(1, getDatastore().find(rect.getClass()).count());
    }

    @Test
    public void testUpdateWithCollation() {
        checkMinServerVersion(3.4);
        getDatastore().getCollection(FacebookUser.class).drop();
        getDatastore().saveMany(asList(new FacebookUser(1, "John Doe"),
                            new FacebookUser(2, "john doe")));

        Query<FacebookUser> query = getDatastore().find(FacebookUser.class)
                                                  .field("username").equal("john doe");
        UpdateOperations<FacebookUser> updateOperations = getDatastore().createUpdateOperations(FacebookUser.class)
                                                                        .inc("loginCount");
        UpdateResult results = getDatastore().updateMany(query, updateOperations);
        assertEquals(1, results.getModifiedCount());
        assertEquals(0, getDatastore().find(FacebookUser.class).filter("id", 1).first().loginCount);
        assertEquals(1, getDatastore().find(FacebookUser.class).filter("id", 2).first().loginCount);

        results = getDatastore().updateMany(query, updateOperations, new UpdateOptions()
            .collation(Collation.builder()
                                .locale("en")
                                .collationStrength(SECONDARY)
                                .build()), getDatastore().getDefaultWriteConcern());
        assertEquals(2, results.getModifiedCount());
        assertEquals(1, getDatastore().find(FacebookUser.class).filter("id", 1).first().loginCount);
        assertEquals(2, getDatastore().find(FacebookUser.class).filter("id", 2).first().loginCount);
    }

    @Test
    public void testFindAndModify() {
        getDatastore().getCollection(FacebookUser.class).drop();
        getDatastore().saveMany(asList(new FacebookUser(1, "John Doe"),
                            new FacebookUser(2, "Ron Swanson")));

        Query<FacebookUser> query = getDatastore().find(FacebookUser.class)
                                                  .field("username").equal("Ron Swanson");
        UpdateOperations<FacebookUser> updateOperations = getDatastore().createUpdateOperations(FacebookUser.class)
                                                                        .inc("loginCount");
        FacebookUser results = getDatastore().findAndModify(query, updateOperations);
        assertEquals(0, getDatastore().find(FacebookUser.class).filter("id", 1).first().loginCount);
        assertEquals(1, getDatastore().find(FacebookUser.class).filter("id", 2).first().loginCount);
        assertEquals(1, results.loginCount);

        results = getDatastore().findAndModify(query, updateOperations, new FindOneAndUpdateOptions(),
            getDatastore().getDefaultWriteConcern());
        assertEquals(0, getDatastore().find(FacebookUser.class).filter("id", 1).first().loginCount);
        assertEquals(2, getDatastore().find(FacebookUser.class).filter("id", 2).first().loginCount);
        assertEquals(1, results.loginCount);

        results = getDatastore()
                      .findAndModify(getDatastore()
                                         .find(FacebookUser.class)
                                         .field("id").equal(3L)
                                         .field("username").equal("Jon Snow"), updateOperations,
                          new FindOneAndUpdateOptions()
                              .returnDocument(BEFORE)
                              .upsert(true), getDatastore().getDefaultWriteConcern());
        assertNull(results);
        FacebookUser user = getDatastore().find(FacebookUser.class).filter("id", 3).first();
        assertEquals(1, user.loginCount);
        assertEquals("Jon Snow", user.username);


        results = getDatastore().findAndModify(getDatastore().find(FacebookUser.class)
                                                             .field("id").equal(4L)
                                                             .field("username").equal("Ron Swanson"), updateOperations,
            new FindOneAndUpdateOptions()
                .returnDocument(AFTER)
                .upsert(true), getDatastore().getDefaultWriteConcern());
        assertNotNull(results);
        user = getDatastore().find(FacebookUser.class).filter("id", 4).first();
        assertEquals(1, results.loginCount);
        assertEquals("Ron Swanson", results.username);
        assertEquals(1, user.loginCount);
        assertEquals("Ron Swanson", user.username);
    }

    @Test
    public void testFindAndModifyWithOptions() {
        checkMinServerVersion(3.4);
        getDatastore().getCollection(FacebookUser.class).drop();
        getDatastore().saveMany(asList(
            new FacebookUser(1, "John Doe"),
            new FacebookUser(2, "Ron Swanson")));

        Query<FacebookUser> query = getDatastore().find(FacebookUser.class)
                                                  .field("username").equal("Ron Swanson");
        UpdateOperations<FacebookUser> updateOperations = getDatastore().createUpdateOperations(FacebookUser.class)
                                                                        .inc("loginCount");
        FacebookUser results = getDatastore().findAndModify(query, updateOperations,
            new FindOneAndUpdateOptions()
            .returnDocument(AFTER),
            getDatastore().getDefaultWriteConcern());
        assertEquals(0, getDatastore().find(FacebookUser.class).filter("id", 1).first().loginCount);
        assertEquals(1, getDatastore().find(FacebookUser.class).filter("id", 2).first().loginCount);
        assertEquals(1, results.loginCount);

        results = getDatastore().findAndModify(query, updateOperations,
            new FindOneAndUpdateOptions()
                .returnDocument(BEFORE)
                .collation(Collation.builder()
                                    .locale("en")
                                    .collationStrength(SECONDARY)
                                    .build()),
            getDatastore().getDefaultWriteConcern());
        assertEquals(0, getDatastore().find(FacebookUser.class).filter("id", 1).first().loginCount);
        assertEquals(1, results.loginCount);
        assertEquals(2, getDatastore().find(FacebookUser.class).filter("id", 2).first().loginCount);

        results = getDatastore().findAndModify(getDatastore().find(FacebookUser.class)
                                                             .field("id").equal(3L)
                                                             .field("username").equal("Jon Snow"),
            updateOperations, new FindOneAndUpdateOptions()
                                  .returnDocument(BEFORE)
                                  .upsert(true), getDatastore().getDefaultWriteConcern());
        assertNull(results);
        FacebookUser user = getDatastore().find(FacebookUser.class).filter("id", 3).first();
        assertEquals(1, user.loginCount);
        assertEquals("Jon Snow", user.username);


        results = getDatastore().findAndModify(getDatastore().find(FacebookUser.class)
                                                             .field("id").equal(4L)
                                                             .field("username").equal("Ron Swanson"),
            updateOperations, new FindOneAndUpdateOptions()
                                  .returnDocument(AFTER)
                                  .upsert(true), getDatastore().getDefaultWriteConcern());
        assertNotNull(results);
        user = getDatastore().find(FacebookUser.class).filter("id", 4).first();
        assertEquals(1, results.loginCount);
        assertEquals("Ron Swanson", results.username);
        assertEquals(1, user.loginCount);
        assertEquals("Ron Swanson", user.username);
    }

    @Test
    public void testDeleteWithCollation() {
        checkMinServerVersion(3.4);
        getDatastore().getCollection(FacebookUser.class).drop();
        getDatastore().saveMany(asList(new FacebookUser(1, "John Doe"),
            new FacebookUser(2, "john doe")));

        Query<FacebookUser> query = getDatastore().find(FacebookUser.class)
                                                  .field("username").equal("john doe");
        assertEquals(1, getDatastore().deleteMany(query).getDeletedCount());

        assertEquals(1, getDatastore().deleteMany(query,
            new DeleteOptions()
                .collation(Collation.builder()
                                    .locale("en")
                                    .collationStrength(SECONDARY)
                                    .build()), getDatastore().getDefaultWriteConcern())
                                      .getDeletedCount());
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testEnforceWriteConcern() {
        DatastoreImpl ds = (DatastoreImpl) getDatastore();

        assertEquals(ACKNOWLEDGED, ds.enforceWriteConcern(FacebookUser.class));
        assertEquals(UNACKNOWLEDGED, ds.enforceWriteConcern(Simple.class));
    }

    @Test
    public void testFindAndDeleteWithCollation() {
        checkMinServerVersion(3.4);
        getDatastore().getCollection(FacebookUser.class).drop();
        getDatastore().saveMany(asList(new FacebookUser(1, "John Doe"),
                            new FacebookUser(2, "john doe")));

        Query<FacebookUser> query = getDatastore().find(FacebookUser.class)
                                           .field("username").equal("john doe");
        assertNotNull(getDatastore().findAndDelete(query));
        assertNull(getDatastore().findAndDelete(query));

        FindOneAndDeleteOptions options = new FindOneAndDeleteOptions()
            .collation(Collation.builder()
                                .locale("en")
                                .collationStrength(SECONDARY)
                                .build());
        assertNotNull(getDatastore().findAndDelete(query, options, getDatastore().getDefaultWriteConcern()));
    }

    @Test
    public void testFindAndDeleteWithNoQueryMatch() {
        assertNull(getDatastore().findAndDelete(getDatastore()
                                             .find(FacebookUser.class)
                                             .field("username").equal("David S. Pumpkins")));
    }

    private void testFirstDatastore(final Datastore ds1) {
        final FacebookUser user = ds1.find(FacebookUser.class).filter("id", 1).first();
        Assert.assertNotNull(user);
        Assert.assertNotNull(ds1.find(FacebookUser.class).filter("id", 3).first());

        Assert.assertEquals("Should find 1 friend", 1, user.friends.size());
        Assert.assertEquals("Should find the right friend", 3, user.friends.get(0).id);

        Assert.assertNull(ds1.find(FacebookUser.class).filter("id", 2).first());
        Assert.assertNull(ds1.find(FacebookUser.class).filter("id", 4).first());
    }

    private void testSecondDatastore(final Datastore ds2) {
        Assert.assertNull(ds2.find(FacebookUser.class).filter("id", 1).first());
        Assert.assertNull(ds2.find(FacebookUser.class).filter("id", 3).first());

        final FacebookUser db2FoundUser = ds2.find(FacebookUser.class).filter("id", 2).first();
        Assert.assertNotNull(db2FoundUser);
        Assert.assertNotNull(ds2.find(FacebookUser.class).filter("id", 4).first());
        Assert.assertEquals("Should find 1 friend", 1, db2FoundUser.friends.size());
        Assert.assertEquals("Should find the right friend", 4, db2FoundUser.friends.get(0).id);
    }

    private void testStandardDatastore() {
        Assert.assertNull(getDatastore().find(FacebookUser.class).filter("id", 1).first());
        Assert.assertNull(getDatastore().find(FacebookUser.class).filter("id", 2).first());
        Assert.assertNull(getDatastore().find(FacebookUser.class).filter("id", 3).first());
        Assert.assertNull(getDatastore().find(FacebookUser.class).filter("id", 4).first());
    }

    @Entity("facebook_users")
    public static class FacebookUser {
        @Id
        private long id;
        private String username;
        private int loginCount;
        @Reference
        private List<FacebookUser> friends = new ArrayList<>();

        public FacebookUser(final long id, final String name) {
            this();
            this.id = id;
            username = name;
        }

        FacebookUser() {
        }

        public long getId() {
            return id;
        }

        public String getUsername() {
            return username;
        }

        public int getLoginCount() {
            return loginCount;
        }

        public List<FacebookUser> getFriends() {
            return friends;
        }
    }

    @SuppressWarnings("UnusedDeclaration")
    static class LifecycleListener {
        private static boolean prePersist;
        private static boolean prePersistWithEntity;

        @PrePersist
        void prePersist() {
            prePersist = true;
        }

        @PrePersist
        void prePersist(final LifecycleTestObj obj) {
            if (obj == null) {
                throw new RuntimeException();
            }
            prePersistWithEntity = true;

        }
    }

    @SuppressWarnings("UnusedDeclaration")
    @EntityListeners(LifecycleListener.class)
    public static class LifecycleTestObj {
        @Id
        private ObjectId id;
        @Transient
        private boolean prePersist;
        @Transient
        private boolean postPersist;
        @Transient
        private boolean preLoad;
        @Transient
        private boolean postLoad;
        @Transient
        private boolean postLoadWithParam;
        private boolean prePersistWithParam;
        private boolean postPersistWithParam;
        private boolean preLoadWithParam;

        @PrePersist
        protected void prePersistWithParam(final Document document) {
            if (prePersistWithParam) {
                throw new RuntimeException("already called");
            }
            prePersistWithParam = true;
        }

        @PostPersist
        private void postPersistPersist() {
            if (postPersist) {
                throw new RuntimeException("already called");
            }
            postPersist = true;

        }

        @PrePersist
        void prePersist() {
            if (prePersist) {
                throw new RuntimeException("already called");
            }

            prePersist = true;
        }

        @PostPersist
        void postPersistWithParam(final Document document) {
            postPersistWithParam = true;
            if (!document.containsKey(Mapper.ID_KEY)) {
                throw new RuntimeException("missing " + Mapper.ID_KEY);
            }
        }

        @PreLoad
        void preLoad() {
            if (preLoad) {
                throw new RuntimeException("already called");
            }

            preLoad = true;
        }

        @PreLoad
        void preLoadWithParam(final Document document) {
            document.put("preLoadWithParam", true);
        }

        @PostLoad
        void postLoad() {
            if (postLoad) {
                throw new RuntimeException("already called");
            }

            postLoad = true;
        }

        @PostLoad
        void postLoadWithParam(final Document document) {
            if (postLoadWithParam) {
                throw new RuntimeException("already called");
            }
            postLoadWithParam = true;
        }
    }

    @SuppressWarnings("UnusedDeclaration")
    public static class KeysKeysKeys {
        @Id
        private ObjectId id;
        private List<Key<FacebookUser>> users;
        private Key<Rectangle> rect;

        private KeysKeysKeys() {
        }

        public KeysKeysKeys(final Key<Rectangle> rectKey, final List<Key<FacebookUser>> users) {
            rect = rectKey;
            this.users = users;
        }

        public ObjectId getId() {
            return id;
        }

        public Key<Rectangle> getRect() {
            return rect;
        }

        public List<Key<FacebookUser>> getUsers() {
            return users;
        }
    }

    @Entity(concern = "UNACKNOWLEDGED")
    static class Simple {
        @Id
        private String id;

        Simple(final String id) {
            this();
            this.id = id;
        }

        private Simple() {
        }
    }

}
