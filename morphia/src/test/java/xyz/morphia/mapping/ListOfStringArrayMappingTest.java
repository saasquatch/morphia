package xyz.morphia.mapping;


import org.bson.types.ObjectId;
import org.junit.Assert;
import org.junit.Test;
import xyz.morphia.Datastore;
import xyz.morphia.TestBase;
import xyz.morphia.annotations.Id;

import java.util.ArrayList;
import java.util.List;

public class ListOfStringArrayMappingTest extends TestBase {
    @Test
    public void testMapping() {
        getMapper().map(ContainsListStringArray.class);
        final ContainsListStringArray ent = new ContainsListStringArray();
        ent.listOfStrings.add(new String[]{"a", "b"});
        ent.arrayOfStrings = new String[]{"only", "the", "lonely"};
        ent.string = "raw string";

        getDatastore().save(ent);
        final Datastore datastore = getDatastore();
        final ContainsListStringArray loaded = datastore.find(ent.getClass())
                                                        .filter("_id", datastore.getMapper().getId(ent))
                                                        .first();
        Assert.assertNotNull(loaded.id);
        Assert.assertArrayEquals(ent.listOfStrings.get(0), loaded.listOfStrings.get(0));
        Assert.assertArrayEquals(ent.arrayOfStrings, loaded.arrayOfStrings);
        Assert.assertEquals(ent.string, loaded.string);
    }

    private static class ContainsListStringArray {
        private final List<String[]> listOfStrings = new ArrayList<>();
        @Id
        private ObjectId id;
        private String[] arrayOfStrings;
        private String string;
    }
}
