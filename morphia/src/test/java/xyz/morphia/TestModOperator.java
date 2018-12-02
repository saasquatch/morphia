package xyz.morphia;


import org.bson.types.ObjectId;
import org.junit.Assert;
import org.junit.Test;
import xyz.morphia.annotations.Entity;
import xyz.morphia.annotations.Id;
import xyz.morphia.query.Sort;

import java.util.List;

import static xyz.morphia.query.Sort.ascending;


public class TestModOperator extends TestBase {
    @Test
    public void mod() {
        getMapper().map(Inventory.class);
        getDatastore().save(new Inventory("Flowers", 8));
        getDatastore().save(new Inventory("Candy", 2));
        getDatastore().save(new Inventory("Basketballs", 12));

        List<Inventory> list = getDatastore().find(Inventory.class)
                                             .filter("quantity mod", new Integer[]{4, 0})
                                             .order(ascending("name"))
                                             .asList();

        Assert.assertEquals(2, list.size());
        Assert.assertEquals("Basketballs", list.get(0).name);
        Assert.assertEquals("Flowers", list.get(1).name);

        list = getDatastore().find(Inventory.class)
                             .filter("quantity mod", new Integer[]{4, 2})
                             .order(ascending("name"))
                             .asList();

        Assert.assertEquals(1, list.size());
        Assert.assertEquals("Candy", list.get(0).name);

        list = getDatastore().find(Inventory.class)
                             .filter("quantity mod", new Integer[]{6, 0})
                             .order(ascending("name"))
                             .asList();

        Assert.assertEquals(1, list.size());
        Assert.assertEquals("Basketballs", list.get(0).name);

        list = getDatastore().find(Inventory.class)
                             .field("quantity").mod(4, 0)
                             .order(ascending("name"))
                             .asList();

        Assert.assertEquals(2, list.size());
        Assert.assertEquals("Basketballs", list.get(0).name);
        Assert.assertEquals("Flowers", list.get(1).name);

        list = getDatastore().find(Inventory.class)
                             .field("quantity").mod(4, 2)
                             .order(ascending("name"))
                             .asList();

        Assert.assertEquals(1, list.size());
        Assert.assertEquals("Candy", list.get(0).name);

        list = getDatastore().find(Inventory.class)
                             .field("quantity").mod(6, 0)
                             .order(ascending("name"))
                             .asList();

        Assert.assertEquals(1, list.size());
        Assert.assertEquals("Basketballs", list.get(0).name);
    }

    @Entity
    private static final class Inventory {
        @Id
        private ObjectId id;
        private Integer quantity;
        private String name;

        private Inventory() {
        }

        private Inventory(final String name, final Integer quantity) {
            this.name = name;
            this.quantity = quantity;
        }

        @Override
        public String toString() {
            return String.format("Inventory{quantity=%d, name='%s'}", quantity, name);
        }
    }
}
