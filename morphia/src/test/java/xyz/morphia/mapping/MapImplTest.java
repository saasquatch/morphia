package xyz.morphia.mapping;


import com.mongodb.client.MongoCollection;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.Assert;
import org.junit.Test;
import xyz.morphia.Datastore;
import xyz.morphia.TestBase;
import xyz.morphia.annotations.Embedded;
import xyz.morphia.annotations.Id;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertFalse;

public class MapImplTest extends TestBase {

    @Test
    public void testEmbeddedMap() {
        getMapper().map(ContainsMapOfEmbeddedGoos.class, ContainsMapOfEmbeddedInterfaces.class);
        final Goo g1 = new Goo("Scott");
        final ContainsMapOfEmbeddedGoos cmoeg = new ContainsMapOfEmbeddedGoos();
        cmoeg.values.put("first", g1);
        getDatastore().save(cmoeg);
        //check className in the map values.

        final MongoCollection<ContainsMapOfEmbeddedGoos> collection = getDatastore().getCollection(
            ContainsMapOfEmbeddedGoos.class);
        final MongoCollection<Document> docCollection = getDatabase().getCollection(collection.getNamespace().getCollectionName());

        final Document next = docCollection
                                  .find()
                                  .limit(1)
                                  .iterator().next();
        final Document values = (Document) next
                                               .get("values");
        final Document goo = (Document) values
                                            .get("first");
        assertFalse(goo.containsKey(Mapper.CLASS_NAME_FIELDNAME));
    }

    @Test //@Ignore("waiting on issue 184")
    public void testEmbeddedMapUpdateOperations() {
        getMapper().map(ContainsMapOfEmbeddedGoos.class, ContainsMapOfEmbeddedInterfaces.class);
        final Goo g1 = new Goo("Scott");
        final Goo g2 = new Goo("Ralph");

        final ContainsMapOfEmbeddedGoos cmoeg = new ContainsMapOfEmbeddedGoos();
        cmoeg.values.put("first", g1);
        getDatastore().save(cmoeg);
        getDatastore().update(cmoeg, getDatastore().createUpdateOperations(ContainsMapOfEmbeddedGoos.class).set("values.second", g2));
        //check className in the map values.

        final MongoCollection<ContainsMapOfEmbeddedGoos> collection = getDatastore().getCollection(
            ContainsMapOfEmbeddedGoos.class);
        final MongoCollection<Document> docCollection = getDatabase().getCollection(collection.getNamespace().getCollectionName());

        final Document goo = (Document) ((Document) docCollection
                                                        .find()
                                                        .limit(1)
                                                        .iterator().next()
                                                        .get("values"))
                                            .get("second");
        assertFalse("className should not be here.", goo.containsKey(Mapper.CLASS_NAME_FIELDNAME));
    }

    @Test
    public void testEmbeddedMapUpdateOperationsOnInterfaceValue() {
        getMapper().map(ContainsMapOfEmbeddedGoos.class, ContainsMapOfEmbeddedInterfaces.class);
        final Goo g1 = new Goo("Scott");
        final Goo g2 = new Goo("Ralph");

        final ContainsMapOfEmbeddedInterfaces entity = new ContainsMapOfEmbeddedInterfaces();
        entity.values.put("first", g1);
        getDatastore().save(entity);
        getDatastore().update(entity, getDatastore().createUpdateOperations(ContainsMapOfEmbeddedInterfaces.class)
                                                    .set("values.second", g2));
        //check className in the map values.
        final MongoCollection<ContainsMapOfEmbeddedInterfaces> collection = getDatastore().getCollection(
            ContainsMapOfEmbeddedInterfaces.class);
        final MongoCollection<Document> docCollection = getDatabase().getCollection(collection.getNamespace().getCollectionName());

        final Document goo = (Document) ((Document) docCollection
                                                        .find()
                                                        .limit(1)
                                                        .iterator().next()
                                                        .get("values"))
                                            .get("second");
        assertFalse("className should not be here.", goo.containsKey(Mapper.CLASS_NAME_FIELDNAME));
    }

    @Test
    public void testEmbeddedMapWithValueInterface() {
        getMapper().map(ContainsMapOfEmbeddedGoos.class, ContainsMapOfEmbeddedInterfaces.class);
        final Goo g1 = new Goo("Scott");

        final ContainsMapOfEmbeddedInterfaces cmoei = new ContainsMapOfEmbeddedInterfaces();
        cmoei.values.put("first", g1);
        getDatastore().save(cmoei);
        //check className in the map values.
        final MongoCollection<ContainsMapOfEmbeddedInterfaces> collection = getDatastore().getCollection(
            ContainsMapOfEmbeddedInterfaces.class);
        final MongoCollection<Document> docCollection = getDatabase().getCollection(collection.getNamespace().getCollectionName());

        final Document goo = (Document) ((Document) docCollection
                                                        .find()
                                                        .limit(1)
                                                        .iterator().next()
                                                        .get("values"))
                                            .get("first");
        assertFalse(goo.containsKey(Mapper.CLASS_NAME_FIELDNAME));
    }

    @Test
    public void testMapping() {
        E e = new E();
        e.mymap.put("1", "a");
        e.mymap.put("2", "b");

        getDatastore().save(e);

        final Datastore datastore = getDatastore();
        e = datastore.find(e.getClass()).filter("_id", datastore.getMapper().getId(e)).first();
        Assert.assertEquals("a", e.mymap.get("1"));
        Assert.assertEquals("b", e.mymap.get("2"));
    }

    private static class ContainsMapOfEmbeddedInterfaces {
        @Id
        private ObjectId id;
        private final Map<String, Object> values = new HashMap<>();
    }

    private static class ContainsMapOfEmbeddedGoos {
        @Id
        private ObjectId id;
        private final Map<String, Goo> values = new HashMap<>();
    }

    @Embedded(useDiscriminator = false)
    private static class Goo {
        private String name;

        Goo() {
        }

        Goo(final String n) {
            name = n;
        }
    }

    private static class E {
        @Id
        private ObjectId id;
        private final MyMap mymap = new MyMap();
    }

    @Embedded
    private static class MyMap extends HashMap<String, String> {
    }
}
