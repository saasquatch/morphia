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

package xyz.morphia.mapping;

import com.mongodb.client.ListIndexesIterable;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.Assert;
import org.junit.Test;
import xyz.morphia.TestBase;
import xyz.morphia.annotations.Embedded;
import xyz.morphia.annotations.Entity;
import xyz.morphia.annotations.Field;
import xyz.morphia.annotations.Id;
import xyz.morphia.annotations.Index;
import xyz.morphia.annotations.IndexOptions;
import xyz.morphia.annotations.Indexes;
import xyz.morphia.query.ValidationException;

import java.util.HashMap;
import java.util.Map;

public class EmbeddedMappingTest extends TestBase {
    @Test
    public void mapGenericEmbeds() {
        getMapper().map(AuditEntry.class, Delta.class);

        final AuditEntry entry = new AuditEntry();

        final HashMap<String, Integer> before = new HashMap<>();
        final HashMap<String, Integer> after = new HashMap<>();
        before.put("before", 42);
        after.put("after", 84);

        entry.delta = new Delta<>(before, after);
        getDatastore().save(entry);

        final AuditEntry fetched = getDatastore().find(AuditEntry.class)
                                                 .filter("id = ", entry.id).first();

        Assert.assertEquals(entry, fetched);
    }

    @Test
    public void testNestedInterfaces() {
        getMapper().map(WithNested.class, NestedImpl.class);
        getDatastore().ensureIndexes();

        WithNested nested = new WithNested();
        nested.nested = new NestedImpl("nested value");
        getDatastore().save(nested);

        WithNested found;
        try {
            getDatastore().find(WithNested.class)
                          .field("nested.field").equal("nested value").first();
            Assert.fail("Querying against an interface should fail validation");
        } catch (ValidationException ignore) {
            // all good
        }
        found = getDatastore().find(WithNested.class)
                              .disableValidation()
                              .field("nested.field").equal("nested value").first();
        Assert.assertNotNull(found);
        Assert.assertEquals(nested, found);

        found = getDatastore().find(WithNested.class)
                              .disableValidation()
                              .field("nested.field.fails").equal("nested value").first();
        Assert.assertNull(found);
    }

    @Test
    public void validateNestedInterfaces() {
        getMapper().map(WithNestedValidated.class, Nested.class, NestedImpl.class, AnotherNested.class);
        try {
            getDatastore().ensureIndexes();
        } catch (MappingException e) {
            Assert.assertEquals("Could not resolve path 'nested.field.fail' against 'xyz.morphia.mapping"
                                + ".EmbeddedMappingTest$WithNestedValidated'.", e.getMessage());
        }

        final ListIndexesIterable<Document> indexInfo = getDatastore().getCollection(WithNestedValidated.class).listIndexes();
        boolean indexFound = false;
        for (Document dbObject : indexInfo) {
            indexFound |= "nested.field.fail".equals(((Document) dbObject.get("key")).keySet().iterator().next());
        }
        Assert.assertFalse("Should not find the nested field index", indexFound);
    }

    public interface Nested {
    }

    @Entity(value = "audit", useDiscriminator = false)
    public static class AuditEntry {
        @Id
        private ObjectId id;

        private Delta<Integer> delta;

        @Override
        public int hashCode() {
            int result = id != null ? id.hashCode() : 0;
            result = 31 * result + delta.hashCode();
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

            final AuditEntry that = (AuditEntry) o;

            if (id != null ? !id.equals(that.id) : that.id != null) {
                return false;
            }
            return delta.equals(that.delta);

        }

    }

    @Embedded
    public static class Delta<T> {
        private Map<String, T> before;
        private Map<String, T> after;

        private Delta() {
        }

        public Delta(final Map<String, T> before, final Map<String, T> after) {
            this.before = before;
            this.after = after;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            final Delta<?> delta = (Delta<?>) o;

            if (!before.equals(delta.before)) {
                return false;
            }
            return after.equals(delta.after);

        }

        @Override
        public int hashCode() {
            int result = before.hashCode();
            result = 31 * result + after.hashCode();
            return result;
        }
    }

    @Embedded
    public static class NestedImpl implements Nested {
        private String field;

        public NestedImpl() {
        }

        public NestedImpl(final String field) {
            this.field = field;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            final NestedImpl nested = (NestedImpl) o;

            return field != null ? field.equals(nested.field) : nested.field == null;

        }

        @Override
        public int hashCode() {
            return field != null ? field.hashCode() : 0;
        }
    }

    @Embedded
    public static class AnotherNested implements Nested {
        private Long value;
    }

    @Indexes({
        @Index(fields = {@Field("nested.field.fail")},
            options = @IndexOptions(disableValidation = true, sparse = true))
        })
    public static class WithNested {
        @Id
        private ObjectId id;
        private Nested nested;

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            final WithNested that = (WithNested) o;

            if (id != null ? !id.equals(that.id) : that.id != null) {
                return false;
            }
            return nested != null ? nested.equals(that.nested) : that.nested == null;

        }

        @Override
        public int hashCode() {
            int result = id != null ? id.hashCode() : 0;
            result = 31 * result + (nested != null ? nested.hashCode() : 0);
            return result;
        }
    }

    @Indexes(@Index(fields = {@Field("nested.field.fail")}))
    public static class WithNestedValidated {
        @Id
        private ObjectId id;
        private Nested nested;
    }
}
