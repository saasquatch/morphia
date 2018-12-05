package xyz.morphia.mapping;


import org.bson.types.ObjectId;
import org.junit.Assert;
import org.junit.Test;
import xyz.morphia.Datastore;
import xyz.morphia.TestBase;
import xyz.morphia.annotations.Id;
import xyz.morphia.annotations.Property;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;

public class ClassMappingTest extends TestBase {

    @Test
    public void testClassQueries() {
        E e = new E();

        e.testClass2 = LinkedList.class;
        getDatastore().save(e);

        Assert.assertNull(getDatastore().find(E.class).field("testClass2").equal(ArrayList.class).get());
    }

    @Test
    public void testMapping() {
        E e = new E();

        e.testClass = LinkedList.class;
        getDatastore().save(e);

        final Datastore datastore = getDatastore();
        e = datastore.find(e.getClass()).filter("_id", datastore.getMapper().getId(e)).first();
        Assert.assertEquals(LinkedList.class, e.testClass);
    }

    @Test
    public void testMappingWithoutAnnotation() {
        E e = new E();

        e.testClass2 = LinkedList.class;
        getDatastore().save(e);

        final Datastore datastore = getDatastore();
        e = datastore.find(e.getClass()).filter("_id", datastore.getMapper().getId(e)).first();
        Assert.assertEquals(LinkedList.class, e.testClass2);
    }

    public static class E {
        @Id
        private ObjectId id;

        @Property
        private Class<? extends Collection> testClass;
        private Class<? extends Collection> testClass2;
    }
}
