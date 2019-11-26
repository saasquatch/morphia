package dev.morphia.ext.guice;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import dev.morphia.annotations.Embedded;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import dev.morphia.annotations.Transient;
import org.bson.types.ObjectId;
import org.junit.Assert;
import org.junit.Test;

public class TestEmbedded extends TestBase {

    @Override
    public void setUp() {

        super.setUp();
        new GuiceExtension(getMorphia(), Guice.createInjector(new AbstractModule() {}));
        getMorphia().map(Profile.class, ProfileCache.class);
    }

    @Test
    public void embedded() {
        Profile profile = new Profile(new ProfileCache<Profile>(), new Core());

        getDs().save(profile);

        Profile first = getDs().find(Profile.class).filter("_id", profile.id).first();
        Assert.assertNotNull(first.);
    }

    @Entity
    static class Profile {
        @Id
        private ObjectId id;
        @Transient
        private final ProfileCache<Profile> cache;
        @Transient
        private final Core core;
        @Embedded("profileGroups") // Groups for this player
        private ProfileGroupCache groups;

        @Inject
        public Profile(ProfileCache<Profile> cache, Core core) {
            this.cache = cache;
            this.core = core;
            this.groups = new ProfileGroupCache(core.getGroupManager());
        }
    }

    @Embedded
    static class ProfileCache<T> {
    }

    @Embedded
    static class ProfileGroupCache<T> {

        private GroupManager groupManager;

        @Inject
        public ProfileGroupCache(final GroupManager groupManager) {
            this.groupManager = groupManager;
        }
    }

    static class Core {
        public GroupManager getGroupManager() {
            return new GroupManager();
        }
    }

    static class GroupManager {}
}
