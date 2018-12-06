/*
 * Copyright (c) 2008-2015 MongoDB, Inc.
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

package xyz.morphia.indexes;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.CollationCaseFirst;
import com.mongodb.client.model.CollationMaxVariable;
import com.mongodb.client.model.CollationStrength;
import org.bson.Document;
import org.junit.Test;
import xyz.morphia.Datastore;
import xyz.morphia.TestBase;
import xyz.morphia.annotations.Collation;
import xyz.morphia.annotations.Entity;
import xyz.morphia.annotations.Field;
import xyz.morphia.annotations.Id;
import xyz.morphia.annotations.Index;
import xyz.morphia.annotations.IndexOptions;
import xyz.morphia.annotations.Indexes;
import xyz.morphia.utils.IndexType;

import java.util.ArrayList;
import java.util.List;

import static com.mongodb.client.model.CollationAlternate.SHIFTED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TestIndexes extends TestBase {

    @Test
    public void testIndexes() {

        final Datastore datastore = getDatastore();
        datastore.delete(datastore.find(TestWithIndexOption.class));

        getDatastore().getCollection(TestWithIndexOption.class)
                      .drop();

        getDatastore().getCollection(TestWithHashedIndex.class)
                      .drop();

        if (serverIsAtLeastVersion(3.4)) {
            datastore.ensureIndexes(TestWithIndexOption.class, false);
            List<Document> indexes = getDatastore()
                                         .getCollection(TestWithIndexOption.class)
                                         .listIndexes()
                                         .into(new ArrayList<>());


            assertEquals(2, indexes.size());

            for (Document document : indexes) {
                if (document.get("name").equals("collated")) {
                    assertEquals(Document.parse("{ name : { $exists : true } }"),
                        document.get("partialFilterExpression"));
                    Document collation = (Document) document.get("collation");
                    assertEquals("en_US", collation.get("locale"));
                    assertEquals("upper", collation.get("caseFirst"));
                    assertEquals("shifted", collation.get("alternate"));
                    assertTrue(collation.getBoolean("backwards"));
                    assertEquals("upper", collation.get("caseFirst"));
                    assertTrue(collation.getBoolean("caseLevel"));
                    assertEquals("space", collation.get("maxVariable"));
                    assertTrue(collation.getBoolean("normalization"));
                    assertTrue(collation.getBoolean("numericOrdering"));
                    assertEquals(5, collation.get("strength"));
                }
            }
        }

        datastore.ensureIndexes(TestWithHashedIndex.class);
        final MongoCollection<TestWithHashedIndex> hashIndexColl = getDatastore().getCollection(
            TestWithHashedIndex.class);
        assertEquals(2, count(hashIndexColl.listIndexes().iterator()));
        assertHashed(hashIndexColl.listIndexes().into(new ArrayList<>()));
    }

    private void assertHashed(final List<Document> indexInfo) {
        for (final Document document : indexInfo) {
            if (!document.getString("name").equals("_id_")) {
                assertEquals(((Document) document.get("key")).get("hashedValue"), "hashed");
            }
        }
    }

    @SuppressWarnings("unused")
    @Entity(useDiscriminator = false)
    @Indexes({@Index(options = @IndexOptions(name = "collated",
        partialFilter = "{ name : { $exists : true } }",
        collation = @Collation(locale = "en_US", alternate = SHIFTED, backwards = true,
            caseFirst = CollationCaseFirst.UPPER, caseLevel = true, maxVariable = CollationMaxVariable.SPACE, normalization = true,
            numericOrdering = true, strength = CollationStrength.IDENTICAL)),
        fields = {@Field(value = "name")})})
    private static class TestWithIndexOption {
        @Id
        private Object id;
        private String name;

    }

    @SuppressWarnings("unused")
    @Entity(useDiscriminator = false)
    @Indexes({@Index(options = @IndexOptions(), fields = {@Field(value = "hashedValue", type = IndexType.HASHED)})})
    private static class TestWithHashedIndex {
        @Id
        private Object id;
        private String hashedValue;
    }


}
