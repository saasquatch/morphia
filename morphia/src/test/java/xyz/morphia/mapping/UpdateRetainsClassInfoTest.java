package xyz.morphia.mapping;


import org.bson.types.ObjectId;
import org.junit.Test;
import xyz.morphia.TestBase;
import xyz.morphia.annotations.Id;
import xyz.morphia.query.Query;
import xyz.morphia.query.UpdateOperations;

import java.util.HashMap;
import java.util.Map;


public class UpdateRetainsClassInfoTest extends TestBase {
    @Test
    public void retainsClassName() {
        final X x = new X();

        final E1 e1 = new E1();
        e1.foo = "narf";
        x.map.put("k1", e1);

        final E2 e2 = new E2();
        e2.bar = "narf";
        x.map.put("k2", e2);

        getDatastore().save(x);

        final Query<X> query = getDatastore().find(X.class);
        final UpdateOperations<X> update = getDatastore().createUpdateOperations(X.class);
        update.set("map.k2", e2);

        getDatastore().updateMany(query, update);

        // fails due to type now missing
        getDatastore().find(X.class).first();
    }

    public abstract static class E {
        @Id
        private ObjectId id = new ObjectId();
    }

    public static class E1 extends E {
        private String foo;
    }

    public static class E2 extends E {
        private String bar;
    }

    public static class X {
        private final Map<String, E> map = new HashMap<>();
        @Id
        private ObjectId id;

    }
}
