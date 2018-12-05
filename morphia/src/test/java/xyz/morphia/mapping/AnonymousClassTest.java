package xyz.morphia.mapping;


import org.bson.types.ObjectId;
import org.junit.Assert;
import org.junit.Test;
import xyz.morphia.Datastore;
import xyz.morphia.Key;
import xyz.morphia.TestBase;
import xyz.morphia.annotations.Embedded;
import xyz.morphia.annotations.Id;

public class AnonymousClassTest extends TestBase {

    @Test
    public void testDelete() {
        final E e = new E();
        e.id = new CId("test");

        final Key<E> key = getDatastore().save(e);
        getDatastore().deleteOne(E.class, e.id);
    }

    @Test
    public void testMapping() {
        E e = new E();
        e.id = new CId("test");

        getDatastore().save(e);
        final Datastore datastore = getDatastore();
        e = datastore.find(e.getClass()).filter("_id", datastore.getMapper().getId(e)).first();
        Assert.assertEquals("test", e.id.name);
        Assert.assertNotNull(e.id.id);
    }

    @Test
    public void testOtherDelete() {
        final E e = new E();
        e.id = new CId("test");

        getDatastore().save(e);
        getAds().deleteOne(getDatastore().getCollection(E.class).getNamespace().getCollectionName(), E.class, e.id);
    }

    @Embedded
    private static class CId {
        private final ObjectId id = new ObjectId();
        private String name;

        CId() {
        }

        CId(final String n) {
            name = n;
        }

        @Override
        public int hashCode() {
            int result = id.hashCode();
            result = 31 * result + name.hashCode();
            return result;
        }

        @Override
        public boolean equals(final Object obj) {
            if (!(obj instanceof CId)) {
                return false;
            }
            final CId other = ((CId) obj);
            return other.id.equals(id) && other.name.equals(name);
        }

    }

    private static class E {
        @Id
        private CId id;
        private String e;
    }

}
