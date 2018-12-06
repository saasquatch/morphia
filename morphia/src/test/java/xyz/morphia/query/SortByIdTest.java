package xyz.morphia.query;


import org.bson.types.ObjectId;
import org.junit.Assert;
import org.junit.Test;
import xyz.morphia.TestBase;
import xyz.morphia.annotations.Entity;
import xyz.morphia.annotations.Id;

import static xyz.morphia.query.Sort.descending;


public class SortByIdTest extends TestBase {

    @Test
    public void getLastByIdTest() {
        final A a1 = new A("a1");
        final A a2 = new A("a2");
        final A a3 = new A("a3");

        getDatastore().save(a1);
        getDatastore().save(a2);
        getDatastore().save(a3);

        Assert.assertEquals("last id", a3.id, getDatastore().find(A.class).order(descending("id")).first().id);
        Assert.assertEquals("last id", a3.id, getDatastore().find(A.class).disableValidation().order(descending("_id")).first().id);
    }

    @Entity("A")
    static class A {
        @Id
        private ObjectId id;
        private String name;

        A(final String name) {
            this.name = name;
        }

        A() {
        }
    }

}
