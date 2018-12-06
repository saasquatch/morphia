package xyz.morphia.query;


import com.mongodb.MongoException;
import org.bson.types.ObjectId;
import org.junit.Assert;
import org.junit.Test;
import xyz.morphia.Key;
import xyz.morphia.TestBase;
import xyz.morphia.annotations.Entity;
import xyz.morphia.annotations.Id;
import xyz.morphia.annotations.Reference;
import xyz.morphia.testutil.TestEntity;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static java.util.Collections.singletonList;
import static org.slf4j.LoggerFactory.getLogger;

public class QueryInTest extends TestBase {
    private static final Logger LOG = getLogger(QueryInTest.class);

    @Test
    public void testAddEmpty() {
        Query<Data> query = getDatastore().find(Data.class);
        List<ObjectId> memberships = new ArrayList<>();

        query.or(
            query.criteria("id").hasAnyOf(memberships),
            query.criteria("otherIds").hasAnyOf(memberships)
        );

        List<Data> dataList = query.asList();

        Assert.assertEquals(0, dataList.size());
    }

    @Test
    public void testIdOnly() {
        ReferencedEntity b = new ReferencedEntity();
        b.setId(new ObjectId("111111111111111111111111"));
        getDatastore().save(b);

        HasIdOnly has = new HasIdOnly();
        has.list = new ArrayList<>();
        has.list.add(b);
        has.entity = b;
        getDatastore().save(has);

        Query<HasIdOnly> q = getDatastore().find(HasIdOnly.class);
        q.criteria("list").in(singletonList(b));
        Assert.assertEquals(1, q.asList().size());

        q = getDatastore().find(HasIdOnly.class);
        q.criteria("entity").equal(b.getId());
        Assert.assertEquals(1, q.asList().size());
    }

    @Test
    public void testInIdList() {
        final Doc doc = new Doc();
        doc.id = 1;
        getDatastore().save(doc);

        // this works
        getDatastore().find(Doc.class).field("_id").equal(1).asList();

        final List<Long> idList = new ArrayList<>();
        idList.add(1L);
        // this causes an NPE
        getDatastore().find(Doc.class).field("_id").in(idList).asList();

    }

    @Test
    public void testInQuery() {
        final HasRefs hr = new HasRefs();
        for (int x = 0; x < 10; x++) {
            final ReferencedEntity re = new ReferencedEntity("" + x);
            hr.refs.add(re);
        }
        getDatastore().save(hr.refs);
        getDatastore().save(hr);

        Query<HasRefs> query = getDatastore().find(HasRefs.class).field("refs").in(hr.refs.subList(1, 3));
        final List<HasRefs> res = query.asList();
        Assert.assertEquals(1, res.size());
    }

    @Test
    public void testInQueryByKey() {
        final HasRef hr = new HasRef();
        List<Key<ReferencedEntity>> refs = new ArrayList<>();
        for (int x = 0; x < 10; x++) {
            final ReferencedEntity re = new ReferencedEntity("" + x);
            getDatastore().save(re);
            refs.add(new Key<>(ReferencedEntity.class,
                getMapper().getCollectionName(ReferencedEntity.class),
                re.getId()));
        }
        hr.ref = refs.get(0);

        getDatastore().save(hr);

        Query<HasRef> query = getDatastore().find(HasRef.class).field("ref").in(refs);
        try {
            Assert.assertEquals(1, query.asList().size());
        } catch (MongoException e) {
            LOG.debug("query = " + query);
            throw e;
        }
    }

    @Test
    public void testMapping() {
        getMapper().map(HasRefs.class);
        getMapper().map(ReferencedEntity.class);
    }

    @Test
    public void testReferenceDoesNotExist() {
        final HasRefs hr = new HasRefs();
        getDatastore().save(hr);

        final Query<HasRefs> q = getDatastore().find(HasRefs.class);
        q.field("refs").doesNotExist();
        final List<HasRefs> found = q.asList();
        Assert.assertNotNull(found);
        Assert.assertEquals(1, found.size());
    }

    @Entity("data")
    private static final class Data {
        @Id
        private ObjectId id;
        private Set<ObjectId> otherIds;

        private Data() {
            otherIds = new HashSet<>();
        }
    }

    @Entity
    private static class HasRef {
        @Id
        private ObjectId id = new ObjectId();
        private Key<ReferencedEntity> ref;
    }

    @Entity
    private static class HasRefs {
        @Id
        private ObjectId id = new ObjectId();
        @Reference
        private List<ReferencedEntity> refs = new ArrayList<>();
    }

    @Entity
    private static class ReferencedEntity extends TestEntity {
        private String foo;

        ReferencedEntity() {
        }

        ReferencedEntity(final String s) {
            foo = s;
        }
    }

    @Entity(value = "as", useDiscriminator = false)
    private static class HasIdOnly {
        @Id
        private ObjectId id;
        private String name;
        @Reference(idOnly = true)
        private List<ReferencedEntity> list;
        @Reference(idOnly = true)
        private ReferencedEntity entity;
    }

    @Entity("docs")
    private static class Doc {
        @Id
        private long id = 4;

    }
}
