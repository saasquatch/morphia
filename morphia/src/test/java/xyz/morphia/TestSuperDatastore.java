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

import com.mongodb.client.model.FindOptions;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.Test;
import xyz.morphia.testmodel.Circle;
import xyz.morphia.testmodel.Rectangle;

import static org.junit.Assert.assertEquals;

public class TestSuperDatastore extends TestBase {
    @Test
    public void testDeleteDoesNotDeleteAnythingWhenGivenAnIncorrectId() {
        // given
        final String ns = "someCollectionName";
        getDatabase().getCollection(ns).deleteMany(new Document());

        final Rectangle rect = new Rectangle(10, 10);
        ObjectId id = new ObjectId();
        rect.setId(id);

        getAds().save(ns, rect);
        assertEquals(1, getAds().find(ns, Object.class).count());

        // when giving an ID that is not the entity ID.  Note that at the time of writing this will also log a validation warning
        getAds().deleteOne(ns, Rectangle.class, 1);

        // then
        assertEquals(1, getAds().find(ns, Object.class).count());
    }

    @Test
    public void testDeleteWillRemoveAnyDocumentWithAMatchingId() {
        // given
        final String ns = "someCollectionName";
        getDatabase().getCollection(ns).deleteMany(new Document());

        final Rectangle rect = new Rectangle(10, 10);
        ObjectId rectangleId = new ObjectId();
        rect.setId(rectangleId);
        getAds().save(ns, rect);

        final Circle circle = new Circle();
        circle.setId(new ObjectId());
        getAds().save(ns, circle);

        assertEquals(2, getAds().find(ns, Object.class).count());

        // when
        getAds().deleteOne(ns, Circle.class, rectangleId);

        // then
        assertEquals(1, getAds().find(ns, Object.class).count());
    }

    @Test
    public void testDeleteWithAnEntityTypeAndId() {
        // given
        final String ns = "someCollectionName";
        getDatabase().getCollection(ns).deleteMany(new Document());

        final Rectangle rect = new Rectangle(10, 10);
        ObjectId id = new ObjectId();
        rect.setId(id);

        getAds().save(ns, rect);
        assertEquals(1, getAds().find(ns, Object.class).count());

        // when
        getAds().deleteOne(ns, Rectangle.class, id);

        // then
        assertEquals(0, getAds().find(ns, Object.class).count());
    }

    @Test
    public void testFind() {
        final String ns = "hotels";
        Rectangle rect = new Rectangle(10, 10);
        ObjectId id = new ObjectId();
        rect.setId(id);

        getDatabase().getCollection(ns).deleteMany(new Document());

        getAds().save(ns, rect);
        assertEquals(1, getAds().find(ns, Object.class).count());
        Rectangle rectLoaded = getAds().find(ns, Rectangle.class).first();
        assertEquals(rect.getId(), rectLoaded.getId());
        assertEquals(rect.getArea(), rectLoaded.getArea(), 0);

        rect = new Rectangle(2, 1);
        getAds().save(rect);
        assertEquals(1, getDatastore().find(rect.getClass()).count());

        rect.setId(null);
        getAds().save(rect);
        assertEquals(2, getDatastore().find(rect.getClass()).count());

        rect = new Rectangle(4, 3);
        getAds().save(ns, rect);
        assertEquals(2, getAds().find(ns, Object.class).count());

        rectLoaded = getAds().find(ns, Rectangle.class).asList().get(1);
        assertEquals(rect.getId(), rectLoaded.getId());
        assertEquals(rect.getArea(), rectLoaded.getArea(), 0);

        getAds()
            .find(ns, Rectangle.class)
            .filter("_id !=", "-1").first(new FindOptions()
                     .skip(1)
                     .limit(1));
    }

    @Test
    public void testGet() {
        final String ns = "hotels";
        final Rectangle rect = new Rectangle(10, 10);

        getDatabase().getCollection(ns).deleteMany(new Document());

        getAds().save(ns, rect);
        assertEquals(1, getAds().find(ns, Object.class).count());
        final Rectangle rectLoaded = getAds().find(ns, Rectangle.class)
                                             .filter("_id", rect.getId()).first();
        assertEquals(rect.getId(), rectLoaded.getId());
        assertEquals(rect.getArea(), rectLoaded.getArea(), 0);
    }
}
