package xyz.morphia.generics;

import org.junit.Assert;
import org.junit.Test;
import xyz.morphia.TestBase;
import xyz.morphia.generics.model.AnotherChildEmbedded;
import xyz.morphia.generics.model.ChildEmbedded;
import xyz.morphia.generics.model.ChildEntity;

import static java.util.Arrays.asList;

public class WildcardsTest extends TestBase {

    @Test
    public void example() {
        ChildEntity entity = new ChildEntity();
        entity.setEmbeddedList(asList(
            new ChildEmbedded("first"),
            new ChildEmbedded("second"),
            new AnotherChildEmbedded("third")));
        getDatastore().save(entity);

        ChildEntity childEntity = getDatastore().find(ChildEntity.class).first();

        Assert.assertEquals(entity, childEntity);
    }
}
