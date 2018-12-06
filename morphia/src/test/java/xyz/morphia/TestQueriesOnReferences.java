package xyz.morphia;


import org.junit.Assert;
import org.junit.Test;
import xyz.morphia.TestDatastore.FacebookUser;
import xyz.morphia.mapping.MappingException;
import xyz.morphia.query.Query;
import xyz.morphia.query.TestQuery.ContainsPic;
import xyz.morphia.query.TestQuery.Pic;

import java.util.ArrayList;
import java.util.List;

public class TestQueriesOnReferences extends TestBase {
    @Test
    public void testKeyExists() {
        final ContainsPic cpk = new ContainsPic();
        final Pic p = new Pic();
        cpk.setPic(p);
        getDatastore().save(p);
        getDatastore().save(cpk);

        Assert.assertNotNull(getDatastore().find(ContainsPic.class)
                                           .field("pic").exists()
                                           .project("pic", true).first());
        Assert.assertNull(getDatastore().find(ContainsPic.class)
                                        .field("pic").doesNotExist()
                                        .project("pic", true).first());
    }

    @Test(expected = MappingException.class)
    public void testMissingSingleReference() {
        final ContainsPic cpk = new ContainsPic();
        final Pic p = new Pic();
        cpk.setPic(p);
        getDatastore().save(p);
        getDatastore().save(cpk);

        getDatastore().deleteOne(p);

        getDatastore().find(ContainsPic.class).asList();
    }

    @Test(expected = MappingException.class)
    public void testMissingReferencesInList() {
        final FacebookUser user = new FacebookUser(100, "Big Guy");
        List<FacebookUser> friends = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            final FacebookUser friend = new FacebookUser(i, "Friend " + i);
            friends.add(friend);
            if (i % 2 == 0) {
                getDatastore().save(friend);
            }
        }
        user.getFriends().addAll(friends);
        getDatastore().save(user);

        getDatastore().find(FacebookUser.class).asList();
    }

    @Test
    public void testDuplicateReferences() {
        final FacebookUser user = new FacebookUser(100, "Big Guy");
        List<FacebookUser> friends = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            final FacebookUser friend = new FacebookUser(i, "Friend " + i);
            friends.add(friend);
            friends.add(friend);
            getDatastore().save(friend);
        }
        user.getFriends().addAll(friends);
        getDatastore().save(user);

        final FacebookUser facebookUser = getDatastore().find(FacebookUser.class)
                                                        .filter("_id", 100).first();
        Assert.assertEquals(10, facebookUser.getFriends().size());
        for (int i = 0; i < 5; i++) {
            Assert.assertSame(facebookUser.getFriends().get(2 * i), facebookUser.getFriends().get(2 * i + 1));
        }
    }

    @Test
    public void testQueryOverReference() {

        final ContainsPic cpk = new ContainsPic();
        final Pic p = new Pic();
        getDatastore().save(p);
        cpk.setPic(p);
        getDatastore().save(cpk);

        final Query<ContainsPic> query = getDatastore().find(ContainsPic.class);
        final ContainsPic object = query.field("pic")
                                        .equal(p).first();
        Assert.assertNotNull(object);

    }

    @Test
    public void testWithKeyQuery() {
        final ContainsPic cpk = new ContainsPic();
        final Pic p = new Pic();
        cpk.setPic(p);
        getDatastore().save(p);
        getDatastore().save(cpk);

        ContainsPic containsPic = getDatastore().find(ContainsPic.class)
                                                .field("pic").equal(new Key<>(Pic.class, "Pic", p.getId())).first();
        Assert.assertEquals(cpk.getId(), containsPic.getId());

        containsPic = getDatastore().find(ContainsPic.class).field("pic").equal(new Key<>(Pic.class, "Pic", p.getId())).first();
        Assert.assertEquals(cpk.getId(), containsPic.getId());
    }
}

