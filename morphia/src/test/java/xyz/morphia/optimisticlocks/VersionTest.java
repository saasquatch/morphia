package xyz.morphia.optimisticlocks;


import com.mongodb.client.result.UpdateResult;
import org.bson.types.ObjectId;
import org.junit.Assert;
import org.junit.Test;
import xyz.morphia.Datastore;
import xyz.morphia.TestBase;
import xyz.morphia.annotations.Entity;
import xyz.morphia.annotations.Id;
import xyz.morphia.annotations.Version;
import xyz.morphia.mapping.MappedField;
import xyz.morphia.mapping.validation.ConstraintViolationException;
import xyz.morphia.query.Query;
import xyz.morphia.query.UpdateOperations;
import xyz.morphia.testutil.TestEntity;

import java.util.ConcurrentModificationException;

public class VersionTest extends TestBase {


    @Test(expected = ConcurrentModificationException.class)
    public void testConcurrentModDetection() {
        getMapper().map(ALongPrimitive.class);

        final ALongPrimitive a = new ALongPrimitive();
        Assert.assertEquals(0, a.version);
        getDatastore().save(a);

        final Datastore datastore = getDatastore();
        final ALongPrimitive a2 = datastore.find(a.getClass()).filter("_id", datastore.getMapper().getId(a)).first();
        getDatastore().save(a2);

        getDatastore().save(a);
    }

    @Test(expected = ConcurrentModificationException.class)
    public void testConcurrentModDetectionLong() {
        final ALong a = new ALong();
        Assert.assertEquals(null, a.v);
        getDatastore().save(a);

        final Datastore datastore = getDatastore();
        getDatastore().save((ALong) datastore.find(a.getClass()).filter("_id", datastore.getMapper().getId(a)).first());

        getDatastore().save(a);
    }

    @Test(expected = ConcurrentModificationException.class)
    public void testConcurrentModDetectionLongWithMerge() {
        final ALong a = new ALong();
        Assert.assertEquals(null, a.v);
        getDatastore().save(a);

        a.text = " foosdfds ";
        final Datastore datastore = getDatastore();
        final ALong a2 = datastore.find(a.getClass()).filter("_id", datastore.getMapper().getId(a)).first();
        getDatastore().save(a2);

        getDatastore().merge(a);
    }

    @Test(expected = ConstraintViolationException.class)
    public void testInvalidVersionUse() {
        getMapper().map(InvalidVersionUse.class);
    }

    @Test
    public void testVersionFieldNameContribution() {
        final MappedField mappedFieldByJavaField = getMapper().getMappedClass(ALong.class).getMappedFieldByJavaField("v");
        Assert.assertEquals("versionNameContributedByAnnotation", mappedFieldByJavaField.getNameToStore());
    }

    @Test
    public void testVersionInHashcode() {
        getMapper().mapPackage("com.example");

        final VersionInHashcode model = new VersionInHashcode();
        model.data = "whatever";
        getDatastore().save(model);
        Assert.assertNotNull(model.version);
    }

    @Test
    public void testVersions() {
        final ALongPrimitive a = new ALongPrimitive();
        Assert.assertEquals(0, a.version);
        getDatastore().save(a);
        Assert.assertTrue(a.version > 0);
        final long version1 = a.version;

        getDatastore().save(a);
        Assert.assertTrue(a.version > 0);
        final long version2 = a.version;

        Assert.assertFalse(version1 == version2);
    }

    @Test
    public void testVersionsWithFindAndModify() {
        final ALongPrimitive initial = new ALongPrimitive();
        Datastore ds = getDatastore();
        ds.save(initial);

        Query<ALongPrimitive> query = ds.find(ALongPrimitive.class)
                                     .field("id").equal(initial.getId());
        UpdateOperations<ALongPrimitive> update = ds.createUpdateOperations(ALongPrimitive.class)
                                                    .set("text", "some new value");
        ALongPrimitive postUpdate = ds.findAndModify(query, update);

        Assert.assertEquals(initial.version + 1, postUpdate.version);
    }

    @Test
    public void testVersionsWithUpdate() {
        final ALongPrimitive initial = new ALongPrimitive();
        Datastore ds = getDatastore();
        ds.save(initial);

        Query<ALongPrimitive> query = ds.find(ALongPrimitive.class)
                                     .field("id").equal(initial.getId());
        UpdateOperations<ALongPrimitive> update = ds.createUpdateOperations(ALongPrimitive.class)
                                                    .set("text", "some new value");
        UpdateResult results = ds.updateOne(query, update);
        Assert.assertEquals(1, results.getModifiedCount());
        ALongPrimitive postUpdate = ds.find(ALongPrimitive.class).filter("_id", initial.getId()).get();

        Assert.assertEquals(initial.version + 1, postUpdate.version);
    }

    @Entity
    public static class VersionInHashcode {
        @Id
        private ObjectId id;
        @Version
        private Long version;

        private String data;

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            final VersionInHashcode that = (VersionInHashcode) o;

            if (id != null ? !id.equals(that.id) : that.id != null) {
                return false;
            }
            if (version != null ? !version.equals(that.version) : that.version != null) {
                return false;
            }
            return data != null ? data.equals(that.data) : that.data == null;
        }

        @Override
        public int hashCode() {
            final int dataHashCode = (data == null) ? 0 : data.hashCode();
            final int versionHashCode = (version == null) ? 0 : version.hashCode();
            return dataHashCode + versionHashCode;
        }
    }

    public static class ALongPrimitive extends TestEntity {

        @Version
        private long version;

        private String text;

        public long getVersion() {
            return version;
        }

        public void setVersion(final long version) {
            this.version = version;
        }

        public String getText() {
            return text;
        }

        public void setText(final String text) {
            this.text = text;
        }
    }

    public static class ALong extends TestEntity {
        @Version("versionNameContributedByAnnotation")
        private Long v;

        private String text;
    }

    @Entity
    static class InvalidVersionUse {
        @Id
        private String id;
        @Version
        private long version1;
        @Version
        private long version2;

    }

}
