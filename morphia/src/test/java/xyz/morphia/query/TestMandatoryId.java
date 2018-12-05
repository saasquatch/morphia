package xyz.morphia.query;


import org.junit.Test;
import xyz.morphia.Key;
import xyz.morphia.TestBase;
import xyz.morphia.TestMapping.MissingId;
import xyz.morphia.mapping.validation.ConstraintViolationException;


public class TestMandatoryId extends TestBase {
    @Test(expected = ConstraintViolationException.class)
    public final void testMissingIdNoImplicitMapCall() {
        final Key<MissingId> save = getDatastore().save(new MissingId());

        getDatastore().find(MissingId.class).filter("_id", save.getId()).get();
    }
}
