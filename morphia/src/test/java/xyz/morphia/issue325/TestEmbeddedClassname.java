package xyz.morphia.issue325;


import org.bson.Document;
import org.junit.Assert;
import org.junit.Test;
import xyz.morphia.Datastore;
import xyz.morphia.TestBase;
import xyz.morphia.annotations.Embedded;
import xyz.morphia.annotations.Entity;
import xyz.morphia.annotations.Id;
import xyz.morphia.annotations.PreLoad;
import xyz.morphia.annotations.Transient;
import xyz.morphia.mapping.Mapper;

import java.util.ArrayList;
import java.util.List;


public class TestEmbeddedClassname extends TestBase {

    @Test
    public final void testEmbeddedClassname() {
        getMapper().map(Root.class, A.class, B.class);
        Datastore ds = getDatastore();

        Root r = new Root();
        r.singleA = new A();
        ds.save(r);

        ds.updateOne(ds.find(Root.class), ds.createUpdateOperations(Root.class).addToSet("aList", new A()));
        r = ds.find(Root.class).filter("_id", "id").first();
        Document aRaw = r.singleA.raw;

        // Test that singleA does not contain the class name
        Assert.assertFalse(aRaw.containsKey(Mapper.CLASS_NAME_FIELDNAME));

        // Test that aList does not contain the class name
        aRaw = r.aList.get(0).raw;
        Assert.assertFalse(aRaw.containsKey(Mapper.CLASS_NAME_FIELDNAME));

        // Test that bList does not contain the class name of the subclass
        ds.updateOne(ds.find(Root.class), ds.createUpdateOperations(Root.class).addToSet("bList", new B()));
        r = ds.find(Root.class).filter("_id", "id").first();

        aRaw = r.aList.get(0).raw;
        Assert.assertFalse(aRaw.containsKey(Mapper.CLASS_NAME_FIELDNAME));

        Document bRaw = r.bList.get(0).getRaw();
        Assert.assertTrue(bRaw.containsKey(Mapper.CLASS_NAME_FIELDNAME));

        ds.delete(ds.find(Root.class));

        //test saving an B in aList, and it should have the classname.
        Root entity = new Root();
        entity.singleA = new B();
        ds.save(entity);
        ds.updateOne(ds.find(Root.class), ds.createUpdateOperations(Root.class).addToSet("aList", new B()));
        r = ds.find(Root.class).filter("_id", "id").first();

        // test that singleA.raw *does* contain the classname because we stored a subclass there
        aRaw = r.singleA.raw;
        Assert.assertTrue("singleA.raw should contain the classname because we stored a subclass",
            aRaw.containsKey(Mapper.CLASS_NAME_FIELDNAME));
        Document bRaw2 = r.aList.get(0).raw;
        Assert.assertTrue(bRaw2.containsKey(Mapper.CLASS_NAME_FIELDNAME));
    }

    @Entity(useDiscriminator = false)
    private static class Root {
        private final List<A> aList = new ArrayList<>();
        private final List<B> bList = new ArrayList<>();
        @Id
        private String id = "id";
        private A singleA;
    }

    @Embedded(useDiscriminator = false)
    private static class A {
        private String name = "some name";

        @Transient
        private Document raw;

        public String getName() {
            return name;
        }

        public void setName(final String name) {
            this.name = name;
        }

        public Document getRaw() {
            return raw;
        }

        public void setRaw(final Document raw) {
            this.raw = raw;
        }

        @PreLoad
        void preLoad(final Document document) {
            raw = document;
        }
    }

    @Embedded
    private static class B extends A {
        private String description = "<description here>";
    }

}
