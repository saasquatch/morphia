/*
 * Copyright 2016 MongoDB, Inc.
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

package xyz.morphia;

import com.mongodb.MongoCommandException;
import com.mongodb.MongoException;
import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.InsertManyOptions;
import com.mongodb.client.model.InsertOneOptions;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.ValidationAction;
import com.mongodb.client.model.ValidationLevel;
import com.mongodb.client.model.ValidationOptions;
import org.bson.Document;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import xyz.morphia.annotations.Validation;
import xyz.morphia.entities.DocumentValidation;
import xyz.morphia.internal.DatastoreImpl;
import xyz.morphia.internal.ValidationBuilder;
import xyz.morphia.mapping.MappedClass;
import xyz.morphia.query.Query;
import xyz.morphia.query.UpdateOperations;

import java.util.Date;
import java.util.EnumSet;
import java.util.List;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class TestDocumentValidation extends TestBase {
    @Before
    public void versionCheck() {
        checkMinServerVersion(3.2);
    }

    @Test
    public void createValidation() {
        getMapper().map(DocumentValidation.class);
        getDatastore().enableDocumentValidation();
        assertEquals(Document.parse(DocumentValidation.class.getAnnotation(Validation.class).value()), getValidator());

        try {
            getDatastore().save(new DocumentValidation("John", 1, new Date()));
            fail("Document should have failed validation");
        } catch (MongoException e) {
            assertTrue(e.getMessage().contains("Document failed validation"));
        }

        getDatastore().save(new DocumentValidation("Harold", 100, new Date()));

    }

    @Test
    public void overwriteValidation() {
        Document validator = Document.parse("{ jelly : { $ne : 'rhubarb' } }");
        MongoDatabase database = addValidation(validator, "validation");

        assertEquals(validator, getValidator());

        Document rhubarb = new Document("jelly", "rhubarb").append("number", 20);
        database.getCollection("validation").insertOne(new Document("jelly", "grape"));
        try {
            database.getCollection("validation").insertOne(rhubarb);
            fail("Document should have failed validation");
        } catch (MongoWriteException e) {
            assertTrue(e.getMessage().contains("Document failed validation"));
        }

        getMapper().map(DocumentValidation.class);
        getDatastore().enableDocumentValidation();
        assertEquals(Document.parse(DocumentValidation.class.getAnnotation(Validation.class).value()), getValidator());

        try {
            database.getCollection("validation").insertOne(rhubarb);
        } catch (MongoWriteException e) {
            assertFalse(e.getMessage().contains("Document failed validation"));
        }

        try {
            getDatastore().save(new DocumentValidation("John", 1, new Date()));
            fail("Document should have failed validation");
        } catch (MongoException e) {
            assertTrue(e.getMessage().contains("Document failed validation"));
        }
    }

    private MongoDatabase addValidation(final Document validator, final String collectionName) {
        ValidationOptions options = new ValidationOptions()
            .validator(validator)
            .validationLevel(ValidationLevel.MODERATE)
            .validationAction(ValidationAction.ERROR);
        MongoDatabase database = getMongoClient().getDatabase(TEST_DB_NAME);
        database.getCollection(collectionName).drop();
        database.createCollection(collectionName, new CreateCollectionOptions().validationOptions(options));
        return database;
    }

    @Test
    public void validationDocuments() {
        Document validator = Document.parse("{ jelly : { $ne : 'rhubarb' } }");
        getMapper().map(DocumentValidation.class);
        MappedClass mappedClass = getMapper().getMappedClass(DocumentValidation.class);

        for (ValidationLevel level : EnumSet.allOf(ValidationLevel.class)) {
            for (ValidationAction action : EnumSet.allOf(ValidationAction.class)) {
                checkValidation(validator, mappedClass, level, action);
            }
        }
    }

    @Test
    public void findAndModify() {
        getMapper().map(DocumentValidation.class);
        getDatastore().enableDocumentValidation();

        getDatastore().save(new DocumentValidation("Harold", 100, new Date()));

        Query<DocumentValidation> query = getDatastore().find(DocumentValidation.class);
        UpdateOperations<DocumentValidation> updates = getDatastore().createUpdateOperations(DocumentValidation.class)
                                                                     .set("number", 5);
        FindOneAndUpdateOptions options = new FindOneAndUpdateOptions()
                                              .bypassDocumentValidation(false);
        try {
            getDatastore().findAndModify(query, updates, options,
                getDatastore().getDefaultWriteConcern());
            fail("Document validation should have complained");
        } catch (MongoCommandException e) {
            // expected
        }

        options.bypassDocumentValidation(true);
        getDatastore().findAndModify(query, updates, options,
            getDatastore().getDefaultWriteConcern());

        Assert.assertNotNull(query.field("number").equal(5).get());
    }

    @Test
    public void update() {
        getMapper().map(DocumentValidation.class);
        getDatastore().enableDocumentValidation();

        getDatastore().save(new DocumentValidation("Harold", 100, new Date()));

        Query<DocumentValidation> query = getDatastore().find(DocumentValidation.class);
        UpdateOperations<DocumentValidation> updates = getDatastore().createUpdateOperations(DocumentValidation.class)
                                                                     .set("number", 5);
        UpdateOptions options = new UpdateOptions()
                                    .bypassDocumentValidation(false);
        try {
            getDatastore().updateMany(query, updates, options,
                getDatastore().getDefaultWriteConcern());
            fail("Document validation should have complained");
        } catch (MongoException e) {
            // expected
        }

        options.bypassDocumentValidation(true);
        getDatastore().updateMany(query, updates, options,
            getDatastore().getDefaultWriteConcern());

        Assert.assertNotNull(query.field("number").equal(5).get());
    }

    @Test
    public void save() {
        getMapper().map(DocumentValidation.class);
        getDatastore().enableDocumentValidation();

        try {
            getDatastore().save(new DocumentValidation("Harold", 8, new Date()));
            fail("Document validation should have complained");
        } catch (MongoException e) {
            // expected
        }

        getDatastore().save(new DocumentValidation("Harold", 8, new Date()), new InsertOneOptions()
                                                                                 .bypassDocumentValidation(true),
            getDatastore().getDefaultWriteConcern());

        Query<DocumentValidation> query = getDatastore().find(DocumentValidation.class)
                                                        .field("number").equal(8);
        Assert.assertNotNull(query.get());

        List<DocumentValidation> list = asList(new DocumentValidation("Harold", 8, new Date()),
            new DocumentValidation("Harold", 8, new Date()),
            new DocumentValidation("Harold", 8, new Date()),
            new DocumentValidation("Harold", 8, new Date()),
            new DocumentValidation("Harold", 8, new Date()));
        try {
            getDatastore().saveMany(list);
            fail("Document validation should have complained");
        } catch (MongoException e) {
            // expected
        }

        getDatastore().saveMany(list, new InsertManyOptions()
                                      .bypassDocumentValidation(true),
            getDatastore().getDefaultWriteConcern());

        Assert.assertFalse(query.field("number").equal(8).asList().isEmpty());
    }

    @Test
    public void saveToNewCollection() {
        getMapper().map(DocumentValidation.class);
        final Document validator = Document.parse("{ number : { $gt : 10 } }");
        String collection = "newdocs";
        addValidation(validator, collection);

        try {
            getAds().save(collection, new DocumentValidation("Harold", 8, new Date()));
            fail("Document validation should have complained");
        } catch (MongoException e) {
            // expected
        }

        getAds().save(collection, new DocumentValidation("Harold", 8, new Date()), new InsertOneOptions()
                                                                                       .bypassDocumentValidation(true),
            getDatastore().getDefaultWriteConcern());

        Query<DocumentValidation> query = getAds().createQuery(collection, DocumentValidation.class)
                                                  .field("number").equal(8);
        Assert.assertNotNull(query.get());
    }

    @Test
    public void insert() {
        getMapper().map(DocumentValidation.class);
        getDatastore().enableDocumentValidation();

        try {
            getAds().insertOne(new DocumentValidation("Harold", 8, new Date()));
            fail("Document validation should have complained");
        } catch (MongoWriteException e) {
            // expected
        }

        getAds().insertOne(new DocumentValidation("Harold", 8, new Date()), new InsertOneOptions()
                                                                             .bypassDocumentValidation(true),
            getDatastore().getDefaultWriteConcern());

        Query<DocumentValidation> query = getDatastore().find(DocumentValidation.class)
                                                        .field("number").equal(8);
        Assert.assertNotNull(query.get());

        List<DocumentValidation> list = asList(new DocumentValidation("Harold", 8, new Date()),
            new DocumentValidation("John", 8, new Date()),
            new DocumentValidation("Sarah", 8, new Date()),
            new DocumentValidation("Amy", 8, new Date()),
            new DocumentValidation("James", 8, new Date()));
        try {
            getAds().insertMany(list);
            fail("Document validation should have complained");
        } catch (MongoException e) {
            // expected
        }

        getAds().insertMany(list, new InsertManyOptions()
                                  .bypassDocumentValidation(true), getDatastore().getDefaultWriteConcern());

        Assert.assertFalse(query.field("number").equal(8).asList().isEmpty());
    }


    private void checkValidation(final Document validator, final MappedClass mappedClass, final ValidationLevel level,
                                 final ValidationAction action) {
        updateValidation(mappedClass, level, action);
        Document expected = new Document("validator", validator)
            .append("validationLevel", level.getValue())
            .append("validationAction", action.getValue());

        Document validation = getValidation();
        for (String key : expected.keySet()) {
            assertEquals(expected.get(key), validation.get(key));
        }
    }

    @SuppressWarnings("unchecked")
    private Document getValidation() {
        Document document = getMongoClient().getDatabase(TEST_DB_NAME)
                                            .runCommand(new Document("listCollections", 1)
                                                            .append("filter", new Document("name", "validation")));

        List<Document> firstBatch = (List<Document>) ((Document) document.get("cursor")).get("firstBatch");
        return (Document) firstBatch.get(0).get("options");
    }

    private Document getValidator() {
        return (Document) getValidation().get("validator");
    }

    private void updateValidation(final MappedClass mappedClass, final ValidationLevel level, final ValidationAction action) {
        ((DatastoreImpl) getDatastore()).process(mappedClass, new ValidationBuilder().value("{ jelly : { $ne : 'rhubarb' } }")
                                                                                     .level(level)
                                                                                     .action(action));
    }
}
