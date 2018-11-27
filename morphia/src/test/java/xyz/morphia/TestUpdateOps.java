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

package xyz.morphia;

import com.mongodb.WriteConcern;
import com.mongodb.client.model.PushOptions;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.Assert;
import org.junit.Test;
import xyz.morphia.annotations.Embedded;
import xyz.morphia.annotations.Entity;
import xyz.morphia.annotations.Id;
import xyz.morphia.annotations.Indexed;
import xyz.morphia.annotations.PreLoad;
import xyz.morphia.logging.Logger;
import xyz.morphia.query.Query;
import xyz.morphia.query.TestQuery.ContainsPic;
import xyz.morphia.query.TestQuery.Pic;
import xyz.morphia.query.UpdateOperations;
import xyz.morphia.query.ValidationException;
import xyz.morphia.testmodel.Article;
import xyz.morphia.testmodel.Circle;
import xyz.morphia.testmodel.Rectangle;
import xyz.morphia.testmodel.Translation;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.Matchers.arrayContaining;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static xyz.morphia.logging.MorphiaLoggerFactory.get;

@SuppressWarnings("UnusedDeclaration")
public class TestUpdateOps extends TestBase {
    private static final Logger LOG = get(TestUpdateOps.class);

    @Test
    public void shouldUpdateAnArrayElement() {
        // given
        ObjectId parentId = new ObjectId();
        String childName = "Bob";
        String updatedLastName = "updatedLastName";

        Parent parent = new Parent();
        parent.id = parentId;
        parent.children.add(new Child("Anthony", "Child"));
        parent.children.add(new Child(childName, "originalLastName"));
        getDatastore().save(parent);

        // when
        Query<Parent> query = getDatastore().find(Parent.class)
                                            .field("_id").equal(parentId)
                                            .field("children.first")
                                            .equal(childName);
        UpdateOperations<Parent> updateOps = getDatastore().createUpdateOperations(Parent.class)
                                                           .set("children.$.last", updatedLastName);
        UpdateResult updateResult = getDatastore().updateMany(query, updateOps);

        // then
        assertThat(updateResult.getModifiedCount(), is(1L));
        assertThat(getDatastore().find(Parent.class).filter("id", parentId).get().children, hasItem(new Child(childName, updatedLastName)));
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testAdd() {
        ContainsIntArray cIntArray = new ContainsIntArray();
        Datastore ds = getDatastore();
        ds.save(cIntArray);

        assertThat(ds.get(cIntArray).values, is((new ContainsIntArray()).values));

        //add 4 to array
        assertUpdated(ds.updateOne(ds.createQuery(ContainsIntArray.class),
            ds.createUpdateOperations(ContainsIntArray.class)
              .addToSet("values", 4)), 1);

        assertThat(ds.get(cIntArray).values, is(new Integer[]{1, 2, 3, 4}));

        //add unique (4) -- noop
        assertUpdated(ds.updateOne(ds.createQuery(ContainsIntArray.class),
            ds.createUpdateOperations(ContainsIntArray.class)
              .addToSet("values", 4)),
            0);
        assertThat(ds.get(cIntArray).values, is(new Integer[]{1, 2, 3, 4}));

        //add dup 4
        assertUpdated(ds.updateOne(ds.createQuery(ContainsIntArray.class),
            ds.createUpdateOperations(ContainsIntArray.class)
              .push("values", 4)),
            1);
        assertThat(ds.get(cIntArray).values, is(new Integer[]{1, 2, 3, 4, 4}));

        //cleanup for next tests
        ds.deleteMany(ds.find(ContainsIntArray.class));
        cIntArray = ds.getByKey(ContainsIntArray.class, ds.save(new ContainsIntArray()));

        //add [4,5]
        final List<Integer> newValues = new ArrayList<>();
        newValues.add(4);
        newValues.add(5);
        assertUpdated(ds.updateOne(ds.createQuery(ContainsIntArray.class),
            ds.createUpdateOperations(ContainsIntArray.class)
              .addToSet("values", newValues)),
            1);
        assertThat(ds.get(cIntArray).values, is(new Integer[]{1, 2, 3, 4, 5}));

        //add them again... noop
        assertUpdated(ds.updateOne(ds.createQuery(ContainsIntArray.class),
            ds.createUpdateOperations(ContainsIntArray.class)
              .addToSet("values", newValues)),
            0);
        assertThat(ds.get(cIntArray).values, is(new Integer[]{1, 2, 3, 4, 5}));

        //add dups [4,5]
        assertUpdated(ds.updateOne(ds.createQuery(ContainsIntArray.class),
            ds.createUpdateOperations(ContainsIntArray.class)
              .push("values", newValues)),
            1);
        assertThat(ds.get(cIntArray).values, is(new Integer[]{1, 2, 3, 4, 5, 4, 5}));
    }

    private void assertUpdated(final UpdateResult res, final long count) {
        assertNull(res.getUpsertedId());
        assertEquals(count, res.getModifiedCount());
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testAddAll() {
        getMapper().map(EntityLogs.class, EntityLog.class);
        String uuid = "4ec6ada9-081a-424f-bee0-934c0bc4fab7";

        EntityLogs logs = new EntityLogs();
        logs.uuid = uuid;
        getDatastore().save(logs);

        Query<EntityLogs> finder = getDatastore().find(EntityLogs.class).field("uuid").equal(uuid);

        // both of these entries will have a className attribute
        List<EntityLog> latestLogs = asList(new EntityLog("whatever1", new Date()), new EntityLog("whatever2", new Date()));
        getDatastore().updateOne(finder,
            getDatastore().createUpdateOperations(EntityLogs.class)
                          .addToSet("logs", latestLogs),
            new UpdateOptions().upsert(true),
            getDatastore().getDefaultWriteConcern());
        validateNoClassName(finder.get());

        // this entry will NOT have a className attribute
        getDatastore().updateOne(finder,
            getDatastore().createUpdateOperations(EntityLogs.class)
                          .addToSet("logs", new EntityLog("whatever3", new Date())),
            new UpdateOptions().upsert(true),
            getDatastore().getDefaultWriteConcern());
        validateNoClassName(finder.get());

        // this entry will NOT have a className attribute
        getDatastore().updateOne(finder,
            getDatastore().createUpdateOperations(EntityLogs.class)
                          .addToSet("logs", new EntityLog("whatever4", new Date())), new UpdateOptions().upsert(true),
            getDatastore().getDefaultWriteConcern());
        validateNoClassName(finder.get());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void validateNoClassName(final EntityLogs loaded) {
        List<Document> logs = (List<Document>) loaded.raw.get("logs");
        for (Document o : logs) {
            Assert.assertNull(o.get("className"));
        }
    }

    @Test
    public void testAddToSet() {
        ContainsIntArray cIntArray = new ContainsIntArray();
        getDatastore().save(cIntArray);

        assertThat(getDatastore().get(cIntArray).values, is((new ContainsIntArray()).values));

        assertUpdated(getDatastore().updateOne(getDatastore().find(ContainsIntArray.class),
            getDatastore().createUpdateOperations(ContainsIntArray.class)
                          .addToSet("values", 5)),
            1);
        assertThat(getDatastore().get(cIntArray).values, is(new Integer[]{1, 2, 3, 5}));

        assertUpdated(getDatastore().updateOne(getDatastore().find(ContainsIntArray.class),
            getDatastore().createUpdateOperations(ContainsIntArray.class)
                          .addToSet("values", 4)),
            1);
        assertThat(getDatastore().get(cIntArray).values, is(new Integer[]{1, 2, 3, 5, 4}));

        assertUpdated(getDatastore().updateOne(getDatastore().find(ContainsIntArray.class),
            getDatastore().createUpdateOperations(ContainsIntArray.class)
                          .addToSet("values", asList(8, 9))),
            1);
        assertThat(getDatastore().get(cIntArray).values, is(new Integer[]{1, 2, 3, 5, 4, 8, 9}));

        assertUpdated(getDatastore().updateOne(getDatastore().find(ContainsIntArray.class),
            getDatastore().createUpdateOperations(ContainsIntArray.class)
                          .addToSet("values", asList(4, 5))),
            0);
        assertThat(getDatastore().get(cIntArray).values, is(new Integer[]{1, 2, 3, 5, 4, 8, 9}));
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testUpdateFirst() {
        ContainsIntArray cIntArray = new ContainsIntArray();
        ContainsIntArray control = new ContainsIntArray();
        Datastore ds = getDatastore();
        getMongoClient().dropDatabase("ContainsIntArray");
        ds.saveMany(asList(cIntArray, control));

        assertThat(ds.get(cIntArray).values, is((new ContainsIntArray()).values));
        Query<ContainsIntArray> query = ds.find(ContainsIntArray.class)
                                          .filter("_id = ", cIntArray.id);

        doUpdates(cIntArray.id, control.id, query, ds.createUpdateOperations(ContainsIntArray.class)
                                                     .addToSet("values", 4),
            new Integer[]{1, 2, 3, 4});


        doUpdates(cIntArray.id, control.id, query, ds.createUpdateOperations(ContainsIntArray.class)
                                                     .addToSet("values", asList(4, 5)),
            new Integer[]{1, 2, 3, 4, 5});


        assertInserted(ds.updateOne(ds.find(ContainsIntArray.class)
                                      .filter("values", new Integer[]{4, 5, 7}),
            ds.createUpdateOperations(ContainsIntArray.class)
              .addToSet("values", 6), new UpdateOptions().upsert(true),
            getDatastore().getDefaultWriteConcern()));
        assertNotNull(ds.find(ContainsIntArray.class)
                        .filter("values", new Integer[]{4, 5, 7, 6}));
    }

    @SuppressWarnings("deprecation")
    private void doUpdates(final ObjectId updated, final ObjectId control,
                           final Query<ContainsIntArray> query, final UpdateOperations<ContainsIntArray> operations,
                           final Integer[] target) {
        assertUpdated(getDatastore().updateOne(query, operations), 1);
        assertThat(getDatastore().get(ContainsIntArray.class, updated).values, is(target));
        assertThat(getDatastore().get(ContainsIntArray.class, control).values, is(new Integer[]{1, 2, 3}));

        assertUpdated(getDatastore().updateOne(query, operations, new UpdateOptions(), getDatastore().getDefaultWriteConcern()), 0);
        assertThat(getDatastore().get(ContainsIntArray.class, updated).values, is(target));
        assertThat(getDatastore().get(ContainsIntArray.class, control).values, is(new Integer[]{1, 2, 3}));
    }

    private void assertInserted(final UpdateResult res) {
        assertThat(res.getUpsertedId(), notNullValue());
        assertThat(res.getModifiedCount(), is(0L));
    }

    @Test
    public void testExistingUpdates() {
        Circle c = new Circle(100D);
        getDatastore().save(c);
        c = new Circle(12D);
        getDatastore().save(c);
        assertUpdated(getDatastore().updateOne(getDatastore().find(Circle.class),
            getDatastore().createUpdateOperations(Circle.class)
                          .inc("radius", 1D)),
            1);

        assertUpdated(getDatastore().updateMany(getDatastore().find(Circle.class),
            getDatastore().createUpdateOperations(Circle.class)
                          .inc("radius")),
            2);

        //test possible data type change.
        final Circle updatedCircle = getDatastore().find(Circle.class).filter("radius", 13).get();
        assertThat(updatedCircle, is(notNullValue()));
        assertThat(updatedCircle.getRadius(), is(13D));
    }

    @Test
    public void testIncDec() {

        final List<Rectangle> list = asList(
            new Rectangle(1, 10),
            new Rectangle(1, 10),
            new Rectangle(1, 10),
            new Rectangle(10, 10),
            new Rectangle(10, 10));
        getDatastore().saveMany(list);

        final Query<Rectangle> query1 = getDatastore().find(Rectangle.class).filter("height", 1D);
        final Query<Rectangle> query2 = getDatastore().find(Rectangle.class).filter("height", 2D);
        final Query<Rectangle> query35 = getDatastore().find(Rectangle.class).filter("height", 3.5D);

        assertThat(getDatastore().getCount(query1), is(3L));
        assertThat(getDatastore().getCount(query2), is(0L));

        final UpdateResult results = getDatastore().updateMany(query1, getDatastore().createUpdateOperations(Rectangle.class)
                                                                                     .inc("height"));
        assertUpdated(results, 3);

        assertThat(getDatastore().getCount(query1), is(0L));
        assertThat(getDatastore().getCount(query2), is(3L));

        getDatastore().updateMany(query2, getDatastore().createUpdateOperations(Rectangle.class).dec("height"));
        assertThat(getDatastore().getCount(query1), is(3L));
        assertThat(getDatastore().getCount(query2), is(0L));

        getDatastore().updateMany(query1, getDatastore().createUpdateOperations(Rectangle.class).inc("height", 2.5D));
        assertThat(getDatastore().getCount(query1), is(0L));
        assertThat(getDatastore().getCount(query35), is(3L));

        getDatastore().updateMany(query35, getDatastore().createUpdateOperations(Rectangle.class).dec("height", 2.5D));
        assertThat(getDatastore().getCount(query1), is(3L));
        assertThat(getDatastore().getCount(query35), is(0L));

        getDatastore().updateMany(getDatastore().find(Rectangle.class).filter("height", 1D),
            getDatastore().createUpdateOperations(Rectangle.class)
                          .set("height", 1D)
                          .inc("width", 20D));

        assertThat(getDatastore().getCount(Rectangle.class), is(5L));
        assertThat(getDatastore().find(Rectangle.class).filter("height", 1D).get(), is(notNullValue()));
        assertThat(getDatastore().find(Rectangle.class).filter("width", 30D).get(), is(notNullValue()));

        getDatastore().updateMany(getDatastore().find(Rectangle.class).filter("width", 30D),
            getDatastore().createUpdateOperations(Rectangle.class).set("height", 2D).set("width", 2D));
        assertThat(getDatastore().find(Rectangle.class).filter("width", 1D).get(), is(nullValue()));
        assertThat(getDatastore().find(Rectangle.class).filter("width", 2D).get(), is(notNullValue()));

        getDatastore().updateMany(query35, getDatastore().createUpdateOperations(Rectangle.class).dec("height", 1));
        getDatastore().updateMany(query35, getDatastore().createUpdateOperations(Rectangle.class).dec("height", Long.MAX_VALUE));
        getDatastore().updateMany(query35, getDatastore().createUpdateOperations(Rectangle.class).dec("height", 1.5f));
        getDatastore().updateMany(query35, getDatastore().createUpdateOperations(Rectangle.class).dec("height", Double.MAX_VALUE));
        try {
            getDatastore().updateMany(query35, getDatastore().createUpdateOperations(Rectangle.class)
                                                             .dec("height", new AtomicInteger(1)));
            fail("Wrong data type not recognized.");
        } catch (IllegalArgumentException ignore) {
        }
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testInsertUpdate() {
        assertInserted(getDatastore().updateOne(
            getDatastore().find(Circle.class).field("radius").equal(0),
            getDatastore().createUpdateOperations(Circle.class).inc("radius", 1D),
            new UpdateOptions().upsert(true),
            getDatastore().getDefaultWriteConcern()));

        assertInserted(getDatastore().updateMany(
            getDatastore().find(Circle.class).field("radius").equal(0),
            getDatastore().createUpdateOperations(Circle.class).inc("radius", 1D),
            new UpdateOptions().upsert(true),
            getDatastore().getDefaultWriteConcern()));
    }

    @Test
    public void testInsertWithRef() {
        final Pic pic = new Pic();
        pic.setName("fist");
        final Key<Pic> picKey = getDatastore().save(pic);

        assertInserted(getDatastore().updateOne(
            getDatastore().find(ContainsPic.class)
                          .filter("name", "first")
                          .filter("pic", picKey),
            getDatastore().createUpdateOperations(ContainsPic.class)
                          .set("name", "A"),
            new UpdateOptions().upsert(true), getDatastore().getDefaultWriteConcern()));

        assertThat(getDatastore().find(ContainsPic.class).count(), is(1L));
        getDatastore().deleteMany(getDatastore().find(ContainsPic.class));

        assertInserted(getDatastore().updateMany(getDatastore().find(ContainsPic.class)
                                                               .filter("name", "first")
                                                               .filter("pic", pic),
            getDatastore().createUpdateOperations(ContainsPic.class)
                          .set("name", "second"),
            new UpdateOptions()
                .upsert(true),
            getDatastore().getDefaultWriteConcern()));
        assertThat(getDatastore().find(ContainsPic.class).count(), is(1L));

        //test reading the object.
        final ContainsPic cp = getDatastore().find(ContainsPic.class).get();
        assertThat(cp, is(notNullValue()));
        assertThat(cp.getName(), is("second"));
        assertThat(cp.getPic(), is(notNullValue()));
        assertThat(cp.getPic().getName(), is(notNullValue()));
        assertThat(cp.getPic().getName(), is("fist"));
    }

    @Test
    public void testMaxKeepsCurrentDocumentValueWhenThisIsLargerThanSuppliedValue() {
        final ObjectId id = new ObjectId();
        final double originalValue = 2D;

        Datastore ds = getDatastore();
        assertInserted(ds.updateOne(ds.find(Circle.class)
                                      .field("id").equal(id),
            ds.createUpdateOperations(Circle.class)
              .setOnInsert("radius", originalValue),
            new UpdateOptions()
                .upsert(true),
            getDatastore().getDefaultWriteConcern()));

        assertUpdated(ds.updateOne(ds.find(Circle.class).field("id").equal(id),
            ds.createUpdateOperations(Circle.class)
              .max("radius", 1D),
            new UpdateOptions()
                .upsert(true),
            getDatastore().getDefaultWriteConcern()),
            0);


        assertThat(ds.get(Circle.class, id).getRadius(), is(originalValue));
    }

    @Test
    public void testMinKeepsCurrentDocumentValueWhenThisIsSmallerThanSuppliedValue() {
        final ObjectId id = new ObjectId();
        final double originalValue = 3D;

        assertInserted(getDatastore().updateOne(getDatastore().find(Circle.class).field("id").equal(id),
            getDatastore().createUpdateOperations(Circle.class).setOnInsert("radius", originalValue),
            new UpdateOptions().upsert(true),
            getDatastore().getDefaultWriteConcern()));

        assertUpdated(getDatastore().updateOne(getDatastore().find(Circle.class).field("id").equal(id),
            getDatastore().createUpdateOperations(Circle.class).min("radius", 5D)), 0);

        final Circle updatedCircle = getDatastore().get(Circle.class, id);
        assertThat(updatedCircle, is(notNullValue()));
        assertThat(updatedCircle.getRadius(), is(originalValue));
    }

    @Test
    public void testMinUsesSuppliedValueWhenThisIsSmallerThanCurrentDocumentValue() {
        final ObjectId id = new ObjectId();
        final double newLowerValue = 2D;

        final UpdateOptions options = new UpdateOptions()
                                          .upsert(true);
        final Query<Circle> query = getDatastore().find(Circle.class).field("id").equal(id);
        assertInserted(getDatastore().updateOne(
            query,
            getDatastore().createUpdateOperations(Circle.class).setOnInsert("radius", 3D),
            options,
            getDatastore().getDefaultWriteConcern()));


        assertUpdated(getDatastore().updateOne(query,
            getDatastore().createUpdateOperations(Circle.class).min("radius", newLowerValue),
            options,
            getDatastore().getDefaultWriteConcern()), 1);

        final Circle updatedCircle = getDatastore().get(Circle.class, id);
        assertThat(updatedCircle, is(notNullValue()));
        assertThat(updatedCircle.getRadius(), is(newLowerValue));
    }

    @Test
    public void testPush() {
        ContainsIntArray cIntArray = new ContainsIntArray();
        getDatastore().save(cIntArray);
        assertThat(getDatastore().get(cIntArray).values, is((new ContainsIntArray()).values));

        getDatastore().updateOne(getDatastore().find(ContainsIntArray.class),
            getDatastore().createUpdateOperations(ContainsIntArray.class)
                          .push("values", 4),
            new UpdateOptions(),
            getDatastore().getDefaultWriteConcern());

        assertThat(getDatastore().get(cIntArray).values, is(new Integer[]{1, 2, 3, 4}));

        getDatastore().updateOne(getDatastore().find(ContainsIntArray.class),
            getDatastore().createUpdateOperations(ContainsIntArray.class)
                          .push("values", 4),
            new UpdateOptions(),
            getDatastore().getDefaultWriteConcern());

        assertThat(getDatastore().get(cIntArray).values, is(new Integer[]{1, 2, 3, 4, 4}));

        getDatastore().updateOne(getDatastore().find(ContainsIntArray.class),
            getDatastore().createUpdateOperations(ContainsIntArray.class)
                          .push("values", asList(5, 6)),
            new UpdateOptions(),
            getDatastore().getDefaultWriteConcern());

        assertThat(getDatastore().get(cIntArray).values, is(new Integer[]{1, 2, 3, 4, 4, 5, 6}));

        getDatastore().updateOne(getDatastore().find(ContainsIntArray.class),
            getDatastore().createUpdateOperations(ContainsIntArray.class)
                          .push("values", 12, new PushOptions().position(2)),
            new UpdateOptions(),
            getDatastore().getDefaultWriteConcern());

        assertThat(getDatastore().get(cIntArray).values, is(new Integer[]{1, 2, 12, 3, 4, 4, 5, 6}));


        getDatastore().updateOne(getDatastore().find(ContainsIntArray.class),
            getDatastore().createUpdateOperations(ContainsIntArray.class)
                          .push("values", asList(99, 98, 97), new PushOptions().position(4)),
            new UpdateOptions(),
            getDatastore().getDefaultWriteConcern());

        assertThat(getDatastore().get(cIntArray).values, is(new Integer[]{1, 2, 12, 3, 99, 98, 97, 4, 4, 5, 6}));
    }

    @Test
    public void testRemoveAllSingleValue() {
        EntityLogs logs = new EntityLogs();
        Date date = new Date();
        logs.logs.addAll(asList(
            new EntityLog("log1", date),
            new EntityLog("log2", date),
            new EntityLog("log3", date),
            new EntityLog("log1", date),
            new EntityLog("log2", date),
            new EntityLog("log3", date)));

        Datastore ds = getDatastore();
        ds.save(logs);

        UpdateOperations<EntityLogs> operations =
            ds.createUpdateOperations(EntityLogs.class).removeAll("logs", new EntityLog("log3", date));

        UpdateResult results = ds.updateOne(ds.find(EntityLogs.class), operations);
        assertEquals(1, results.getModifiedCount());
        EntityLogs updated = ds.find(EntityLogs.class).get();
        assertEquals(4, updated.logs.size());
        for (int i = 0; i < 4; i++) {
            assertEquals(new EntityLog("log" + ((i % 2) + 1), date), updated.logs.get(i));
        }
    }

    @Test
    public void testRemoveAllList() {
        EntityLogs logs = new EntityLogs();
        Date date = new Date();
        logs.logs.addAll(asList(
            new EntityLog("log1", date),
            new EntityLog("log2", date),
            new EntityLog("log3", date),
            new EntityLog("log1", date),
            new EntityLog("log2", date),
            new EntityLog("log3", date)));

        Datastore ds = getDatastore();
        ds.save(logs);

        UpdateOperations<EntityLogs> operations =
            ds.createUpdateOperations(EntityLogs.class).removeAll("logs", singletonList(new EntityLog("log3", date)));

        UpdateResult results = ds.updateOne(ds.find(EntityLogs.class), operations);
        assertEquals(1, results.getModifiedCount());
        EntityLogs updated = ds.find(EntityLogs.class).get();
        assertEquals(4, updated.logs.size());
        for (int i = 0; i < 4; i++) {
            assertEquals(new EntityLog("log" + ((i % 2) + 1), date), updated.logs.get(i));
        }
    }

    @Test
    public void testRemoveWithNoData() {
        DumbColl dumbColl = new DumbColl("ID");
        dumbColl.fromArray = singletonList(new DumbArrayElement("something"));
        DumbColl dumbColl2 = new DumbColl("ID2");
        dumbColl2.fromArray = singletonList(new DumbArrayElement("something"));
        getDatastore().saveMany(asList(dumbColl, dumbColl2));

        UpdateResult deleteResults = getDatastore().updateOne(
            getDatastore().find(DumbColl.class).field("opaqueId").equalIgnoreCase("ID"),
            getAds().createUpdateOperations(DumbColl.class,
                new Document("$pull", new Document("fromArray", new Document("whereId", "not there")))));

        getDatastore().updateOne(
            getDatastore().find(DumbColl.class).field("opaqueId").equalIgnoreCase("ID"),
            getAds().createUpdateOperations(DumbColl.class)
                    .removeAll("fromArray", new DumbArrayElement("something")));
    }

    @Test
    public void testElemMatchUpdate() {
        // setUp
        Object id = getDatastore().save(new ContainsIntArray()).getId();
        assertThat(getDatastore().get(ContainsIntArray.class, id).values, arrayContaining(1, 2, 3));

        // do patch
        Query<ContainsIntArray> q = getDatastore().createQuery(ContainsIntArray.class)
                                                  .filter("id", id)
                                                  .filter("values", 2);

        UpdateOperations<ContainsIntArray> ops = getDatastore().createUpdateOperations(ContainsIntArray.class)
                                                               .set("values.$", 5);
        getDatastore().updateMany(q, ops);

        // expected
        assertThat(getDatastore().get(ContainsIntArray.class, id).values, arrayContaining(1, 5, 3));
    }

    @Test
    public void testRemoveFirst() {
        final ContainsIntArray cIntArray = new ContainsIntArray();
        getDatastore().save(cIntArray);
        ContainsIntArray cIALoaded = getDatastore().get(cIntArray);
        assertThat(cIALoaded.values.length, is(3));
        assertThat(cIALoaded.values, is((new ContainsIntArray()).values));

        assertUpdated(getDatastore().updateOne(getDatastore().find(ContainsIntArray.class),
            getDatastore().createUpdateOperations(ContainsIntArray.class)
                          .removeFirst("values"),
            new UpdateOptions(),
            getDatastore().getDefaultWriteConcern()),
            1);
        assertThat(getDatastore().get(cIntArray).values, is(new Integer[]{2, 3}));

        assertUpdated(getDatastore().updateOne(getDatastore().find(ContainsIntArray.class),
            getDatastore().createUpdateOperations(ContainsIntArray.class)
                          .removeLast("values"),
            new UpdateOptions(),
            getDatastore().getDefaultWriteConcern()),
            1);
        assertThat(getDatastore().get(cIntArray).values, is(new Integer[]{2}));
    }

    @Test
    public void testSetOnInsertWhenInserting() {
        ObjectId id = new ObjectId();

        assertInserted(getDatastore().updateOne(getDatastore().find(Circle.class).field("id").equal(id),
            getDatastore().createUpdateOperations(Circle.class).setOnInsert("radius", 2D),
            new UpdateOptions().upsert(true),
            getDatastore().getDefaultWriteConcern()));

        final Circle updatedCircle = getDatastore().get(Circle.class, id);

        assertThat(updatedCircle, is(notNullValue()));
        assertThat(updatedCircle.getRadius(), is(2D));
    }

    @Test
    public void testSetOnInsertWhenUpdating() {
        ObjectId id = new ObjectId();

        assertInserted(getDatastore().updateOne(getDatastore().find(Circle.class).field("id").equal(id),
            getDatastore().createUpdateOperations(Circle.class).setOnInsert("radius", 1D),
            new UpdateOptions()
                .upsert(true),
            getDatastore().getDefaultWriteConcern()));

        assertUpdated(getDatastore().updateOne(getDatastore().find(Circle.class).field("id").equal(id),
            getDatastore().createUpdateOperations(Circle.class)
                          .inc("radius", 30D),
            new UpdateOptions()
                .upsert(true),
            getDatastore().getDefaultWriteConcern()),
            1);

        final Circle updatedCircle = getDatastore().get(Circle.class, id);

        assertThat(updatedCircle, is(notNullValue()));
        assertThat(updatedCircle.getRadius(), is(31D));
    }

    @Test
    public void testSetUnset() {
        Datastore ds = getDatastore();
        final Key<Circle> key = ds.save(new Circle(1));

        assertUpdated(ds.updateOne(ds.find(Circle.class).filter("radius", 1D),
            ds.createUpdateOperations(Circle.class).set("radius", 2D),
            new UpdateOptions(),
            getDatastore().getDefaultWriteConcern()),
            1);

        assertThat(ds.getByKey(Circle.class, key).getRadius(), is(2D));


        assertUpdated(ds.updateOne(ds.find(Circle.class).filter("radius", 2D),
            ds.createUpdateOperations(Circle.class).unset("radius"),
            new UpdateOptions(),
            getDatastore().getDefaultWriteConcern()),
            1);

        assertThat(ds.getByKey(Circle.class, key).getRadius(), is(0D));

        Article article = new Article();

        ds.save(article);

        ds.updateOne(ds.find(Article.class),
            ds.createUpdateOperations(Article.class)
              .set("translations", new HashMap<String, Translation>()));

        ds.updateOne(ds.find(Article.class),
            ds.createUpdateOperations(Article.class)
              .unset("translations"));
    }

    @Test
    public void testUpdateFirstNoCreate() {
        getDatastore().deleteMany(getDatastore().find(EntityLogs.class));
        List<EntityLogs> logs = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            logs.add(createEntryLogs("logs" + i));
        }
        EntityLogs logs1 = logs.get(0);
        Query<EntityLogs> query = getDatastore().find(EntityLogs.class);
        UpdateOperations<EntityLogs> updateOperations = getDatastore().createUpdateOperations(EntityLogs.class);
        Document object = new Document("new", "value");
        updateOperations.set("raw", object);

        getDatastore().updateOne(query, updateOperations);

        List<EntityLogs> list = getDatastore().find(EntityLogs.class).asList();
        for (int i = 0; i < list.size(); i++) {
            final EntityLogs entityLogs = list.get(i);
            assertEquals(entityLogs.id.equals(logs1.id) ? object : logs.get(i).raw, entityLogs.raw);
        }
    }

    private EntityLogs createEntryLogs(final String value) {
        EntityLogs logs = new EntityLogs();
        logs.raw = new Document("name", value);
        getDatastore().save(logs);

        return logs;
    }

    @Test
    public void testUpdateFirstNoCreateWithWriteConcern() {
        List<EntityLogs> logs = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            logs.add(createEntryLogs("logs" + i));
        }
        EntityLogs logs1 = logs.get(0);

        getDatastore().updateOne(getDatastore().find(EntityLogs.class),
            getDatastore().createUpdateOperations(EntityLogs.class)
                          .set("raw", new Document("new", "value")));

        List<EntityLogs> list = getDatastore().find(EntityLogs.class).asList();
        for (int i = 0; i < list.size(); i++) {
            final EntityLogs entityLogs = list.get(i);
            assertEquals(entityLogs.id.equals(logs1.id) ? new Document("new", "value") : logs.get(i).raw, entityLogs.raw);
        }
    }

    @Test
    public void testUpdateKeyRef() {
        final ContainsPicKey cpk = new ContainsPicKey();
        cpk.name = "cpk one";

        Datastore ds = getDatastore();
        ds.save(cpk);

        final Pic pic = new Pic();
        pic.setName("fist again");
        final Key<Pic> picKey = ds.save(pic);
        // picKey = getDs().getKey(pic);


        //test with Key<Pic>

        assertThat(ds.updateMany(ds.find(ContainsPicKey.class).filter("name", cpk.name),
            ds.createUpdateOperations(ContainsPicKey.class).set("pic", getMapper().getKey(pic))).getModifiedCount(), is(1L));

        //test reading the object.
        final ContainsPicKey cpk2 = ds.find(ContainsPicKey.class).get();
        assertThat(cpk2, is(notNullValue()));
        assertThat(cpk.name, is(cpk2.name));
        assertThat(cpk2.pic, is(notNullValue()));
        assertThat(picKey, is(cpk2.pic));

        ds.updateMany(ds.find(ContainsPicKey.class).filter("name", cpk.name),
            ds.createUpdateOperations(ContainsPicKey.class).set("pic", picKey));

        //test reading the object.
        final ContainsPicKey cpk3 = ds.find(ContainsPicKey.class).get();
        assertThat(cpk3, is(notNullValue()));
        assertThat(cpk.name, is(cpk3.name));
        assertThat(cpk3.pic, is(notNullValue()));
        assertThat(picKey, is(cpk3.pic));
    }

    @Test
    public void testUpdateKeyList() {
        final ContainsPicKey cpk = new ContainsPicKey();
        cpk.name = "cpk one";

        Datastore ds = getDatastore();
        ds.save(cpk);

        final Pic pic = new Pic();
        pic.setName("fist again");
        final Key<Pic> picKey = ds.save(pic);

        cpk.keys = singletonList(picKey);

        //test with Key<Pic>
        final UpdateResult res = ds.updateMany(ds.find(ContainsPicKey.class).filter("name", cpk.name),
            ds.createUpdateOperations(ContainsPicKey.class).set("keys", cpk.keys));

        assertEquals(res.getModifiedCount(), 1);

        //test reading the object.
        final ContainsPicKey cpk2 = ds.find(ContainsPicKey.class).get();
        assertThat(cpk2, is(notNullValue()));
        assertThat(cpk.name, is(cpk2.name));
        assertTrue(format("Should find %s in %s", picKey, cpk2.keys), cpk2.keys.contains(picKey));
    }

    @Test
    public void testUpdateRef() {
        final ContainsPic cp = new ContainsPic();
        cp.setName("cp one");

        getDatastore().save(cp);

        final Pic pic = new Pic();
        pic.setName("fist");
        final Key<Pic> picKey = getDatastore().save(pic);

        final Query<ContainsPic> query = getDatastore().find(ContainsPic.class).filter("name", cp.getName());

        final long count = getDatastore().updateMany(query,
            getDatastore().createUpdateOperations(ContainsPic.class)
                          .set("pic", pic))
                                         .getModifiedCount();
        assertEquals(1, count);

        //test reading the object.
        final ContainsPic cp2 = getDatastore().find(ContainsPic.class).get();
        assertThat(cp2, is(notNullValue()));
        assertThat(cp.getName(), is(cp2.getName()));
        assertThat(cp2.getPic(), is(notNullValue()));
        assertThat(cp2.getPic().getName(), is(notNullValue()));
        assertThat(pic.getName(), is(cp2.getPic().getName()));

        getDatastore().updateMany(query,
            getDatastore().createUpdateOperations(ContainsPic.class)
                          .set("pic", picKey));

        //test reading the object.
        final ContainsPic cp3 = getDatastore().find(ContainsPic.class).get();
        assertThat(cp3, is(notNullValue()));
        assertThat(cp.getName(), is(cp3.getName()));
        assertThat(cp3.getPic(), is(notNullValue()));
        assertThat(cp3.getPic().getName(), is(notNullValue()));
        assertThat(pic.getName(), is(cp3.getPic().getName()));
    }

    @Test(expected = ValidationException.class)
    public void testValidationBadFieldName() {
        getDatastore().updateMany(getDatastore().find(Circle.class).field("radius").equal(0),
            getDatastore().createUpdateOperations(Circle.class).inc("r", 1D));
    }

    private static class ContainsIntArray {
        private final Integer[] values = {1, 2, 3};
        @Id
        private ObjectId id;
    }

    private static class ContainsInt {
        @Id
        private ObjectId id;
        private int val;
    }

    @Entity
    private static class ContainsPicKey {
        @Id
        private ObjectId id;
        private String name = "test";
        private Key<Pic> pic;
        private List<Key<Pic>> keys;
    }

    @Entity(useDiscriminator = false)
    public static class EntityLogs {
        @Id
        private ObjectId id;
        @Indexed
        private String uuid;
        private List<EntityLog> logs = new ArrayList<>();
        private Document raw;

        @PreLoad
        public void preload(final Document raw) {
            this.raw = raw;
        }
    }

    @Embedded(useDiscriminator = false)
    public static class EntityLog {
        private Date receivedTs;
        private String value;

        public EntityLog() {
        }

        EntityLog(final String value, final Date date) {
            this.value = value;
            receivedTs = date;
        }

        @Override
        public int hashCode() {
            int result = receivedTs != null ? receivedTs.hashCode() : 0;
            result = 31 * result + (value != null ? value.hashCode() : 0);
            return result;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof EntityLog)) {
                return false;
            }

            final EntityLog entityLog = (EntityLog) o;

            return receivedTs != null ? receivedTs.equals(entityLog.receivedTs)
                                      : entityLog.receivedTs == null && (value != null ? value.equals(entityLog.value)
                                                                                       : entityLog.value == null);

        }


        @Override
        public String toString() {
            return format("EntityLog{receivedTs=%s, value='%s'}", receivedTs, value);
        }
    }

    private static final class Parent {
        private final Set<Child> children = new HashSet<>();
        @Id
        private ObjectId id;
    }

    @Embedded
    private static final class Child {
        private String first;
        private String last;

        private Child(final String first, final String last) {
            this.first = first;
            this.last = last;
        }

        private Child() {
        }

        @Override
        public int hashCode() {
            int result = first != null ? first.hashCode() : 0;
            result = 31 * result + (last != null ? last.hashCode() : 0);
            return result;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            Child child = (Child) o;

            return first != null ? first.equals(child.first)
                                 : child.first == null && (last != null ? last.equals(child.last) : child.last == null);

        }
    }

    private static final class DumbColl {
        @Id
        private String opaqueId;
        private List<DumbArrayElement> fromArray;

        private DumbColl() {
        }

        private DumbColl(final String opaqueId) {
            this.opaqueId = opaqueId;
        }
    }

    private static final class DumbArrayElement {
        private String whereId;

        private DumbArrayElement() {
        }

        private DumbArrayElement(final String whereId) {
            this.whereId = whereId;
        }
    }
}
