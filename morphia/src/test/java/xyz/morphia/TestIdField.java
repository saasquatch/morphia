/*
  Copyright (C) 2010 Olafur Gauti Gudmundsson
  <p/>
  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may
  obtain a copy of the License at
  <p/>
  http://www.apache.org/licenses/LICENSE-2.0
  <p/>
  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions
  and limitations under the License.
 */


package xyz.morphia;


import org.bson.Document;
import org.junit.Assert;
import org.junit.Test;
import xyz.morphia.annotations.Embedded;
import xyz.morphia.annotations.Entity;
import xyz.morphia.annotations.Id;
import xyz.morphia.mapping.Mapper;
import xyz.morphia.testmodel.Rectangle;

import java.util.HashMap;
import java.util.Map;

import static java.util.Collections.singletonList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class TestIdField extends TestBase {

    @Test
    public void embeddedIds() {
        final MyId id = new MyId("1", "2");

        final EmbeddedId a = new EmbeddedId(id, "data");
        final EmbeddedId b = new EmbeddedId(new MyId("2", "3"), "data, too");

        getDatastore().save(a);
        getDatastore().save(b);

        assertEquals(a.data, getDatastore().find(EmbeddedId.class).filter("_id", id).get().data);

        final EmbeddedId embeddedId = getDatastore().find(EmbeddedId.class).field("_id").in(singletonList(id)).asList().get(0);
        Assert.assertEquals(a.data, embeddedId.data);
        Assert.assertEquals(a.id, embeddedId.id);
    }

    @Test
    public void testIdFieldNameMapping() {
        final Document document = getMapper().toDocument(new Rectangle(1, 12));
        assertFalse(document.containsKey("id"));
        assertTrue(document.containsKey(Mapper.ID_KEY));
        assertTrue(document.containsKey(Mapper.CLASS_NAME_FIELDNAME));
        assertEquals(document.keySet().toString(), 4, document.size()); //_id, h, w, className
    }

    @Test
    public void testKeyAsId() {
        getMapper().map(KeyAsId.class, Rectangle.class);

        final Rectangle r = new Rectangle(1, 1);
        //        Rectangle r2 = new Rectangle(11,11);

        final Key<Rectangle> rKey = getDatastore().save(r);
        //        Key<Rectangle> r2Key = ds.save(r2);
        final KeyAsId kai = new KeyAsId(rKey);
        final Key<KeyAsId> kaiKey = getDatastore().save(kai);
        final KeyAsId kaiLoaded = getDatastore().find(KeyAsId.class).filter("_id", rKey).get();
        assertNotNull(kaiLoaded);
        assertNotNull(kaiKey);
    }

    @Test
    public void testMapAsId() {
        getMapper().map(MapAsId.class);

        final MapAsId mai = new MapAsId();
        mai.id.put("test", "string");
        final Key<MapAsId> maiKey = getDatastore().save(mai);
        final MapAsId maiLoaded = getDatastore().find(MapAsId.class).filter("_id", new Document("test", "string")).get();
        assertNotNull(maiLoaded);
        assertNotNull(maiKey);
    }

    @Entity
    private static class KeyAsId {
        @Id
        private Key<Rectangle> id;

        private KeyAsId() {
        }

        KeyAsId(final Key<Rectangle> key) {
            id = key;
        }
    }

    @Entity
    private static class MapAsId {
        @Id
        private final Map<String, String> id = new HashMap<>();
    }

    @Entity(useDiscriminator = false)
    public static class EmbeddedId {

        @Id
        private MyId id;
        private String data;

        public EmbeddedId() {
        }

        public EmbeddedId(final MyId myId, final String data) {
            id = myId;
            this.data = data;
        }
    }

    @Embedded
    public static class MyId {
        private String myIdPart1;
        private String myIdPart2;

        public MyId() {
        }

        public MyId(final String myIdPart1, final String myIdPart2) {
            this.myIdPart1 = myIdPart1;
            this.myIdPart2 = myIdPart2;
        }

        @Override
        public int hashCode() {
            int result = myIdPart1.hashCode();
            result = 31 * result + myIdPart2.hashCode();
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

            final MyId myId = (MyId) o;

            if (!myIdPart1.equals(myId.myIdPart1)) {
                return false;
            }
            if (!myIdPart2.equals(myId.myIdPart2)) {
                return false;
            }

            return true;
        }
    }
}
