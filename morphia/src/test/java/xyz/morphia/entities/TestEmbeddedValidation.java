/*
 * Copyright (c) 2008-2016 MongoDB, Inc.
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

package xyz.morphia.entities;

import org.bson.types.ObjectId;
import org.junit.Assert;
import org.junit.Test;
import xyz.morphia.Datastore;
import xyz.morphia.TestBase;
import xyz.morphia.annotations.Entity;
import xyz.morphia.annotations.Id;
import xyz.morphia.query.Query;
import xyz.morphia.query.UpdateOperations;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static java.util.Arrays.asList;

public class TestEmbeddedValidation extends TestBase {

    @Test
    public void testDottedNames() {
        getMapper().map(ParentType.class, EmbeddedType.class, EmbeddedSubtype.class);
        ParentType parentType = new ParentType();
        EmbeddedSubtype embedded = new EmbeddedSubtype();
        embedded.setText("text");
        embedded.setNumber(42L);
        embedded.setFlag(true);
        parentType.setEmbedded(embedded);

        Datastore ds = getDatastore();
        ds.save(parentType);

        Query<ParentType> query = ds.find(ParentType.class)
                                    .disableValidation()
                                    .field("embedded.flag").equal(true);

        final ParentType actual = query.first();
        Assert.assertEquals(parentType, actual);
    }

    @Test
    public void testEmbeddedListQueries() {
        EntityWithListsAndArrays entity = new EntityWithListsAndArrays();
        EmbeddedType fortyTwo = new EmbeddedType(42L, "forty-two");
        entity.setListEmbeddedType(asList(fortyTwo, new EmbeddedType(1L, "one")));
        getDatastore().save(entity);

        Query<EntityWithListsAndArrays> query = getDatastore().find(EntityWithListsAndArrays.class)
                                                              .field("listEmbeddedType.number").equal(42L);
        List<EntityWithListsAndArrays> list = query.asList();

        Assert.assertEquals(1, list.size());
        Assert.assertEquals(fortyTwo, list.get(0).getListEmbeddedType().get(0));

        UpdateOperations<EntityWithListsAndArrays> operations = getDatastore()
            .createUpdateOperations(EntityWithListsAndArrays.class)
            .set("listEmbeddedType.$.number", 0);
        getDatastore().updateMany(query, operations);

        Assert.assertEquals(0, query.count());

        fortyTwo.setNumber(0L);
        query = getDatastore().find(EntityWithListsAndArrays.class)
                              .field("listEmbeddedType.number").equal(0);
        list = query.asList();

        Assert.assertEquals(1, list.size());
        Assert.assertEquals(fortyTwo, list.get(0).getListEmbeddedType().get(0));

    }

    @Entity
    public static class TestEntity {

        @Id
        private ObjectId id;
        private List<Map<String, Object>> data;

        public List<Map<String, Object>> getData() {
            return data;
        }

        public void setData(final List<Map<String, Object>> data) {
            this.data = new ArrayList<>();
            this.data.addAll(data);
        }

        public ObjectId getId() {
            return id;
        }

        @Override
        public int hashCode() {
            int result = getId() != null ? getId().hashCode() : 0;
            result = 31 * result + (getData() != null ? getData().hashCode() : 0);
            return result;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof TestEntity)) {
                return false;
            }

            final TestEntity that = (TestEntity) o;

            if (getId() != null ? !getId().equals(that.getId()) : that.getId() != null) {
                return false;
            }
            return getData() != null ? getData().equals(that.getData()) : that.getData() == null;

        }
    }
}
