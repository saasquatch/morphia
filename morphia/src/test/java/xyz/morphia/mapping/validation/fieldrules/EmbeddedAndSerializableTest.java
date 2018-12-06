package xyz.morphia.mapping.validation.fieldrules;


import org.bson.types.ObjectId;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import xyz.morphia.TestBase;
import xyz.morphia.annotations.Embedded;
import xyz.morphia.annotations.Entity;
import xyz.morphia.annotations.Id;
import xyz.morphia.annotations.Serialized;
import xyz.morphia.mapping.validation.ConstraintViolationException;
import xyz.morphia.testutil.TestEntity;

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

@Ignore("@Serialized might be removed altogether")
public class EmbeddedAndSerializableTest extends TestBase {
    @Test
    public void embedded() {
        getMapper().map(Project.class, Period.class);

        Project project = new Project();
        project.period = new Period();
        for (int x = 0; x < 100; x++) {
            project.periods.add(new Period());
        }
        getDatastore().save(project);

        Project project1 = getDatastore().find(Project.class).first();

        final List<Period> periods = project1.periods;
        for (int i = 0; i < periods.size(); i++) {
            compare(project.periods.get(i), periods.get(i));
        }

        compare(project.period, project1.period);
    }

    @Test(expected = ConstraintViolationException.class)
    public void testCheck() {
        getMapper().map(E.class);
    }

    private void compare(final Period original, final Period loaded) {
        Assert.assertEquals(original.from, loaded.from);
        Assert.assertEquals(original.until, loaded.until);
    }

    public static class E extends TestEntity {
        @Serialized
        private R r;
    }

    public static class R {
    }

    @Entity
    public static class Project {
        @Id
        private ObjectId id;
        private Period period;

        private List<Period> periods = new ArrayList<>();
    }

    @Embedded
    public static class Period implements Iterable<Date> {
        private Date from = new Date();
        private Date until = new Date();

        @Override
        public Iterator<Date> iterator() {
            return null;
        }
    }
}
