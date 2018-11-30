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

import com.mongodb.client.ListIndexesIterable;
import com.mongodb.client.MongoCollection;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import xyz.morphia.annotations.Collation;
import xyz.morphia.annotations.Embedded;
import xyz.morphia.annotations.Entity;
import xyz.morphia.annotations.Field;
import xyz.morphia.annotations.Id;
import xyz.morphia.annotations.Index;
import xyz.morphia.annotations.IndexOptions;
import xyz.morphia.annotations.Indexed;
import xyz.morphia.annotations.Indexes;
import xyz.morphia.annotations.Property;
import xyz.morphia.annotations.Text;
import xyz.morphia.internal.CollationBuilder;
import xyz.morphia.internal.FieldBuilder;
import xyz.morphia.internal.IndexBuilder;
import xyz.morphia.internal.IndexHelper;
import xyz.morphia.internal.IndexOptionsBuilder;
import xyz.morphia.internal.IndexedBuilder;
import xyz.morphia.internal.TextBuilder;
import xyz.morphia.mapping.MappedClass;
import xyz.morphia.mapping.Mapper;
import xyz.morphia.mapping.MappingException;
import xyz.morphia.utils.IndexType;

import java.util.concurrent.TimeUnit;

import static com.mongodb.client.model.CollationAlternate.SHIFTED;
import static com.mongodb.client.model.CollationCaseFirst.UPPER;
import static com.mongodb.client.model.CollationMaxVariable.SPACE;
import static com.mongodb.client.model.CollationStrength.IDENTICAL;
import static com.mongodb.client.model.CollationStrength.SECONDARY;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.bson.Document.parse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@SuppressWarnings("unused")
public class IndexHelperTest extends TestBase {
    private final IndexHelper indexHelper = new IndexHelper(getMapper(), getDatabase());

    @Before
    public void before() {
        getMapper().map(AbstractParent.class, IndexedClass.class, NestedClass.class, NestedClassImpl.class);
    }

    @Test
    public void calculateBadKeys() {
        MappedClass mappedClass = getMapper().getMappedClass(IndexedClass.class);
        IndexBuilder index = new IndexBuilder()
            .fields(new FieldBuilder()
                        .value("texting")
                        .type(IndexType.TEXT)
                        .weight(1),
                    new FieldBuilder()
                        .value("nest")
                        .type(IndexType.DESC));
        try {
            indexHelper.calculateKeys(mappedClass, index);
            fail("Validation should have failed on the bad key");
        } catch (MappingException e) {
            // all good
        }

        index.options(new IndexOptionsBuilder().disableValidation(true));
        indexHelper.calculateKeys(mappedClass, index);
    }

    @Test
    public void calculateKeys() {
        MappedClass mappedClass = getMapper().getMappedClass(IndexedClass.class);
        BsonDocument keys = indexHelper.calculateKeys(mappedClass, new IndexBuilder()
            .fields(new FieldBuilder()
                        .value("text")
                        .type(IndexType.TEXT)
                        .weight(1),
                    new FieldBuilder()
                        .value("nest")
                        .type(IndexType.DESC)));
        assertEquals(new BsonDocument()
                         .append("text", new BsonString("text"))
                         .append("nest", new BsonInt32(-1)),
                     keys);
    }

    @Test
    public void createIndex() {
        checkMinServerVersion(3.4);
        String collectionName = getDatastore().getCollection(IndexedClass.class).getNamespace().getCollectionName();
        MongoCollection<Document> collection = getDatabase().getCollection(collectionName);
        Mapper mapper = getMapper();

        indexHelper.createIndex(collection, mapper.getMappedClass(IndexedClass.class), false);
        ListIndexesIterable<Document> indexInfo = getDatastore().getCollection(IndexedClass.class)
                                                                .listIndexes();
        for (Document document : indexInfo) {
            String name = document.get("name").toString();
            switch (name) {
                case "latitude_1":
                    assertEquals(parse("{ 'latitude' : 1 }"), document.get("key"));
                    break;
                case "behind_interface":
                    assertEquals(parse("{ 'nest.name' : -1} "), document.get("key"));
                    assertEquals(
                        parse("{ 'locale' : 'en' , 'caseLevel' : false , 'caseFirst' : 'off' , 'strength' : 2 , 'numericOrdering' :"
                              + " false , 'alternate' : 'non-ignorable' , 'maxVariable' : 'punct' , 'normalization' : false , "
                              + "'backwards' : false , 'version' : '57.1'}"), document.get("collation"));
                    break;
                case "nest.name_1":
                    assertEquals(parse("{ 'nest.name' : 1} "), document.get("key"));
                    break;
                case "searchme":
                    assertEquals(parse("{ 'text' : 10 }"), document.get("weights"));
                    break;
                case "indexName_1":
                    assertEquals(parse("{'indexName': 1 }"), document.get("key"));
                    break;
                default:
                    if (!"_id_".equals(document.get("name"))) {
                        throw new MappingException("Found an index I wasn't expecting:  " + document);
                    }
                    break;
            }
        }

        collection = getDatabase().getCollection(getDatastore().getCollection(AbstractParent.class).getNamespace().getCollectionName());
        indexHelper.createIndex(collection, mapper.getMappedClass(AbstractParent.class), false);
        indexInfo = getDatastore().getCollection(AbstractParent.class).listIndexes();
        assertFalse("Shouldn't find any indexes: " + indexInfo, indexInfo.iterator().hasNext());

    }

    @Test
    public void findField() {
        MappedClass mappedClass = getMapper().getMappedClass(IndexedClass.class);

        assertEquals("indexName", indexHelper.findField(mappedClass, new IndexOptionsBuilder(), singletonList("indexName")));
        assertEquals("nest.name", indexHelper.findField(mappedClass, new IndexOptionsBuilder(), asList("nested", "name")));
        assertEquals("nest.name", indexHelper.findField(mappedClass, new IndexOptionsBuilder(), asList("nest", "name")));

        try {
            assertEquals("nest.whatsit", indexHelper.findField(mappedClass, new IndexOptionsBuilder(), asList("nest", "whatsit")));
            fail("Should have failed on the bad index path");
        } catch (MappingException e) {
            // alles ist gut
        }
        assertEquals("nest.whatsit.nested.more.deeply.than.the.object.model",
                     indexHelper.findField(mappedClass, new IndexOptionsBuilder().disableValidation(true),
                                           asList("nest", "whatsit", "nested", "more", "deeply", "than", "the", "object", "model")));
    }

    @Test
    public void index() {
        checkMinServerVersion(3.4);
        MongoCollection<Document> indexes = getDatabase().getCollection("indexes");
        MappedClass mappedClass = getMapper().getMappedClass(IndexedClass.class);

        indexes.drop();
        Index index = new IndexBuilder()
            .fields(new FieldBuilder()
                        .value("indexName"),
                    new FieldBuilder()
                        .value("text")
                        .type(IndexType.DESC))
            .options(indexOptions());
        indexHelper.createIndex(indexes, mappedClass, index, false);
        ListIndexesIterable<Document> indexInfo = getDatastore().getCollection(IndexedClass.class)
                                                                .listIndexes();
        for (Document document : indexInfo) {
            if (document.get("name").equals("indexName")) {
                checkIndex(document);

                assertEquals("en", document.get("default_language"));
                assertEquals("de", document.get("language_override"));

                assertEquals(new Document()
                                 .append("locale", "en")
                                 .append("caseLevel", true)
                                 .append("caseFirst", "upper")
                                 .append("strength", 5)
                                 .append("numericOrdering", true)
                                 .append("alternate", "shifted")
                                 .append("maxVariable", "space")
                                 .append("backwards", true)
                                 .append("normalization", true)
                                 .append("version", "57.1"),
                             document.get("collation"));
            }
        }
    }

    @Test
    public void indexCollationConversion() {
        Collation collation = collation();
        com.mongodb.client.model.Collation driver = indexHelper.convert(collation);
        assertEquals("en", driver.getLocale());
        assertTrue(driver.getCaseLevel());
        assertEquals(UPPER, driver.getCaseFirst());
        assertEquals(IDENTICAL, driver.getStrength());
        assertTrue(driver.getNumericOrdering());
        assertEquals(SHIFTED, driver.getAlternate());
        assertEquals(SPACE, driver.getMaxVariable());
        assertTrue(driver.getNormalization());
        assertTrue(driver.getBackwards());
    }

    @Test
    public void indexOptionsConversion() {
        IndexOptionsBuilder indexOptions = indexOptions();
        com.mongodb.client.model.IndexOptions options = indexHelper.convert(indexOptions, false);
        assertEquals("index_name", options.getName());
        assertTrue(options.isBackground());
        assertTrue(options.isUnique());
        assertTrue(options.isSparse());
        assertEquals(Long.valueOf(42), options.getExpireAfter(TimeUnit.SECONDS));
        assertEquals("en", options.getDefaultLanguage());
        assertEquals("de", options.getLanguageOverride());
        assertEquals(indexHelper.convert(indexOptions.collation()), options.getCollation());

        assertTrue(indexHelper.convert(indexOptions, true).isBackground());
        assertTrue(indexHelper.convert(indexOptions.background(false), true).isBackground());
        assertTrue(indexHelper.convert(indexOptions.background(true), true).isBackground());
        assertTrue(indexHelper.convert(indexOptions.background(true), false).isBackground());
        assertFalse(indexHelper.convert(indexOptions.background(false), false).isBackground());

    }

    @Test
    public void convertTextIndex() {
        TextBuilder text = new TextBuilder()
            .value(4)
            .options(new IndexOptionsBuilder()
                         .name("index_name")
                         .background(true)
                         .expireAfterSeconds(42)
                         .sparse(true)
                         .unique(true));

        Index index = indexHelper.convert(text, "search_field");
        assertEquals(index.options().name(), "index_name");
        assertTrue(index.options().background());
        assertTrue(index.options().sparse());
        assertTrue(index.options().unique());
        assertEquals(new FieldBuilder()
                         .value("search_field")
                         .type(IndexType.TEXT)
                         .weight(4),
                     index.fields()[0]);

    }

    @Test
    @SuppressWarnings("deprecation")
    public void normalizeIndexed() {
        Indexed indexed = new IndexedBuilder()
            .value(IndexType.DESC)
            .options(new IndexOptionsBuilder().name("index_name")
                                              .background(true)
                                              .expireAfterSeconds(42)
                                              .sparse(true)
                                              .unique(true));

        Index converted = indexHelper.convert(indexed, "oldstyle");
        assertEquals(converted.options().name(), "index_name");
        assertTrue(converted.options().background());
        assertTrue(converted.options().sparse());
        assertTrue(converted.options().unique());
        assertEquals(new FieldBuilder().value("oldstyle").type(IndexType.DESC), converted.fields()[0]);
    }

    @Test
    public void wildcardTextIndex() {
        MongoCollection<Document> indexes = getDatabase().getCollection("indexes");
        MappedClass mappedClass = getMapper().getMappedClass(IndexedClass.class);

        IndexBuilder index = new IndexBuilder()
            .fields(new FieldBuilder()
                        .value("$**")
                        .type(IndexType.TEXT));

        indexHelper.createIndex(indexes, mappedClass, index, false);

        ListIndexesIterable<Document> wildcard = getDatabase().getCollection("indexes").listIndexes();
        boolean found = false;
        for (Document document : wildcard) {
            found |= document.get("name").equals("$**_text");
        }
        assertTrue("Should have found the wildcard index", found);
    }

    @Test(expected = MappingException.class)
    public void weightsOnNonTextIndex() {
        MongoCollection<Document> indexes = getDatabase().getCollection("indexes");
        MappedClass mappedClass = getMapper().getMappedClass(IndexedClass.class);

        IndexBuilder index = new IndexBuilder()
            .fields(new FieldBuilder()
                        .value("name")
                        .weight(10));

        indexHelper.createIndex(indexes, mappedClass, index, false);
    }

    @Test
    public void indexPartialFilters() {
        MongoCollection<Document> collection = getDatabase().getCollection("indexes");
        MappedClass mappedClass = getMapper().getMappedClass(IndexedClass.class);

        Index index = new IndexBuilder()
            .fields(new FieldBuilder().value("text"))
            .options(new IndexOptionsBuilder()
                         .partialFilter("{ name : { $gt : 13 } }"));

        indexHelper.createIndex(collection, mappedClass, index, false);
        findPartialIndex(parse(index.options().partialFilter()));
    }

    @Test
    public void indexedPartialFilters() {
        MongoCollection<Document> collection = getDatabase().getCollection("indexes");
        MappedClass mappedClass = getMapper().getMappedClass(IndexedClass.class);

        Indexed indexed = new IndexedBuilder()
            .options(new IndexOptionsBuilder()
                         .partialFilter("{ name : { $gt : 13 } }"));

        indexHelper.createIndex(collection, mappedClass, indexHelper.convert(indexed, "text"), false);
        findPartialIndex(parse(indexed.options().partialFilter()));
    }

    @Test
    public void textPartialFilters() {
        MongoCollection<Document> collection = getDatabase().getCollection("indexes");
        MappedClass mappedClass = getMapper().getMappedClass(IndexedClass.class);

        Text text = new TextBuilder()
            .value(4)
            .options(new IndexOptionsBuilder()
                         .partialFilter("{ name : { $gt : 13 } }"));

        indexHelper.createIndex(collection, mappedClass, indexHelper.convert(text, "text"), false);
        findPartialIndex(parse(text.options().partialFilter()));
    }

    private void checkIndex(final Document document) {
        assertTrue((Boolean) document.get("background"));
        assertTrue((Boolean) document.get("unique"));
        assertTrue((Boolean) document.get("sparse"));
        assertEquals(42L, document.get("expireAfterSeconds"));
        assertEquals(new Document("name", 1).append("text", -1), document.get("key"));
    }

    private void findPartialIndex(final Document expected) {
        ListIndexesIterable<Document> indexInfo = getDatastore().getCollection(IndexedClass.class)
                                                                .listIndexes();
        for (Document document : indexInfo) {
            if (!document.get("name").equals("_id_")) {
                Assert.assertEquals(expected, document.get("partialFilterExpression"));
            }
        }
    }

    private Collation collation() {
        return new CollationBuilder()
            .alternate(SHIFTED)
            .backwards(true)
            .caseFirst(UPPER)
            .caseLevel(true)
            .locale("en")
            .maxVariable(SPACE)
            .normalization(true)
            .numericOrdering(true)
            .strength(IDENTICAL);
    }

    private IndexOptionsBuilder indexOptions() {
        return new IndexOptionsBuilder()
            .name("index_name")
            .background(true)
            .collation(collation())
            .disableValidation(true)
            .expireAfterSeconds(42)
            .language("en")
            .languageOverride("de")
            .sparse(true)
            .unique(true);
    }

    @Embedded
    private interface NestedClass {
    }

    @Entity("indexes")
    @Indexes(@Index(fields = @Field("latitude")))
    private static class IndexedClass extends AbstractParent {
        @Text(value = 10, options = @IndexOptions(name = "searchme"))
        private String text;
        private double latitude;
        @Property("nest")
        private NestedClass nested;
    }

    @Indexes(
        @Index(fields = @Field(value = "name", type = IndexType.DESC),
            options = @IndexOptions(name = "behind_interface",
                collation = @Collation(locale = "en", strength = SECONDARY))))
    private static class NestedClassImpl implements NestedClass {
        @Indexed
        private String name;
    }

    @Indexes(@Index(fields = @Field("indexName")))
    private abstract static class AbstractParent {
        @Id
        private ObjectId id;
        private double indexName;
    }
}
